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
import sweetie.evaware.luma.api.TextureFormat
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.api.VertexBufferHandle
import sweetie.evaware.luma.shader.translator.ShaderReflection
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
    internal val retirement = VulkanRetirementQueue()

    private val vertexBuffer = VulkanBuffer(
        { LumaNames.VERTEX_BUFFER },
        32 or 8,
        config.initialVertexBufferBytes,
        preset,
        retirement
    )
    private val uboBuffer = VulkanBuffer(
        { LumaNames.UBO_BUFFER },
        128 or 8,
        config.initialUniformBufferBytes,
        preset,
        retirement
    )

    private val boundTextures = arrayOfNulls<TextureHandle>(DrawCallRecorder.TEXTURE_UNITS)
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
    private val samplerCache = VulkanSamplerCache(preset, retirement)
    private val textureUploads = TextureUploadQueue(config.maxPooledUploadBytes, retirement)
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
        retirement.collectCompleted()
        renderState.beginFrame()
        frameId++
        vertexBuffer.beginFrame()
        uboBuffer.beginFrame()
        recorder.reset()
        vertexStaging.reset()
        uboStaging.reset()
        bufferUploads.beginFrame()

        var texMask = boundTextureMask
        while (texMask != 0) {
            val slot = Integer.numberOfTrailingZeros(texMask)
            boundTextures[slot] = null
            texMask = texMask and (texMask - 1)
        }
        boundTextureMask = 0

        targetStack[0] = null
        targetStackDepth = 1
        targetPassId = 0
    }

    override fun endFrame() {
        if (
            recorder.size == 0 &&
            !textureUploads.hasPending() &&
            !bufferUploads.hasPending() &&
            !retirement.hasPending() &&
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

        val pendingRetirement = retirement.attachTo(encoder)
        val requestedFence = if (completionFenceRequested) encoder.createFence() else null
        encoder.submit()
        retirement.submitted(pendingRetirement)
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
    ): ProgramHandle = createProgram(vertexSource, fragmentSource, VertexInputLayout().binding(layout), null)

    override fun createProgram(
        vertexSource: String,
        fragmentSource: String,
        layout: VertexLayout,
        reflection: ShaderReflection?
    ): ProgramHandle = createProgram(vertexSource, fragmentSource, VertexInputLayout().binding(layout), reflection)

    override fun createProgram(
        vertexSource: String,
        fragmentSource: String,
        layouts: VertexInputLayout
    ): ProgramHandle = createProgram(vertexSource, fragmentSource, layouts, null)

    override fun createProgram(
        vertexSource: String,
        fragmentSource: String,
        layouts: VertexInputLayout,
        reflection: ShaderReflection?
    ): ProgramHandle {
        val prog = Program(
            "${LumaNames.SHADER_NAMESPACE}:${LumaNames.PROGRAM_PREFIX}${nextProgramId++}",
            vertexSource,
            fragmentSource,
            layouts,
            schedulePostRecordAction,
            retirement,
            reflection
        )
        if (config.precompileDefaultPipeline) {
            prog.precompileDefaults(preset, pipelineGeneration)
        }
        return prog
    }

    override fun bindProgram(program: ProgramHandle) {}

    override fun createTexture(image: BufferedImage, mipmap: Boolean): TextureHandle =
        createTexture(image, mipmap, TextureFormat.RGBA8)

    override fun createTexture(image: BufferedImage, mipmap: Boolean, format: TextureFormat): TextureHandle {
        require(!mipmap) { "Automatic mipmap generation is not supported by the Vulkan backend" }
        return VulkanTexture.create(
            image,
            samplerCache.get(VulkanSamplerDescriptor.clamp(PresetFilterMode.LINEAR)),
            textureUploads,
            preset,
            retirement,
            format
        )
    }

    override fun createTexture(
        width: Int,
        height: Int,
        data: ByteBuffer?,
        format: TextureFormat,
        mipmap: Boolean
    ): TextureHandle {
        require(!mipmap) { "Automatic mipmap generation is not supported by the Vulkan backend" }
        return VulkanTexture.createRaw(
            width,
            height,
            data,
            format,
            samplerCache.get(VulkanSamplerDescriptor.clamp(PresetFilterMode.LINEAR)),
            textureUploads,
            preset,
            retirement
        )
    }

    override fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage) {
        (texture as VulkanTexture).update(x, y, image)
    }

    override fun updateTexture(
        texture: TextureHandle,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        data: ByteBuffer
    ) {
        (texture as VulkanTexture).update(x, y, width, height, data)
    }

    override fun bindTexture(texture: TextureHandle, unit: Int) {
        require(unit in boundTextures.indices) {
            "Texture unit $unit is outside 0..${boundTextures.lastIndex}"
        }
        val vulkanTexture = texture as VulkanTexture
        vulkanTexture.requireOpen()
        vulkanTexture.attachRetirement(retirement)
        val bit = 1 shl unit
        if ((boundTextureMask and bit) != 0 && boundTextures[unit] === vulkanTexture) return
        boundTextures[unit] = vulkanTexture
        boundTextureMask = boundTextureMask or bit
    }

    override fun createVertexBuffer(sizeBytes: Long, usage: BufferUsage): VertexBufferHandle {
        require(sizeBytes > 0L) { "Buffer size must be positive" }
        return VulkanVertexBuffer(sizeBytes, usage, preset, retirement)
    }

    override fun createIndexBuffer(sizeBytes: Long, indexType: IndexType, usage: BufferUsage): IndexBufferHandle {
        require(sizeBytes > 0L) { "Buffer size must be positive" }
        return VulkanIndexBuffer(sizeBytes, indexType, usage, preset, retirement)
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
            preset,
            retirement
        )
    }

    override fun beginRenderTarget(target: RenderTargetHandle, clearColor: FloatArray?) {
        val vulkanTarget = target as VulkanRenderTarget
        vulkanTarget.requireOpen()
        vulkanTarget.attachRetirement(retirement)
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

    override fun pushDebugGroup(label: String) {
        if (targetStackDepth <= 0) return
        recorder.recordDebugGroupPush(label, targetPassId)
    }

    override fun popDebugGroup() {
        if (targetStackDepth <= 0) return
        recorder.recordDebugGroupPop(targetPassId)
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
        recorder.recordClear(
            targetStack[targetStackDepth - 1],
            targetPassId,
            color,
            red,
            green,
            blue,
            alpha,
            depth,
            depthValue
        )
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
        val strideBytes = prog.strideBytes
        val vertexBytes = vertexCount * strideBytes
        val vertexOffset = vertexStaging.appendFromAddressAligned(
            MemoryUtil.memAddress(vertices),
            vertexBytes,
            strideBytes
        )
        val uboBytes = prepareUniforms(prog, uniforms)
        val uboOffset = if (uboBytes > 0) prog.uboCacheOffset else 0L
        val uboBytesLong = uboBytes.toLong()
        val pipelineKey = renderState.statePipelineBits or primitiveType.ordinal
        val target = targetStack[targetStackDepth - 1]
        val scissorXY = renderState.scissorXY
        val scissorWH = renderState.scissorWH
        val texMask = boundTextureMask

        // O(1) tail-merge consecutive identical contiguous immediate draws at record time
        val lastIdx = recorder.size - 1
        if (
            lastIdx >= 0 &&
            recorder.flags[lastIdx] == 0.toByte() &&
            recorder.programs[lastIdx] === prog &&
            recorder.pipelineKeys[lastIdx] == pipelineKey &&
            recorder.targets[lastIdx] === target &&
            recorder.targetPassIds[lastIdx] == targetPassId &&
            recorder.uboOffsets[lastIdx] == uboOffset &&
            recorder.uboBytes[lastIdx] == uboBytesLong &&
            recorder.scissorWH[lastIdx] == scissorWH &&
            (scissorWH == 0L || recorder.scissorXY[lastIdx] == scissorXY) &&
            recorder.vertexOffsets[lastIdx] + recorder.vertexBytes[lastIdx] == vertexOffset &&
            recorder.sameTexturesAt(lastIdx, boundTextures, texMask)
        ) {
            recorder.vertexBytes[lastIdx] += vertexBytes.toLong()
            recorder.vertexCounts[lastIdx] += vertexCount
            return
        }

        if (texMask == 0 || texMask == 1) {
            recorder.recordImmediateSingleTexture(
                prog,
                target,
                targetPassId,
                pipelineKey,
                vertexOffset,
                vertexBytes.toLong(),
                vertexCount,
                uboOffset,
                uboBytesLong,
                scissorXY,
                scissorWH,
                if (texMask == 1) boundTextures[0] else null
            )
        } else {
            recorder.recordImmediate(
                prog,
                target,
                targetPassId,
                pipelineKey,
                vertexOffset,
                vertexBytes.toLong(),
                vertexCount,
                uboOffset,
                uboBytesLong,
                scissorXY,
                scissorWH,
                texMask,
                boundTextures
            )
        }
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
        val prog = program as Program
        prog.requireOpen()
        val uboBytes = prepareUniforms(prog, uniforms)
        val uboOffset = if (uboBytes > 0) prog.uboCacheOffset else 0L
        recorder.recordDirect(
            prog,
            targetStack[targetStackDepth - 1],
            targetPassId,
            renderState.statePipelineBits or primitiveType.ordinal,
            uboOffset,
            uboBytes.toLong(),
            renderState.scissorXY,
            renderState.scissorWH,
            boundTextureMask,
            boundTextures,
            boundVertexBuffers,
            boundVertexOffsets,
            boundVertexBindingsMask,
            null,
            false,
            firstVertex,
            0,
            vertexCount,
            0,
            instanceCount,
            firstInstance
        )
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
        val prog = program as Program
        prog.requireOpen()
        val indexBuffer = requireNotNull(boundIndexBuffer) { "Index buffer is not bound" }
        val uboBytes = prepareUniforms(prog, uniforms)
        val uboOffset = if (uboBytes > 0) prog.uboCacheOffset else 0L
        recorder.recordDirect(
            prog,
            targetStack[targetStackDepth - 1],
            targetPassId,
            renderState.statePipelineBits or primitiveType.ordinal,
            uboOffset,
            uboBytes.toLong(),
            renderState.scissorXY,
            renderState.scissorWH,
            boundTextureMask,
            boundTextures,
            boundVertexBuffers,
            boundVertexOffsets,
            boundVertexBindingsMask,
            indexBuffer,
            true,
            0,
            firstIndex,
            indexCount,
            vertexOffset,
            instanceCount,
            firstInstance
        )
    }

    private fun prepareUniforms(prog: Program, uniforms: ShaderUniforms): Int {
        val vulkanUniforms = prog.vulkanUniforms
        if (vulkanUniforms.isEmpty()) return 0

        val sameUniforms = prog.uboCacheUniforms === uniforms
        val currentRevision = uniforms.registry.revision
        if (sameUniforms && prog.uboCacheFrameId == frameId && prog.uboCacheRevision == currentRevision) {
            return prog.uboCacheBytes
        }

        val handles = prog.resolveUniformHandles(uniforms)
        var anyDirty = !(sameUniforms && prog.uboCacheFrameId == frameId)
        if (!anyDirty) {
            for (i in handles.indices) {
                val handle = handles[i]
                if (handle != null && handle.isDirty) {
                    anyDirty = true
                    break
                }
            }
        }

        if (!anyDirty) {
            prog.uboCacheRevision = currentRevision
            return prog.uboCacheBytes
        }

        val uboOffset = uboStaging.beginAlignedWrite(
            maxOf(config.uniformScratchBytes, prog.uniformBlockBytes),
            uniformOffsetAlignment
        )
        uniformWriter.begin(uboStaging.buffer)
        for (i in vulkanUniforms.indices) {
            val handle = handles[i]
            vulkanUniforms[i].write(uniformWriter, handle)
            if (handle != null) handle.isDirty = false
        }
        val uboBytes = uniformWriter.size()
        prog.uboCacheOffset = uboOffset
        prog.uboCacheBytes = uboBytes
        prog.uboCacheFrameId = frameId
        prog.uboCacheUniforms = uniforms
        prog.uboCacheRevision = currentRevision
        return uboBytes
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
        boundTextures.fill(null)
        boundTextureMask = 0
        boundVertexBuffers.fill(null)
        boundVertexOffsets.fill(0L)
        boundVertexBindingsMask = 0
        boundIndexBuffer = null
        targetStack.fill(null)
        targetStackDepth = 0
        textureUploads.close()
        bufferUploads.close()
        vertexStaging.close()
        uboStaging.close()
        retirement.closeAll(preset::createCommandEncoder)
        runPostRecordActions()
        vertexBuffer.close()
        uboBuffer.close()
        samplerCache.close()
        retirement.closeAll(preset::createCommandEncoder)
    }

    private fun schedulePostRecord(action: () -> Unit) {
        postRecordActions.add(action)
    }

    private fun runPostRecordActions() {
        for (index in postRecordActions.indices) postRecordActions[index]()
        postRecordActions.clear()
    }
}
