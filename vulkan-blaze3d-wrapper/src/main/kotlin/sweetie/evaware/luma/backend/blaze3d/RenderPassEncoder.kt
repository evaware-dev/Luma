package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.IndexType as MinecraftIndexType
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.systems.CommandEncoder
import com.mojang.blaze3d.systems.GpuDevice
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTextureView
import org.joml.Vector4f
import sweetie.evaware.luma.LumaNames
import sweetie.evaware.luma.api.BlendFunction
import sweetie.evaware.luma.api.IndexType
import sweetie.evaware.luma.api.TextureHandle
import java.util.Optional
import java.util.OptionalDouble

internal class RenderPassEncoder(
    private val vertexBuffer: VulkanBuffer,
    private val uboBuffer: VulkanBuffer
) : GroupConsumer {

    private var device: GpuDevice? = null
    private var encoder: CommandEncoder? = null
    private var currentPass: RenderPass? = null
    private var currentProgram: Program? = null
    private var currentTargetHasDepth = false
    private val directVertexBuffers = arrayOfNulls<VulkanVertexBuffer>(RenderPass.MAX_VERTEX_BUFFERS)
    private val directVertexOffsets = LongArray(RenderPass.MAX_VERTEX_BUFFERS)
    private var directIndexBuffer: VulkanIndexBuffer? = null
    private var pipelineGeneration = 0L
    private val clearColor = Vector4f()

    fun begin(device: GpuDevice, encoder: CommandEncoder, pipelineGeneration: Long): RenderPassEncoder {
        check(this.encoder == null) { "Render pass encoder is already active" }
        this.device = device
        this.encoder = encoder
        this.pipelineGeneration = pipelineGeneration
        return this
    }

    override fun onTargetChanged(target: VulkanRenderTarget?) {
        currentPass?.close()
        currentPass = null
        currentProgram = null
        resetDirectBindings()

        val colorView: GpuTextureView
        val depthView: GpuTextureView?

        checkNotNull(target) {
            "No Blaze3D render target is active; call beginRenderTarget before drawing"
        }
        colorView = target.colorView
        depthView = target.depthView

        val pass = checkNotNull(encoder).createRenderPass(
            { LumaNames.RENDER_PASS },
            colorView,
            Optional.empty(),
            depthView,
            OptionalDouble.empty()
        )

        currentPass = pass
        currentTargetHasDepth = depthView != null
        RenderSystem.bindDefaultUniforms(pass)
    }

    override fun onClear(
        target: VulkanRenderTarget?,
        color: Boolean,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        depth: Boolean,
        depthValue: Double
    ) {
        currentPass?.close()
        currentPass = null
        currentProgram = null
        resetDirectBindings()
        val commandEncoder = checkNotNull(encoder)
        val activeTarget = checkNotNull(target) {
            "No Blaze3D render target is active; call beginRenderTarget before clearing"
        }
        val colorTexture = activeTarget.gpuTexture
        val depthTexture = activeTarget.gpuDepthTexture
        if (color) clearColor.set(red, green, blue, alpha)
        when {
            color && depth && depthTexture != null ->
                commandEncoder.clearColorAndDepthTextures(requireNotNull(colorTexture), clearColor, depthTexture, depthValue)
            color -> commandEncoder.clearColorTexture(requireNotNull(colorTexture), clearColor)
            depth && depthTexture != null -> commandEncoder.clearDepthTexture(depthTexture, depthValue)
        }
    }

    override fun onPipelineChanged(
        program: Program,
        topology: PrimitiveTopology,
        blendEnabled: Boolean,
        blendFunction: BlendFunction,
        depthEnabled: Boolean,
        depthWrite: Boolean,
        depthFunc: CompareOp,
        cullEnabled: Boolean
    ) {
        val pass = currentPass ?: return
        val pipeline = program.getOrCreatePipeline(
            checkNotNull(device),
            topology,
            blendEnabled,
            blendFunction,
            depthEnabled,
            depthWrite,
            depthFunc,
            cullEnabled,
            currentTargetHasDepth,
            pipelineGeneration
        )
        pass.setPipeline(pipeline)
        currentProgram = program
    }

    override fun onUboChanged(offset: Long, size: Long) {
        val pass = currentPass ?: return
        pass.setUniform(LumaNames.UNIFORMS_BLOCK, uboBuffer.slice(offset, size))
    }

    override fun onTextureChanged(program: Program, textures: Array<TextureHandle?>) {
        val pass = currentPass ?: return
        val bindings = program.samplerBindings
        for (index in bindings.indices) {
            val binding = bindings[index]
            val texture = textures.getOrNull(binding.unit) as? VulkanTexture ?: continue
            pass.bindTexture(binding.name, texture.view, texture.sampler)
        }
    }

    override fun onScissorChanged(enabled: Boolean, x: Int, y: Int, width: Int, height: Int) {
        val pass = currentPass ?: return
        if (enabled) pass.enableScissor(x, y, width, height) else pass.disableScissor()
    }

    override fun onDraw(topology: PrimitiveTopology, vertexStart: Long, vertexBytes: Long, vertexCount: Int) {
        val pass = currentPass ?: return
        val program = currentProgram ?: return
        pass.setVertexBuffer(0, vertexBuffer.slice(vertexStart, vertexBytes))
        resetDirectBindings()

        if (program.layout.instanced) {
            pass.draw(program.layout.baseVertexCount, vertexCount, 0, 0)
            return
        }

        if (topology == PrimitiveTopology.QUADS) {
            val indexCount = (vertexCount / 4) * 6
            val sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)
            val indexBuffer = sequential.getBuffer(indexCount)
            pass.setIndexBuffer(indexBuffer, sequential.type())
            directIndexBuffer = null
            pass.drawIndexed(indexCount, 1, 0, 0, 0)
        } else {
            pass.draw(vertexCount, 1, 0, 0)
        }
    }

    override fun onDirectDraw(draw: DrawCall, topology: PrimitiveTopology) {
        val pass = currentPass ?: return
        val bindings = requireNotNull(draw.vertexBindings)
        val program = requireNotNull(draw.program)
        for (binding in 0 until program.layouts.size()) {
            val buffer = requireNotNull(bindings.buffers[binding]) { "Vertex binding $binding is not set" }
            buffer.requireOpen()
            val offset = bindings.offsets[binding]
            if (directVertexBuffers[binding] !== buffer || directVertexOffsets[binding] != offset) {
                pass.setVertexBuffer(binding, buffer.slice(offset, buffer.sizeBytes - offset))
                directVertexBuffers[binding] = buffer
                directVertexOffsets[binding] = offset
            }
        }
        if (draw.indexed) {
            val indices = requireNotNull(draw.indexBuffer) { "Index buffer is not bound" }
            indices.requireOpen()
            if (directIndexBuffer !== indices) {
                pass.setIndexBuffer(
                    indices.gpuBuffer,
                    if (indices.indexType == IndexType.UINT16) MinecraftIndexType.SHORT else MinecraftIndexType.INT
                )
                directIndexBuffer = indices
            }
            pass.drawIndexed(draw.vertexCount, draw.instanceCount, draw.firstIndex, draw.baseVertex, draw.firstInstance)
        } else {
            pass.draw(draw.vertexCount, draw.instanceCount, draw.firstVertex, draw.firstInstance)
        }
    }

    fun finish() {
        currentPass?.close()
        currentPass = null
        currentProgram = null
        resetDirectBindings()
        device = null
        encoder = null
    }

    private fun resetDirectBindings() {
        directVertexBuffers.fill(null)
        directVertexOffsets.fill(0L)
        directIndexBuffer = null
    }
}
