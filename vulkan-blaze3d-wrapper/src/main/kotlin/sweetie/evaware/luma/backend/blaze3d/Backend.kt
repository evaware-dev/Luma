package sweetie.evaware.luma.backend.blaze3d

import sweetie.evaware.luma.LumaNames

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.joml.Vector4f
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.api.RenderBackend
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.uniform.*
import sweetie.evaware.luma.vertex.VertexLayout
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.util.Optional

interface GroupConsumer {
    fun onTargetChanged(target: VulkanRenderTarget?, clearColor: FloatArray?)
    fun onPipelineChanged(
        program: Program,
        topology: PrimitiveTopology,
        depthEnabled: Boolean,
        depthWrite: Boolean,
        depthFunc: CompareOp,
        cullEnabled: Boolean
    )
    fun onUboChanged(offset: Long, size: Long)
    fun onTextureChanged(program: Program, textures: Array<TextureHandle?>)
    fun onDraw(topology: PrimitiveTopology, vertexStart: Long, vertexBytes: Long, vertexCount: Int)
}

class Backend : RenderBackend {
    private var nextProgramId = 1
    
    private val vertexBuffer = VulkanBuffer({ LumaNames.VERTEX_BUFFER }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, 1024)
    private val uboBuffer = VulkanBuffer({ LumaNames.UBO_BUFFER }, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, 256)
    private val boundTextures = arrayOfNulls<TextureHandle>(16)

    private object MAIN_MARKER

    private class DrawCall {
        var program: Program? = null
        var vertexOffset: Long = 0
        var vertexBytes: Long = 0
        var vertexCount: Int = 0
        var uboOffset: Long = 0
        var uboBytes: Long = 0
        val textures = arrayOfNulls<TextureHandle>(16)
        var primitiveType: Int = 0
        var depthEnabled: Boolean = false
        var depthWrite: Boolean = false
        var depthFunc: CompareOp = CompareOp.ALWAYS_PASS
        var cullEnabled: Boolean = false
        var target: VulkanRenderTarget? = null
        var clearColor: FloatArray? = null
    }

    private val recordedDraws = ArrayList<DrawCall>()
    private val drawCallPool = ArrayList<DrawCall>()
    private var drawCallPoolIndex = 0

    private class TargetFrame(
        val target: VulkanRenderTarget?,
        var clearColor: FloatArray?
    )
    private val targetStack = ArrayList<TargetFrame>()

    private fun obtainDrawCall(): DrawCall {
        if (drawCallPoolIndex >= drawCallPool.size) {
            drawCallPool.add(DrawCall())
        }
        return drawCallPool[drawCallPoolIndex++]
    }

    private var verticesStaging = ByteBuffer.allocateDirect(1024 * 1024).order(java.nio.ByteOrder.nativeOrder())
    private var uboStaging = ByteBuffer.allocateDirect(64 * 1024).order(java.nio.ByteOrder.nativeOrder())

    private val lastUboSlice = java.util.IdentityHashMap<Program, Long>()

    private fun ensureStagingCapacity(buffer: ByteBuffer, required: Int): ByteBuffer {
        if (required <= buffer.capacity()) return buffer
        var newCapacity = buffer.capacity() * 2
        while (newCapacity < required) newCapacity *= 2
        val newBuffer = ByteBuffer.allocateDirect(newCapacity).order(java.nio.ByteOrder.nativeOrder())
        val oldPos = buffer.position()
        buffer.flip()
        newBuffer.put(buffer)
        newBuffer.position(oldPos)
        return newBuffer
    }

    override fun beginFrame() {
        recordedDraws.clear()
        for (i in 0 until drawCallPool.size) {
            val draw = drawCallPool[i]
            draw.program = null
            for (j in draw.textures.indices) {
                draw.textures[j] = null
            }
            draw.target = null
            draw.clearColor = null
        }
        drawCallPoolIndex = 0
        verticesStaging.clear()
        uboStaging.clear()
        lastUboSlice.clear()

        targetStack.clear()
        targetStack.add(TargetFrame(null, null))
    }

    override fun endFrame() {
        if (drawCallPoolIndex == 0) return

        val device = RenderSystem.getDevice()
        val encoder = device.createCommandEncoder()

        verticesStaging.flip()
        vertexBuffer.write(encoder, verticesStaging)

        uboStaging.flip()
        uboBuffer.write(encoder, uboStaging)

        var currentPass: com.mojang.blaze3d.systems.RenderPass? = null

        runMergeLoop(object : GroupConsumer {
            override fun onTargetChanged(target: VulkanRenderTarget?, clearColor: FloatArray?) {
                currentPass?.close()
                currentPass = null

                val colorView: com.mojang.blaze3d.textures.GpuTextureView
                val depthView: com.mojang.blaze3d.textures.GpuTextureView?

                if (target != null) {
                    colorView = target.colorView
                    depthView = null
                } else {
                    val mainTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget()
                    colorView = mainTarget.colorTextureView ?: return
                    depthView = mainTarget.depthTextureView
                }

                val passOpt = if (clearColor != null && clearColor.size >= 4) {
                    encoder.createRenderPass(
                        { LumaNames.RENDER_PASS },
                        colorView,
                        Optional.of(Vector4f(clearColor[0], clearColor[1], clearColor[2], clearColor[3])),
                        depthView,
                        java.util.OptionalDouble.empty()
                    )
                } else {
                    encoder.createRenderPass(
                        { LumaNames.RENDER_PASS },
                        colorView,
                        Optional.empty(),
                        depthView,
                        java.util.OptionalDouble.empty()
                    )
                }

                currentPass = passOpt
                RenderSystem.bindDefaultUniforms(passOpt)
            }

            override fun onPipelineChanged(
                program: Program,
                topology: PrimitiveTopology,
                depthEnabled: Boolean,
                depthWrite: Boolean,
                depthFunc: CompareOp,
                cullEnabled: Boolean
            ) {
                val pass = currentPass ?: return
                val key = PipelineKey(
                    topology = topology,
                    depthEnabled = depthEnabled,
                    depthWrite = depthWrite,
                    depthFunc = depthFunc,
                    cullEnabled = cullEnabled
                )
                val pipeline = program.getOrCreatePipeline(device, key)
                pass.setPipeline(pipeline)
            }

            override fun onUboChanged(offset: Long, size: Long) {
                val pass = currentPass ?: return
                pass.setUniform(LumaNames.UNIFORMS_BLOCK, uboBuffer.slice(offset, size))
            }

            override fun onTextureChanged(program: Program, textures: Array<TextureHandle?>) {
                val pass = currentPass ?: return
                for (sampler in program.samplers) {
                    val unit = sampler.removePrefix("Sampler").toIntOrNull() ?: 0
                    val texture = if (unit in textures.indices) textures[unit] else null
                    if (texture != null) {
                        val tex = texture as VulkanTexture
                        pass.bindTexture(sampler, tex.view, tex.sampler)
                    }
                }
            }

            override fun onDraw(topology: PrimitiveTopology, vertexStart: Long, vertexBytes: Long, vertexCount: Int) {
                val pass = currentPass ?: return
                pass.setVertexBuffer(0, vertexBuffer.slice(vertexStart, vertexBytes))
                if (topology == PrimitiveTopology.QUADS) {
                    val indexCount = (vertexCount / 4) * 6
                    val sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)
                    val indexBuffer = sequential.getBuffer(indexCount)
                    pass.setIndexBuffer(indexBuffer, sequential.type())
                    pass.drawIndexed(indexCount, 1, 0, 0, 0)
                } else {
                    pass.draw(vertexCount, 1, 0, 0)
                }
            }
        })

        currentPass?.close()
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
        if (unit in boundTextures.indices) {
            boundTextures[unit] = texture
        }
    }

    override fun createRenderTarget(
        width: Int,
        height: Int,
        useDepth: Boolean,
        format: RenderTargetFormat
    ): RenderTargetHandle {
        return VulkanRenderTarget.create(width, height, format)
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
        texture: TextureHandle?,
        primitiveType: Int
    ) {
        val prog = program as Program
        val vertexBytes = vertexCount * prog.layout.strideFloats * Float.SIZE_BYTES

        verticesStaging = ensureStagingCapacity(verticesStaging, verticesStaging.position() + vertexBytes)
        val vertexOffset = verticesStaging.position().toLong()
        MemoryUtil.memCopy(
            MemoryUtil.memAddress(vertices),
            MemoryUtil.memAddress(verticesStaging) + verticesStaging.position(),
            vertexBytes.toLong()
        )
        verticesStaging.position(verticesStaging.position() + vertexBytes)

        val uboBytes: Int
        val uboOffset: Long
        val vulkanUniforms = prog.getVulkanUniforms(uniforms)
        if (vulkanUniforms.isEmpty()) {
            uboBytes = 0
            uboOffset = 0L
        } else {
            var anyDirty = false
            for (i in vulkanUniforms.indices) {
                if (vulkanUniforms[i].isHandleDirty(uniforms)) {
                    anyDirty = true
                    break
                }
            }

            val previous = if (anyDirty) 0L else lastUboSlice[prog] ?: 0L
            if (previous != 0L) {
                uboOffset = previous ushr 32
                uboBytes = (previous and 0xFFFFFFFFL).toInt()
            } else {
                val stack = MemoryStack.stackPush()
                try {
                    val builder = Std140Builder.onStack(stack, 256)
                    for (i in vulkanUniforms.indices) {
                        vulkanUniforms[i].write(builder, uniforms)
                    }
                    val uboData = builder.get()
                    uboBytes = uboData.remaining()
                    uboStaging = ensureStagingCapacity(uboStaging, uboStaging.position() + uboBytes)
                    uboOffset = uboStaging.position().toLong()
                    uboStaging.put(uboData)
                } finally {
                    stack.pop()
                }
                for (i in vulkanUniforms.indices) {
                    vulkanUniforms[i].clearDirty(uniforms)
                }
                lastUboSlice[prog] = (uboOffset shl 32) or (uboBytes.toLong() and 0xFFFFFFFFL)
            }
        }

        val activeTarget = targetStack.last()
        val draw = obtainDrawCall()
        draw.program = prog
        draw.vertexOffset = vertexOffset
        draw.vertexBytes = vertexBytes.toLong()
        draw.vertexCount = vertexCount
        draw.uboOffset = uboOffset
        draw.uboBytes = uboBytes.toLong()
        System.arraycopy(boundTextures, 0, draw.textures, 0, boundTextures.size)
        draw.primitiveType = primitiveType
        draw.depthEnabled = GlStateQuery.depthEnabled
        draw.depthWrite = GlStateQuery.depthWrite
        draw.depthFunc = GlStateQuery.depthFunc
        draw.cullEnabled = GlStateQuery.cullEnabled
        draw.target = activeTarget.target
        draw.clearColor = activeTarget.clearColor

        activeTarget.clearColor = null

        recordedDraws.add(draw)
    }

    internal fun addDrawCallForTest(
        program: Program,
        vertexOffset: Long,
        vertexBytes: Long,
        vertexCount: Int,
        uboOffset: Long,
        uboBytes: Long,
        texture: TextureHandle?,
        primitiveType: Int,
        depthEnabled: Boolean = false,
        depthWrite: Boolean = false,
        depthFunc: CompareOp = CompareOp.ALWAYS_PASS,
        cullEnabled: Boolean = false,
        target: VulkanRenderTarget? = null,
        clearColor: FloatArray? = null
    ) {
        val draw = obtainDrawCall()
        draw.program = program
        draw.vertexOffset = vertexOffset
        draw.vertexBytes = vertexBytes
        draw.vertexCount = vertexCount
        draw.uboOffset = uboOffset
        draw.uboBytes = uboBytes
        draw.textures[0] = texture
        draw.primitiveType = primitiveType
        draw.depthEnabled = depthEnabled
        draw.depthWrite = depthWrite
        draw.depthFunc = depthFunc
        draw.cullEnabled = cullEnabled
        draw.target = target
        draw.clearColor = clearColor
    }

    internal fun runMergeLoop(consumer: GroupConsumer) {
        var currentTarget: Any? = Any()
        var currentClearColor: FloatArray? = null
        var currentProgram: Program? = null
        var currentTopology: PrimitiveTopology? = null
        var currentDepthEnabled = false
        var currentDepthWrite = false
        var currentDepthFunc = CompareOp.ALWAYS_PASS
        var currentCullEnabled = false
        val currentTextures = arrayOfNulls<TextureHandle>(16)
        var currentUboOffset = -1L
        var currentUboBytes = -1L
        var currentVertexOffset = -1L
        var currentVertexBytes = 0L
        var currentVertexCount = 0

        for (i in 0 until drawCallPoolIndex) {
            val draw = drawCallPool[i]
            val program = draw.program ?: continue
            val topology = when (draw.primitiveType) {
                1 -> PrimitiveTopology.LINES
                2 -> PrimitiveTopology.QUADS
                else -> PrimitiveTopology.TRIANGLES
            }

            val targetKey: Any = draw.target ?: MAIN_MARKER

            if (targetKey != currentTarget || !draw.clearColor.contentEquals(currentClearColor)) {
                if (currentVertexCount > 0 && currentTopology != null) {
                    consumer.onDraw(currentTopology, currentVertexOffset, currentVertexBytes, currentVertexCount)
                }
                currentVertexCount = 0

                consumer.onTargetChanged(draw.target, draw.clearColor)
                currentTarget = targetKey
                currentClearColor = draw.clearColor
                currentProgram = null
                currentTopology = null
                for (j in currentTextures.indices) {
                    currentTextures[j] = null
                }
                currentUboOffset = -1L
                currentUboBytes = -1L
            }

            var texturesEqual = true
            for (j in draw.textures.indices) {
                if (draw.textures[j] != currentTextures[j]) {
                    texturesEqual = false
                    break
                }
            }

            val canMerge = currentProgram == program &&
                currentTopology == topology &&
                currentDepthEnabled == draw.depthEnabled &&
                currentDepthWrite == draw.depthWrite &&
                currentDepthFunc == draw.depthFunc &&
                currentCullEnabled == draw.cullEnabled &&
                texturesEqual &&
                currentUboOffset == draw.uboOffset &&
                currentUboBytes == draw.uboBytes &&
                draw.vertexOffset == currentVertexOffset + currentVertexBytes

            if (canMerge) {
                currentVertexBytes += draw.vertexBytes
                currentVertexCount += draw.vertexCount
            } else {
                if (currentVertexCount > 0 && currentTopology != null) {
                    consumer.onDraw(currentTopology, currentVertexOffset, currentVertexBytes, currentVertexCount)
                }

                val pipelineChanged = currentProgram != program ||
                    currentTopology != topology ||
                    currentDepthEnabled != draw.depthEnabled ||
                    currentDepthWrite != draw.depthWrite ||
                    currentDepthFunc != draw.depthFunc ||
                    currentCullEnabled != draw.cullEnabled

                if (pipelineChanged) {
                    consumer.onPipelineChanged(
                        program,
                        topology,
                        draw.depthEnabled,
                        draw.depthWrite,
                        draw.depthFunc,
                        draw.cullEnabled
                    )
                }

                if (program.uniformInfos.isNotEmpty()) {
                    if (draw.uboOffset != currentUboOffset || draw.uboBytes != currentUboBytes) {
                        consumer.onUboChanged(draw.uboOffset, draw.uboBytes)
                    }
                }

                var texturesChanged = false
                for (j in draw.textures.indices) {
                    if (draw.textures[j] != currentTextures[j]) {
                        texturesChanged = true
                        break
                    }
                }
                if (texturesChanged) {
                    consumer.onTextureChanged(program, draw.textures)
                }

                currentProgram = program
                currentTopology = topology
                currentDepthEnabled = draw.depthEnabled
                currentDepthWrite = draw.depthWrite
                currentDepthFunc = draw.depthFunc
                currentCullEnabled = draw.cullEnabled
                System.arraycopy(draw.textures, 0, currentTextures, 0, draw.textures.size)
                currentUboOffset = draw.uboOffset
                currentUboBytes = draw.uboBytes
                currentVertexOffset = draw.vertexOffset
                currentVertexBytes = draw.vertexBytes
                currentVertexCount = draw.vertexCount
            }
        }

        if (currentVertexCount > 0 && currentTopology != null) {
            consumer.onDraw(currentTopology, currentVertexOffset, currentVertexBytes, currentVertexCount)
        }
    }

    override fun close() {
        vertexBuffer.close()
        uboBuffer.close()
    }
}
