package sweetie.evaware.luma.backend.blaze3d

import org.joml.Vector4f
import sweetie.evaware.luma.LumaNames
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.backend.blaze3d.preset.*

internal class RenderPassEncoder(
    private val vertexBuffer: VulkanBuffer,
    private val uboBuffer: VulkanBuffer
) : GroupConsumer {

    private var preset: VulkanPreset? = null
    private var encoder: PresetCommandEncoder? = null
    private var currentPass: PresetRenderPass? = null
    private var currentProgram: Program? = null
    private var currentTargetHasDepth = false
    private val directVertexBuffers = arrayOfNulls<VulkanVertexBuffer>(16)
    private val directVertexOffsets = LongArray(16)
    private var directVertexBindingsMask = 0
    private var directIndexBuffer: VulkanIndexBuffer? = null
    private var boundSequentialBuffer: PresetGpuBuffer? = null
    private var boundStagingVertexBuffer = false
    private var pipelineGeneration = 0L
    private val clearColor = Vector4f()
    private val renderPassLabel = { LumaNames.RENDER_PASS }
    private var currentPipeline: PresetPipeline? = null
    private var currentUboOffset = -1L
    private var currentUboSize = -1L
    private val boundTextureViews = arrayOfNulls<PresetGpuTextureView>(16)
    private val boundTextureSamplers = arrayOfNulls<PresetGpuSampler>(16)
    private var boundTextureMask = 0
    private val activeDebugGroups = ArrayList<String>()
    private var passDebugGroupDepth = 0

    fun begin(preset: VulkanPreset, encoder: PresetCommandEncoder, pipelineGeneration: Long): RenderPassEncoder {
        check(this.encoder == null) { "Render pass encoder is already active" }
        this.preset = preset
        this.encoder = encoder
        this.pipelineGeneration = pipelineGeneration
        activeDebugGroups.clear()
        passDebugGroupDepth = 0
        return this
    }

    override fun onTargetChanged(target: VulkanRenderTarget?) {
        closeCurrentPass()
        resetPassState()

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
        for (index in activeDebugGroups.indices) {
            pass.pushDebugGroup(activeDebugGroups[index])
            passDebugGroupDepth++
        }
        checkNotNull(preset).bindDefaultUniforms(pass)
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
        closeCurrentPass()
        resetPassState()
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

    override fun onPassBoundary() {
        closeCurrentPass()
        resetPassState()
    }

    override fun onDebugGroupPush(label: String) {
        activeDebugGroups.add(label)
        val pass = currentPass ?: return
        pass.pushDebugGroup(label)
        passDebugGroupDepth++
    }

    override fun onDebugGroupPop() {
        if (activeDebugGroups.isEmpty()) return
        activeDebugGroups.removeAt(activeDebugGroups.lastIndex)
        val pass = currentPass ?: return
        if (passDebugGroupDepth > 0) {
            pass.popDebugGroup()
            passDebugGroupDepth--
        }
    }

    override fun onPipelineChanged(program: Program, pipelineKey: Int) {
        val pass = currentPass ?: return
        val pipeline = program.getOrCreatePipeline(
            checkNotNull(preset),
            pipelineKey,
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

    override fun onTextureChanged(
        program: Program,
        textureMask: Int,
        texture0: TextureHandle?,
        overflowTextures: Array<TextureHandle?>?
    ) {
        val pass = currentPass ?: return
        val bindings = program.samplerBindingArray
        for (index in bindings.indices) {
            val binding = bindings[index]
            val unit = binding.unit
            if ((textureMask and (1 shl unit)) == 0) continue
            val raw = if (unit == 0) texture0 else overflowTextures?.getOrNull(unit)
            val texture = raw as? VulkanTexture ?: continue
            val view = texture.view
            val sampler = texture.sampler
            if (unit in boundTextureViews.indices) {
                if (boundTextureViews[unit] === view && boundTextureSamplers[unit] === sampler) continue
                boundTextureViews[unit] = view
                boundTextureSamplers[unit] = sampler
                boundTextureMask = boundTextureMask or (1 shl unit)
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
        if (directVertexBindingsMask != 0 || directIndexBuffer != null) resetDirectBindings()

        if (program.layout.instanced) {
            pass.setVertexBuffer(0, vertexBuffer.slice(vertexStart, vertexBytes))
            boundStagingVertexBuffer = false
            pass.draw(program.layout.baseVertexCount, vertexCount, 0, 0)
            return
        }

        val strideBytes = program.strideBytes
        val firstVertex: Int
        if (strideBytes > 0 && vertexStart % strideBytes == 0L) {
            if (!boundStagingVertexBuffer) {
                pass.setVertexBuffer(0, vertexBuffer.fullSlice())
                boundStagingVertexBuffer = true
            }
            firstVertex = (vertexStart / strideBytes).toInt()
        } else {
            pass.setVertexBuffer(0, vertexBuffer.slice(vertexStart, vertexBytes))
            boundStagingVertexBuffer = false
            firstVertex = 0
        }

        if (topology == PrimitiveType.QUADS) {
            val indexCount = (vertexCount / 4) * 6
            val sequential = checkNotNull(preset).getSequentialQuadBuffer(indexCount)
            if (boundSequentialBuffer !== sequential.buffer) {
                pass.setSequentialIndexBuffer(sequential)
                boundSequentialBuffer = sequential.buffer
                directIndexBuffer = null
            }
            pass.drawIndexed(indexCount, 1, 0, firstVertex, 0)
        } else {
            pass.draw(vertexCount, 1, firstVertex, 0)
        }
    }

    override fun onDirectDraw(recorder: DrawCallRecorder, index: Int, topology: PrimitiveType) {
        val pass = currentPass ?: return
        val program = requireNotNull(recorder.programs[index])
        val layoutCount = program.layouts.size()
        boundStagingVertexBuffer = false
        if (layoutCount == 1) {
            val buffer = requireNotNull(recorder.directVertexBuffer0?.get(index)) { "Vertex binding 0 is not set" }
            buffer.requireOpen()
            val offset = recorder.directVertexOffset0!![index]
            if (directVertexBuffers[0] !== buffer || directVertexOffsets[0] != offset) {
                pass.setVertexBuffer(0, buffer.slice(offset, buffer.sizeBytes - offset))
                directVertexBuffers[0] = buffer
                directVertexOffsets[0] = offset
                directVertexBindingsMask = directVertexBindingsMask or 1
            }
        } else {
            val buffer0 = requireNotNull(recorder.directVertexBuffer0?.get(index)) { "Vertex binding 0 is not set" }
            buffer0.requireOpen()
            val offset0 = recorder.directVertexOffset0!![index]
            if (directVertexBuffers[0] !== buffer0 || directVertexOffsets[0] != offset0) {
                pass.setVertexBuffer(0, buffer0.slice(offset0, buffer0.sizeBytes - offset0))
                directVertexBuffers[0] = buffer0
                directVertexOffsets[0] = offset0
                directVertexBindingsMask = directVertexBindingsMask or 1
            }
            val buffers = recorder.overflowDirectVertexBuffers?.get(index)
            val offsets = recorder.overflowDirectVertexOffsets?.get(index)
            for (binding in 1 until layoutCount) {
                val buffer = requireNotNull(buffers?.get(binding)) { "Vertex binding $binding is not set" }
                buffer.requireOpen()
                val offset = requireNotNull(offsets)[binding]
                if (directVertexBuffers[binding] !== buffer || directVertexOffsets[binding] != offset) {
                    pass.setVertexBuffer(binding, buffer.slice(offset, buffer.sizeBytes - offset))
                    directVertexBuffers[binding] = buffer
                    directVertexOffsets[binding] = offset
                    directVertexBindingsMask = directVertexBindingsMask or (1 shl binding)
                }
            }
        }
        val f = recorder.flags[index].toInt()
        val count = recorder.vertexCounts[index]
        val instances = recorder.directInstanceCount!![index]
        val firstInstance = recorder.directFirstInstance!![index]
        if ((f and DrawCallRecorder.FLAG_INDEXED) != 0) {
            val indices = requireNotNull(recorder.directIndexBuffers!![index]) { "Index buffer is not bound" }
            indices.requireOpen()
            if (directIndexBuffer !== indices) {
                pass.setIndexBuffer(
                    indices.gpuBuffer,
                    indices.indexType
                )
                directIndexBuffer = indices
                boundSequentialBuffer = null
            }
            pass.drawIndexed(
                count,
                instances,
                recorder.directFirstIndex!![index],
                recorder.directBaseVertex!![index],
                firstInstance
            )
        } else {
            pass.draw(count, instances, recorder.directFirstVertex!![index], firstInstance)
        }
    }

    fun finish() {
        closeCurrentPass()
        resetPassState()
        activeDebugGroups.clear()
        passDebugGroupDepth = 0
        encoder = null
        preset = null
    }

    private fun closeCurrentPass() {
        val pass = currentPass ?: return
        currentPass = null
        try {
            while (passDebugGroupDepth > 0) {
                pass.popDebugGroup()
                passDebugGroupDepth--
            }
        } finally {
            passDebugGroupDepth = 0
            pass.close()
        }
    }

    private fun resetPassState() {
        currentProgram = null
        currentPipeline = null
        currentUboOffset = -1L
        currentUboSize = -1L
        var texMask = boundTextureMask
        if (texMask != 0) {
            while (texMask != 0) {
                val slot = Integer.numberOfTrailingZeros(texMask)
                boundTextureViews[slot] = null
                boundTextureSamplers[slot] = null
                texMask = texMask and (texMask - 1)
            }
            boundTextureMask = 0
        }
        resetDirectBindings()
        boundSequentialBuffer = null
        boundStagingVertexBuffer = false
    }

    private fun resetDirectBindings() {
        var mask = directVertexBindingsMask
        if (mask != 0) {
            while (mask != 0) {
                val slot = Integer.numberOfTrailingZeros(mask)
                directVertexBuffers[slot] = null
                directVertexOffsets[slot] = 0L
                mask = mask and (mask - 1)
            }
            directVertexBindingsMask = 0
        }
        directIndexBuffer = null
    }
}
