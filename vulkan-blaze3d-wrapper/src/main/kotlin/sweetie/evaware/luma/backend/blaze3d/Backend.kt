package sweetie.evaware.luma.backend.blaze3d

import sweetie.evaware.luma.LumaNames

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.platform.CompareOp
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
import sweetie.evaware.luma.api.RenderTargetFilter
import sweetie.evaware.luma.uniform.*
import sweetie.evaware.luma.vertex.VertexLayout
import java.awt.image.BufferedImage
import java.nio.FloatBuffer

class Backend(
    private val config: VulkanBackendConfig = VulkanBackendConfig()
) : RenderBackend {
    private var nextProgramId = 1

    private var frameId = 0L

    private val vertexBuffer = VulkanBuffer(
        { LumaNames.VERTEX_BUFFER },
        GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST,
        config.initialVertexBufferBytes
    )
    private val uboBuffer = VulkanBuffer(
        { LumaNames.UBO_BUFFER },
        GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST,
        config.initialUniformBufferBytes
    )

    private val boundTextures = arrayOfNulls<TextureHandle>(DrawCall.TEXTURE_UNITS)
    private val textureSnapshots = ArrayList<Array<TextureHandle?>>()
    private var textureSnapshotCount = 0
    private var textureSnapshot: Array<TextureHandle?> = DrawCall.NO_TEXTURES
    private var texturesDirty = false

    private val vertexStaging = StagingBuffer(config.initialVertexStagingBytes)
    private val uboStaging = StagingBuffer(config.initialUniformStagingBytes)

    private val recorder = DrawCallRecorder()
    private val merger = DrawCallMerger()

    private class TargetFrame(
        val target: VulkanRenderTarget?,
        var clearColor: FloatArray?
    )
    private val targetStack = ArrayList<TargetFrame>()

    override fun beginFrame() {
        VulkanResourceRetirement.collectCompleted()
        RenderStateTracker.beginFrame()
        frameId++
        recorder.reset()
        resetTextureSnapshots()
        vertexStaging.reset()
        uboStaging.reset()

        targetStack.clear()
        targetStack.add(TargetFrame(null, null))
    }

    override fun endFrame() {
        if (
            recorder.size == 0 &&
            !TextureUploadQueue.hasPending() &&
            !VulkanResourceRetirement.hasPending()
        ) return

        val device = RenderSystem.getDevice()
        val encoder = device.createCommandEncoder()

        TextureUploadQueue.record(encoder)

        if (recorder.size > 0) {
            vertexStaging.flip()
            vertexBuffer.write(encoder, vertexStaging.buffer)

            uboStaging.flip()
            uboBuffer.write(encoder, uboStaging.buffer)

            val pass = RenderPassEncoder(device, encoder, vertexBuffer, uboBuffer)
            merger.run(recorder, pass)
            pass.finish()
        }

        val retirement = VulkanResourceRetirement.attachTo(encoder)
        encoder.submit()
        VulkanResourceRetirement.submitted(retirement)
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
        if (config.precompileDefaultPipeline) prog.precompileDefaults(RenderSystem.getDevice())
        return prog
    }

    override fun bindProgram(program: ProgramHandle) {}

    override fun createTexture(image: BufferedImage, mipmap: Boolean): TextureHandle {
        require(!mipmap) { "Automatic mipmap generation is not supported by the Vulkan backend" }
        return VulkanTexture.create(image)
    }

    override fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage) {
        (texture as VulkanTexture).update(x, y, image)
    }

    override fun bindTexture(texture: TextureHandle, unit: Int) {
        require(unit in boundTextures.indices) {
            "Texture unit $unit is outside 0..${boundTextures.lastIndex}"
        }
        if (boundTextures[unit] === texture) return
        boundTextures[unit] = texture
        texturesDirty = true
    }

    private fun currentTextures(): Array<TextureHandle?> {
        if (texturesDirty) {
            val snapshot = if (textureSnapshotCount < textureSnapshots.size) {
                textureSnapshots[textureSnapshotCount]
            } else {
                arrayOfNulls<TextureHandle>(DrawCall.TEXTURE_UNITS).also(textureSnapshots::add)
            }
            textureSnapshotCount++
            boundTextures.copyInto(snapshot)
            textureSnapshot = snapshot
            texturesDirty = false
        }
        return textureSnapshot
    }

    private fun resetTextureSnapshots() {
        for (index in 0 until textureSnapshotCount) {
            textureSnapshots[index].fill(null)
        }
        textureSnapshotCount = 0
        textureSnapshot = DrawCall.NO_TEXTURES
        texturesDirty = true
    }

    override fun createRenderTarget(
        width: Int,
        height: Int,
        useDepth: Boolean,
        format: RenderTargetFormat
    ): RenderTargetHandle = createRenderTarget(width, height, useDepth, format, RenderTargetFilter.LINEAR)

    override fun createRenderTarget(
        width: Int,
        height: Int,
        useDepth: Boolean,
        format: RenderTargetFormat,
        filter: RenderTargetFilter
    ): RenderTargetHandle {
        return VulkanRenderTarget.create(width, height, useDepth, format, filter)
    }

    override fun beginRenderTarget(target: RenderTargetHandle, clearColor: FloatArray?) {
        targetStack.add(TargetFrame(target as VulkanRenderTarget, clearColor))
    }

    override fun endRenderTarget() {
        check(targetStack.size > 1) { "No render target to end" }
        targetStack.removeAt(targetStack.size - 1)
    }

    override fun depthTest(enabled: Boolean) = RenderStateTracker.depthTest(enabled)

    override fun cull(enabled: Boolean) = RenderStateTracker.cull(enabled)

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
                    val builder = Std140Builder.onStack(stack, config.uniformScratchBytes)
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
        draw.depthEnabled = RenderStateTracker.depthEnabled
        draw.depthWrite = draw.depthEnabled && RenderStateTracker.depthWrite
        draw.depthFunc = if (draw.depthEnabled) RenderStateTracker.depthFunc else CompareOp.ALWAYS_PASS
        draw.cullEnabled = RenderStateTracker.cullEnabled
        draw.target = activeTarget.target
        draw.clearColor = activeTarget.clearColor

        activeTarget.clearColor = null
    }

    override fun close() {
        TextureUploadQueue.close()
        VulkanResourceRetirement.closeAll(RenderSystem.getDevice()::createCommandEncoder)
        vertexBuffer.close()
        uboBuffer.close()
        VulkanResourceRetirement.closeAll(RenderSystem.getDevice()::createCommandEncoder)
    }
}
