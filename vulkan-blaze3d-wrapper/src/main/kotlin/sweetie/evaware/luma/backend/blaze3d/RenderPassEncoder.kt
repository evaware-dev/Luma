package sweetie.evaware.luma.backend.blaze3d

import org.joml.Vector4f
import sweetie.evaware.luma.LumaNames
import sweetie.evaware.luma.api.BlendFunction
import sweetie.evaware.luma.api.DepthCompare
import sweetie.evaware.luma.api.IndexType
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.backend.blaze3d.preset.*

internal class RenderPassEncoder(
    private val vertexBuffer: VulkanBuffer,
    private val uboBuffer: VulkanBuffer
) : GroupConsumer {

    private var preset: VulkanPreset = VulkanPresets.active
    private var encoder: PresetCommandEncoder? = null
    private var currentPass: PresetRenderPass? = null
    private var currentProgram: Program? = null
    private var currentTargetHasDepth = false
    private val directVertexBuffers = arrayOfNulls<VulkanVertexBuffer>(16)
    private val directVertexOffsets = LongArray(16)
    private var directIndexBuffer: VulkanIndexBuffer? = null
    private var pipelineGeneration = 0L
    private val clearColor = Vector4f()
    private val renderPassLabel = { LumaNames.RENDER_PASS }
    private var currentPipeline: PresetPipeline? = null
    private var currentUboOffset = -1L
    private var currentUboSize = -1L
    private val boundTextureViews = arrayOfNulls<PresetGpuTextureView>(16)
    private val boundTextureSamplers = arrayOfNulls<PresetGpuSampler>(16)

    fun begin(preset: VulkanPreset, encoder: PresetCommandEncoder, pipelineGeneration: Long): RenderPassEncoder {
        check(this.encoder == null) { "Render pass encoder is already active" }
        this.preset = preset
        this.encoder = encoder
        this.pipelineGeneration = pipelineGeneration
        return this
    }

    override fun onTargetChanged(target: VulkanRenderTarget?) {
        currentPass?.close()
        currentPass = null
        currentProgram = null
        currentPipeline = null
        currentUboOffset = -1L
        currentUboSize = -1L
        boundTextureViews.fill(null)
        boundTextureSamplers.fill(null)
        resetDirectBindings()

        checkNotNull(target) {
            "No Blaze3D render target is active; call beginRenderTarget before drawing"
        }
        val colorView = target.colorView
        val depthView = target.depthView

        val pass = checkNotNull(encoder).createRenderPass(
            renderPassLabel,
            colorView,
            depthView
        )

        currentPass = pass
        currentTargetHasDepth = depthView != null
        preset.bindDefaultUniforms(pass)
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
        currentPipeline = null
        currentUboOffset = -1L
        currentUboSize = -1L
        boundTextureViews.fill(null)
        boundTextureSamplers.fill(null)
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
                commandEncoder.clearColorAndDepthTextures(colorTexture, clearColor.x, clearColor.y, clearColor.z, clearColor.w, depthTexture, depthValue)
            color -> commandEncoder.clearColorTexture(colorTexture, clearColor.x, clearColor.y, clearColor.z, clearColor.w)
            depth && depthTexture != null -> commandEncoder.clearDepthTexture(depthTexture, depthValue)
        }
    }

    override fun onPipelineChanged(
        program: Program,
        topology: PrimitiveType,
        blendEnabled: Boolean,
        blendFunction: BlendFunction,
        depthEnabled: Boolean,
        depthWrite: Boolean,
        depthFunc: DepthCompare,
        cullEnabled: Boolean
    ) {
        val pass = currentPass ?: return
        val pipeline = program.getOrCreatePipeline(
            preset,
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
        if (currentPipeline !== pipeline) {
            pass.setPipeline(pipeline)
            currentPipeline = pipeline
        }
        currentProgram = program
    }

    override fun onUboChanged(offset: Long, size: Long) {
        val pass = currentPass ?: return
        if (currentUboOffset == offset && currentUboSize == size) return
        pass.setUniform(LumaNames.UNIFORMS_BLOCK, uboBuffer.slice(offset, size))
        currentUboOffset = offset
        currentUboSize = size
    }

    override fun onTextureChanged(program: Program, textures: Array<TextureHandle?>) {
        val pass = currentPass ?: return
        val bindings = program.samplerBindings
        for (index in bindings.indices) {
            val binding = bindings[index]
            val texture = textures.getOrNull(binding.unit) as? VulkanTexture ?: continue
            val view = texture.view
            val sampler = texture.sampler
            val unit = binding.unit
            if (unit in boundTextureViews.indices) {
                if (boundTextureViews[unit] === view && boundTextureSamplers[unit] === sampler) continue
                boundTextureViews[unit] = view
                boundTextureSamplers[unit] = sampler
            }
            pass.bindTexture(binding.name, view, sampler)
        }
    }

    override fun onScissorChanged(enabled: Boolean, x: Int, y: Int, width: Int, height: Int) {
        val pass = currentPass ?: return
        if (enabled) pass.enableScissor(x, y, width, height) else pass.disableScissor()
    }

    override fun onDraw(topology: PrimitiveType, vertexStart: Long, vertexBytes: Long, vertexCount: Int) {
        val pass = currentPass ?: return
        val program = currentProgram ?: return
        pass.setVertexBuffer(0, vertexBuffer.slice(vertexStart, vertexBytes))
        resetDirectBindings()

        if (program.layout.instanced) {
            pass.draw(program.layout.baseVertexCount, vertexCount, 0, 0)
            return
        }

        if (topology == PrimitiveType.QUADS) {
            val indexCount = (vertexCount / 4) * 6
            val sequential = preset.getSequentialQuadBuffer(indexCount)
            pass.setSequentialIndexBuffer(sequential)
            directIndexBuffer = null
            pass.drawIndexed(indexCount, 1, 0, 0, 0)
        } else {
            pass.draw(vertexCount, 1, 0, 0)
        }
    }

    override fun onDirectDraw(draw: DrawCall, topology: PrimitiveType) {
        val pass = currentPass ?: return
        val program = requireNotNull(draw.program)
        for (binding in 0 until program.layouts.size()) {
            val buffer = requireNotNull(draw.vertexBuffers[binding]) { "Vertex binding $binding is not set" }
            buffer.requireOpen()
            val offset = draw.vertexOffsets[binding]
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
                    indices.indexType
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
        currentPipeline = null
        currentUboOffset = -1L
        currentUboSize = -1L
        boundTextureViews.fill(null)
        boundTextureSamplers.fill(null)
        resetDirectBindings()
        encoder = null
    }

    private fun resetDirectBindings() {
        directVertexBuffers.fill(null)
        directVertexOffsets.fill(0L)
        directIndexBuffer = null
    }
}
