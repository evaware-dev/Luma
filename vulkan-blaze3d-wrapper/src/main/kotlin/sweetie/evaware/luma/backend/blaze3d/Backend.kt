package sweetie.evaware.luma.backend.blaze3d

import sweetie.evaware.luma.LumaNames

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.resources.Identifier
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.api.RenderBackend
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.uniform.*
import sweetie.evaware.luma.vertex.VertexLayout
import java.awt.image.BufferedImage
import java.nio.FloatBuffer

class Backend : RenderBackend {
    private var nextProgramId = 1

    private var frameId = 0L

    private val vertexBuffer = VulkanBuffer({ LumaNames.VERTEX_BUFFER }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, 1024)
    private val uboBuffer = VulkanBuffer({ LumaNames.UBO_BUFFER }, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, 256)

    private val boundTextures = arrayOfNulls<TextureHandle>(DrawCall.TEXTURE_UNITS)
    private var textureSnapshot: Array<TextureHandle?> = DrawCall.NO_TEXTURES
    private var texturesDirty = false

    private val vertexStaging = StagingBuffer(1024 * 1024)
    private val uboStaging = StagingBuffer(64 * 1024)

    private val recorder = DrawCallRecorder()
    private val merger = DrawCallMerger()

    private class TargetFrame(
        val target: VulkanRenderTarget?,
        var clearColor: FloatArray?
    )
    private val targetStack = ArrayList<TargetFrame>()

    override fun beginFrame() {
        frameId++
        recorder.reset()
        vertexStaging.reset()
        uboStaging.reset()

        targetStack.clear()
        targetStack.add(TargetFrame(null, null))
    }

    override fun endFrame() {
        if (recorder.size == 0) return

        val device = RenderSystem.getDevice()
        val encoder = device.createCommandEncoder()

        vertexStaging.flip()
        vertexBuffer.write(encoder, vertexStaging.buffer)

        uboStaging.flip()
        uboBuffer.write(encoder, uboStaging.buffer)

        val pass = RenderPassEncoder(device, encoder, vertexBuffer, uboBuffer)
        merger.run(recorder, pass)
        pass.finish()

        encoder.submit()
    }

    override fun hasContext(): Boolean = true

    override fun createProgram(
        vertexSource: String,
        fragmentSource: String,
        layout: VertexLayout
    ): ProgramHandle {
        val prog = Program(
            Identifier.fromNamespaceAndPath(LumaNames.SHADER_NAMESPACE, "${LumaNames.PROGRAM_PREFIX}${nextProgramId++}"),
            vertexSource,
            fragmentSource,
            layout
        )
        prog.precompile(RenderSystem.getDevice())
        return prog
    }

    override fun bindProgram(program: ProgramHandle) {}

    override fun createTexture(image: BufferedImage, mipmap: Boolean): TextureHandle {
        return VulkanTexture.create(image, mipmap)
    }

    override fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage) {
        (texture as VulkanTexture).update(x, y, image)
    }

    override fun bindTexture(texture: TextureHandle, unit: Int) {
        if (unit in boundTextures.indices && boundTextures[unit] !== texture) {
            boundTextures[unit] = texture
            texturesDirty = true
        }
    }

    private fun currentTextures(): Array<TextureHandle?> {
        if (texturesDirty) {
            textureSnapshot = boundTextures.copyOf()
            texturesDirty = false
        }
        return textureSnapshot
    }

    override fun createRenderTarget(
        width: Int,
        height: Int,
        useDepth: Boolean,
        format: RenderTargetFormat
    ): RenderTargetHandle {
        return VulkanRenderTarget.create(width, height, useDepth, format)
    }

    override fun beginRenderTarget(target: RenderTargetHandle, clearColor: FloatArray?) {
        targetStack.add(TargetFrame(target as VulkanRenderTarget, clearColor))
    }

    override fun endRenderTarget() {
        if (targetStack.size > 1) {
            targetStack.removeAt(targetStack.size - 1)
        }
    }

    override fun draw(
        program: ProgramHandle,
        vertices: FloatBuffer,
        vertexCount: Int,
        uniforms: ShaderUniforms,
        primitiveType: PrimitiveType
    ) {
        val prog = program as Program
        val vertexBytes = vertexCount * prog.layout.strideFloats * Float.SIZE_BYTES
        val vertexOffset = vertexStaging.appendFromAddress(MemoryUtil.memAddress(vertices), vertexBytes)

        val uboOffset: Long
        val uboBytes: Int
        val vulkanUniforms = prog.getVulkanUniforms(uniforms)
        if (vulkanUniforms.isEmpty()) {
            uboOffset = 0L
            uboBytes = 0
        } else {
            var anyDirty = false
            for (i in vulkanUniforms.indices) {
                if (vulkanUniforms[i].isHandleDirty(uniforms)) {
                    anyDirty = true
                    break
                }
            }

            if (!anyDirty && prog.uboCacheFrameId == frameId) {
                uboOffset = prog.uboCacheOffset
                uboBytes = prog.uboCacheBytes
            } else {
                val stack = MemoryStack.stackPush()
                try {
                    val builder = Std140Builder.onStack(stack, 256)
                    for (i in vulkanUniforms.indices) {
                        vulkanUniforms[i].write(builder, uniforms)
                    }
                    val uboData = builder.get()
                    uboBytes = uboData.remaining()
                    uboOffset = uboStaging.append(uboData)
                } finally {
                    stack.pop()
                }
                for (i in vulkanUniforms.indices) {
                    vulkanUniforms[i].clearDirty(uniforms)
                }
                prog.uboCacheOffset = uboOffset
                prog.uboCacheBytes = uboBytes
                prog.uboCacheFrameId = frameId
            }
        }

        val activeTarget = targetStack.last()
        val draw = recorder.obtain()
        draw.program = prog
        draw.vertexOffset = vertexOffset
        draw.vertexBytes = vertexBytes.toLong()
        draw.vertexCount = vertexCount
        draw.uboOffset = uboOffset
        draw.uboBytes = uboBytes.toLong()
        draw.textures = currentTextures()
        draw.primitiveType = primitiveType
        draw.depthEnabled = GlStateQuery.depthEnabled
        draw.depthWrite = GlStateQuery.depthWrite
        draw.depthFunc = GlStateQuery.depthFunc
        draw.cullEnabled = GlStateQuery.cullEnabled
        draw.target = activeTarget.target
        draw.clearColor = activeTarget.clearColor

        activeTarget.clearColor = null
    }

    override fun close() {
        vertexBuffer.close()
        uboBuffer.close()
    }
}
