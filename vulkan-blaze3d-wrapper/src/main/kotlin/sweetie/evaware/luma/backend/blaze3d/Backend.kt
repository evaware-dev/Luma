package sweetie.evaware.luma.backend.blaze3d

import sweetie.evaware.luma.backend.blaze3d.preset.PresetFilterMode
import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.LumaNames
import sweetie.evaware.luma.api.BlendFunction
import sweetie.evaware.luma.api.BufferUsage
import sweetie.evaware.luma.api.DepthCompare
import sweetie.evaware.luma.api.IndexBufferHandle
import sweetie.evaware.luma.api.IndexType
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.api.RenderBackend
import sweetie.evaware.luma.api.RenderTargetFilter
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.api.VertexBufferHandle
import sweetie.evaware.luma.uniform.*
import sweetie.evaware.luma.vertex.VertexLayout
import sweetie.evaware.luma.vertex.VertexInputLayout
import sweetie.evaware.luma.backend.blaze3d.preset.VulkanPreset
import sweetie.evaware.luma.backend.blaze3d.preset.VulkanPresets
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.FloatBuffer

class Backend(
    val preset: VulkanPreset = VulkanPresets.active,
    private val config: VulkanBackendConfig = VulkanBackendConfig()
) : RenderBackend {
    constructor(config: VulkanBackendConfig) : this(VulkanPresets.active, config)
    private var nextProgramId = 1

    private var frameId = 0L
    private var pipelineGeneration = 0L

    private val vertexBuffer = VulkanBuffer(
        { LumaNames.VERTEX_BUFFER },
        32 or 8,
        config.initialVertexBufferBytes,
        preset
    )
    private val uboBuffer = VulkanBuffer(
        { LumaNames.UBO_BUFFER },
        128 or 8,
        config.initialUniformBufferBytes,
        preset
    )

    private val boundTextures = arrayOfNulls<TextureHandle>(DrawCall.TEXTURE_UNITS)
    private var boundTextureMask = 0

    private val vertexStaging = StagingBuffer(config.initialVertexStagingBytes)
    private val uboStaging = StagingBuffer(config.initialUniformStagingBytes)
    private val uniformWriter = Std140Writer()
    private val uniformOffsetAlignment = preset.minUniformOffsetAlignment

    private val recorder = DrawCallRecorder()
    private val merger = DrawCallMerger()
    private val passEncoder = RenderPassEncoder(vertexBuffer, uboBuffer)
    private val renderState = RenderStateTracker()
    private val postRecordActions = ArrayList<() -> Unit>()
    private val schedulePostRecordAction: (() -> Unit) -> Unit = { schedulePostRecord(it) }
    private val samplerCache = VulkanSamplerCache(preset)
    private val textureUploads = TextureUploadQueue(config.maxPooledUploadBytes)
    private val bufferUploads = BufferUploadQueue(config.initialBufferUploadBytes)

    private val boundVertexBuffers = arrayOfNulls<VulkanVertexBuffer>(16)
    private val boundVertexOffsets = LongArray(16)
    private var boundVertexBindingsMask = 0
    private var boundIndexBuffer: VulkanIndexBuffer? = null

    private val targetStack = arrayOfNulls<VulkanRenderTarget>(64)
    private var targetStackDepth = 0
    private var targetPassId = 0
    private var completionFenceRequested = false
    private var completionFence: sweetie.evaware.luma.backend.blaze3d.preset.PresetFence? = null

    override fun beginFrame() {
        VulkanResourceRetirement.collectCompleted()
        renderState.beginFrame()
        frameId++
        vertexBuffer.beginFrame()
        uboBuffer.beginFrame()
        recorder.reset()
        vertexStaging.reset()
        uboStaging.reset()
        bufferUploads.beginFrame()

        targetStack[0] = null
        targetStackDepth = 1
        targetPassId = 0
    }

    override fun endFrame() {
        if (
            recorder.size == 0 &&
            !textureUploads.hasPending() &&
            !bufferUploads.hasPending() &&
            !VulkanResourceRetirement.hasPending() &&
            !completionFenceRequested
        ) {
            runPostRecordActions()
            return
        }

        val encoder = preset.createCommandEncoder()

        textureUploads.record(encoder)
        bufferUploads.record(encoder)

        if (recorder.size > 0) {
            vertexStaging.flip()
            vertexBuffer.write(encoder, vertexStaging.buffer)

            uboStaging.flip()
            uboBuffer.write(encoder, uboStaging.buffer)

            val pass = passEncoder.begin(preset, encoder, pipelineGeneration)
            try {
                merger.run(recorder, pass)
            } finally {
                pass.finish()
            }
        }

        val retirement = VulkanResourceRetirement.attachTo(encoder)
        val requestedFence = if (completionFenceRequested) encoder.createFence() else null
        encoder.submit()
        VulkanResourceRetirement.submitted(retirement)
        completionFence = requestedFence
        completionFenceRequested = false
        runPostRecordActions()
    }

    override fun hasContext(): Boolean = true

    fun requestCompletionFence() {
        check(!completionFenceRequested && completionFence == null) { "A completion fence is already pending" }
        completionFenceRequested = true
    }

    fun awaitCompletionFence() {
        val fence = checkNotNull(completionFence) { "No submitted completion fence" }
        try {
            check(fence.awaitCompletion(Long.MAX_VALUE)) { "Timed out waiting for Vulkan work" }
        } finally {
            fence.close()
            completionFence = null
        }
    }

    override fun createProgram(
        vertexSource: String,
        fragmentSource: String,
        layout: VertexLayout
    ): ProgramHandle = createProgram(vertexSource, fragmentSource, VertexInputLayout().binding(layout))

    override fun createProgram(
        vertexSource: String,
        fragmentSource: String,
        layouts: VertexInputLayout
    ): ProgramHandle {
        val prog = Program(
            "${LumaNames.SHADER_NAMESPACE}:${LumaNames.PROGRAM_PREFIX}${nextProgramId++}",
            vertexSource,
            fragmentSource,
            layouts,
            schedulePostRecordAction
        )
        if (config.precompileDefaultPipeline) {
            prog.precompileDefaults(preset, pipelineGeneration)
        }
        return prog
    }

    override fun bindProgram(program: ProgramHandle) {}

    override fun createTexture(image: BufferedImage, mipmap: Boolean): TextureHandle {
        require(!mipmap) { "Automatic mipmap generation is not supported by the Vulkan backend" }
        return VulkanTexture.create(
            image,
            samplerCache.get(VulkanSamplerDescriptor.clamp(PresetFilterMode.LINEAR)),
            textureUploads,
            preset
        )
    }

    override fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage) {
        (texture as VulkanTexture).update(x, y, image)
    }

    override fun bindTexture(texture: TextureHandle, unit: Int) {
        require(unit in boundTextures.indices) {
            "Texture unit $unit is outside 0..${boundTextures.lastIndex}"
        }
        val vulkanTexture = texture as VulkanTexture
        vulkanTexture.requireOpen()
        if (boundTextures[unit] === vulkanTexture) return
        boundTextures[unit] = vulkanTexture
        boundTextureMask = boundTextureMask or (1 shl unit)
    }

    override fun createVertexBuffer(sizeBytes: Long, usage: BufferUsage): VertexBufferHandle {
        require(sizeBytes > 0L) { "Buffer size must be positive" }
        return VulkanVertexBuffer(sizeBytes, usage, preset)
    }

    override fun createIndexBuffer(sizeBytes: Long, indexType: IndexType, usage: BufferUsage): IndexBufferHandle {
        require(sizeBytes > 0L) { "Buffer size must be positive" }
        return VulkanIndexBuffer(sizeBytes, indexType, usage, preset)
    }

    override fun updateVertexBuffer(buffer: VertexBufferHandle, offsetBytes: Long, data: ByteBuffer) {
        bufferUploads.add(buffer as VulkanVertexBuffer, offsetBytes, data)
    }

    override fun updateIndexBuffer(buffer: IndexBufferHandle, offsetBytes: Long, data: ByteBuffer) {
        bufferUploads.add(buffer as VulkanIndexBuffer, offsetBytes, data)
    }

    override fun bindVertexBuffer(binding: Int, buffer: VertexBufferHandle, offsetBytes: Long) {
        require(binding in boundVertexBuffers.indices) { "Vertex binding $binding is out of range" }
        require(offsetBytes >= 0L && offsetBytes < buffer.sizeBytes) { "Vertex buffer offset is out of bounds" }
        val vertexBuffer = buffer as VulkanVertexBuffer
        vertexBuffer.requireOpen()
        if (boundVertexBuffers[binding] === vertexBuffer && boundVertexOffsets[binding] == offsetBytes) return
        boundVertexBuffers[binding] = vertexBuffer
        boundVertexOffsets[binding] = offsetBytes
        boundVertexBindingsMask = boundVertexBindingsMask or (1 shl binding)
    }

    override fun bindIndexBuffer(buffer: IndexBufferHandle) {
        val indexBuffer = buffer as VulkanIndexBuffer
        indexBuffer.requireOpen()
        if (boundIndexBuffer === indexBuffer) return
        boundIndexBuffer = indexBuffer
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
        val gpuFilter = when (filter) {
            RenderTargetFilter.NEAREST -> PresetFilterMode.NEAREST
            RenderTargetFilter.LINEAR -> PresetFilterMode.LINEAR
        }
        return VulkanRenderTarget.create(
            width,
            height,
            useDepth,
            format,
            samplerCache.get(VulkanSamplerDescriptor.clamp(gpuFilter)),
            textureUploads,
            preset
        )
    }

    override fun beginRenderTarget(target: RenderTargetHandle, clearColor: FloatArray?) {
        val vulkanTarget = target as VulkanRenderTarget
        vulkanTarget.requireOpen()
        check(targetStackDepth < targetStack.size) { "Render target stack overflow" }
        targetStack[targetStackDepth++] = vulkanTarget
        targetPassId++
        if (clearColor != null) {
            require(clearColor.size >= 4) { "Clear color must contain four components" }
            recordClear(
                color = true,
                red = clearColor[0],
                green = clearColor[1],
                blue = clearColor[2],
                alpha = clearColor[3],
                depth = vulkanTarget.gpuDepthTexture != null,
                depthValue = 1.0
            )
        }
    }

    override fun endRenderTarget() {
        check(targetStackDepth > 1) { "No render target to end" }
        targetStack[--targetStackDepth] = null
        targetPassId++
    }

    override fun blend(enabled: Boolean) = renderState.blend(enabled)

    override fun blendFunction(function: BlendFunction) =
        renderState.blendFunction(function)

    override fun depthTest(enabled: Boolean) = renderState.depthTest(enabled)

    override fun depthWrite(enabled: Boolean) = renderState.depthWrite(enabled)

    override fun depthCompare(compare: DepthCompare) = renderState.depthCompare(compare)

    override fun cull(enabled: Boolean) = renderState.cull(enabled)

    override fun scissor(x: Int, y: Int, width: Int, height: Int) =
        renderState.scissor(x, y, width, height)

    override fun disableScissor() = renderState.disableScissor()

    override fun clearColor(red: Float, green: Float, blue: Float, alpha: Float) {
        recordClear(true, red, green, blue, alpha, false, 1.0)
    }

    override fun clearDepth(depth: Double) {
        require(depth in 0.0..1.0) { "Depth clear value must be in 0..1" }
        recordClear(false, 0f, 0f, 0f, 0f, true, depth)
    }

    private fun recordClear(
        color: Boolean,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        depth: Boolean,
        depthValue: Double
    ) {
        check(targetStackDepth > 0) { "Cannot clear outside a frame" }
        val draw = recorder.obtain()
        draw.target = targetStack[targetStackDepth - 1]
        draw.targetPassId = targetPassId
        draw.clearColorEnabled = color
        draw.clearDepthEnabled = depth
        draw.clearRed = red
        draw.clearGreen = green
        draw.clearBlue = blue
        draw.clearAlpha = alpha
        draw.clearDepth = depthValue
    }

    override fun invalidatePipelineCache() {
        pipelineGeneration++
    }

    override fun draw(
        program: ProgramHandle,
        vertices: FloatBuffer,
        vertexCount: Int,
        uniforms: ShaderUniforms,
        primitiveType: PrimitiveType
    ) {
        val prog = program as Program
        prog.requireOpen()
        val strideBytes = prog.layout.strideFloats * Float.SIZE_BYTES
        val vertexBytes = vertexCount * strideBytes
        val vertexOffset = vertexStaging.appendFromAddressAligned(
            MemoryUtil.memAddress(vertices),
            vertexBytes,
            strideBytes
        )

        val draw = recordDraw(prog, uniforms, primitiveType)
        draw.vertexOffset = vertexOffset
        draw.vertexBytes = vertexBytes.toLong()
        draw.vertexCount = vertexCount
    }

    override fun draw(
        program: ProgramHandle,
        uniforms: ShaderUniforms,
        primitiveType: PrimitiveType,
        firstVertex: Int,
        vertexCount: Int,
        instanceCount: Int,
        firstInstance: Int
    ) {
        require(firstVertex >= 0 && vertexCount >= 0 && instanceCount >= 0 && firstInstance >= 0)
        require(primitiveType != PrimitiveType.QUADS) { "Direct QUADS require an index buffer" }
        val draw = recordDraw(program as Program, uniforms, primitiveType)
        boundVertexBuffers.copyInto(draw.vertexBuffers)
        boundVertexOffsets.copyInto(draw.vertexOffsets)
        draw.vertexBindingsMask = boundVertexBindingsMask
        draw.firstVertex = firstVertex
        draw.vertexCount = vertexCount
        draw.instanceCount = instanceCount
        draw.firstInstance = firstInstance
        draw.direct = true
    }

    override fun drawIndexed(
        program: ProgramHandle,
        uniforms: ShaderUniforms,
        primitiveType: PrimitiveType,
        firstIndex: Int,
        indexCount: Int,
        vertexOffset: Int,
        instanceCount: Int,
        firstInstance: Int
    ) {
        require(firstIndex >= 0 && indexCount >= 0 && instanceCount >= 0 && firstInstance >= 0)
        require(primitiveType != PrimitiveType.QUADS) { "Indexed QUADS must use TRIANGLES topology" }
        val draw = recordDraw(program as Program, uniforms, primitiveType)
        boundVertexBuffers.copyInto(draw.vertexBuffers)
        boundVertexOffsets.copyInto(draw.vertexOffsets)
        draw.vertexBindingsMask = boundVertexBindingsMask
        draw.indexBuffer = requireNotNull(boundIndexBuffer) { "Index buffer is not bound" }
        draw.firstIndex = firstIndex
        draw.vertexCount = indexCount
        draw.baseVertex = vertexOffset
        draw.instanceCount = instanceCount
        draw.firstInstance = firstInstance
        draw.indexed = true
        draw.direct = true
    }

    private fun recordDraw(prog: Program, uniforms: ShaderUniforms, primitiveType: PrimitiveType): DrawCall {
        prog.requireOpen()

        val uboOffset: Long
        val uboBytes: Int
        val vulkanUniforms = prog.vulkanUniforms
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

            if (!anyDirty && prog.uboCacheFrameId == frameId && prog.uboCacheUniforms === uniforms) {
                uboOffset = prog.uboCacheOffset
                uboBytes = prog.uboCacheBytes
            } else {
                uboOffset = uboStaging.beginAlignedWrite(
                    maxOf(config.uniformScratchBytes, prog.uniformBlockBytes),
                    uniformOffsetAlignment
                )
                uniformWriter.begin(uboStaging.buffer)
                for (i in vulkanUniforms.indices) {
                    vulkanUniforms[i].write(uniformWriter, uniforms)
                }
                uboBytes = uniformWriter.size()
                for (i in vulkanUniforms.indices) {
                    vulkanUniforms[i].clearDirty(uniforms)
                }
                prog.uboCacheOffset = uboOffset
                prog.uboCacheBytes = uboBytes
                prog.uboCacheFrameId = frameId
                prog.uboCacheUniforms = uniforms
            }
        }

        val draw = recorder.obtain()
        draw.program = prog
        draw.uboOffset = uboOffset
        draw.uboBytes = uboBytes.toLong()
        boundTextures.copyInto(draw.textures)
        draw.textureMask = boundTextureMask
        draw.primitiveType = primitiveType
        draw.blendEnabled = renderState.blendEnabled
        draw.blendFunction = renderState.blendFunction
        draw.depthEnabled = renderState.depthEnabled
        draw.depthWrite = draw.depthEnabled && renderState.depthWrite
        draw.depthFunc = if (draw.depthEnabled) renderState.depthFunc else DepthCompare.ALWAYS
        draw.cullEnabled = renderState.cullEnabled
        draw.scissorEnabled = renderState.scissorEnabled
        draw.scissorX = renderState.scissorX
        draw.scissorY = renderState.scissorY
        draw.scissorWidth = renderState.scissorWidth
        draw.scissorHeight = renderState.scissorHeight
        draw.target = targetStack[targetStackDepth - 1]
        draw.targetPassId = targetPassId
        return draw
    }

    override fun close() {
        completionFence?.let {
            try {
                it.awaitCompletion(Long.MAX_VALUE)
            } finally {
                it.close()
                completionFence = null
            }
        }
        completionFenceRequested = false
        textureUploads.close()
        bufferUploads.close()
        vertexStaging.close()
        uboStaging.close()
        VulkanResourceRetirement.closeAll(preset::createCommandEncoder)
        runPostRecordActions()
        vertexBuffer.close()
        uboBuffer.close()
        samplerCache.close()
        VulkanResourceRetirement.closeAll(preset::createCommandEncoder)
    }

    private fun schedulePostRecord(action: () -> Unit) {
        postRecordActions.add(action)
    }

    private fun runPostRecordActions() {
        for (index in postRecordActions.indices) postRecordActions[index]()
        postRecordActions.clear()
    }
}
