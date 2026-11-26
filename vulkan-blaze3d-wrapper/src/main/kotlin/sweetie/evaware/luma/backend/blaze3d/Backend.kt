package sweetie.evaware.luma.backend.blaze3d

import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.LumaNames
import sweetie.evaware.luma.api.*
import sweetie.evaware.luma.backend.blaze3d.preset.PresetFence
import sweetie.evaware.luma.backend.blaze3d.preset.VulkanPreset
import sweetie.evaware.luma.backend.blaze3d.preset.VulkanPresets
import sweetie.evaware.luma.shader.translator.ShaderAttributeInfo
import sweetie.evaware.luma.shader.translator.ShaderReflection
import sweetie.evaware.luma.shader.translator.ShaderSamplerInfo
import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.ShaderVertType
import sweetie.evaware.luma.vertex.VertexCursor
import sweetie.evaware.luma.vertex.VertexInputLayout
import sweetie.evaware.luma.vertex.VertexLayout

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
    private val renderState = RenderStateTracker()
    private val postRecordActions = ArrayList<() -> Unit>()
    private val schedulePostRecordAction: (() -> Unit) -> Unit = { schedulePostRecord(it) }
    private val samplerCache = VulkanSamplerCache(preset, retirement)
    private val textureUploads = TextureUploadQueue(config.maxPooledUploadBytes, retirement)
    private val bufferUploads = BufferUploadQueue(config.initialBufferUploadBytes)
    private var blitProgram: Program? = null
    private var closed = false
    private val passEncoder = RenderPassEncoder(vertexBuffer, uboBuffer, samplerCache, ::getOrCreateBlitProgram)

    private val boundVertexBuffers = arrayOfNulls<VulkanVertexBuffer>(16)
    private val boundVertexOffsets = LongArray(16)
    private var boundVertexBindingsMask = 0
    private var boundIndexBuffer: VulkanIndexBuffer? = null

    private val targetStack = arrayOfNulls<VulkanRenderTarget>(64)
    private var targetStackDepth = 0
    private var targetPassId = 0
    private var completionFenceRequested = false
    private var completionFence: PresetFence? = null

    override val supportsStencil: Boolean = false
    override val supportsGpuTimestamps: Boolean get() = preset.supportsTimestamps
    override val isDepthTestEnabled: Boolean get() = renderState.depthEnabled
    override val isDepthWriteEnabled: Boolean get() = renderState.depthWrite
    override val currentDepthCompare: DepthCompare get() = renderState.depthFunc
    override val colorMaskRed: Boolean get() = renderState.colorMaskRed
    override val colorMaskGreen: Boolean get() = renderState.colorMaskGreen
    override val colorMaskBlue: Boolean get() = renderState.colorMaskBlue
    override val colorMaskAlpha: Boolean get() = renderState.colorMaskAlpha

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

    override fun createTexture(
        image: BufferedImage,
        mipmap: Boolean,
        format: TextureFormat,
        sampler: SamplerState
    ): TextureHandle {
        require(!mipmap) { "Automatic mipmap generation is not supported by the Vulkan backend" }
        return VulkanTexture.create(
            image,
            samplerCache.get(sampler),
            sampler,
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
        mipmap: Boolean,
        sampler: SamplerState
    ): TextureHandle {
        require(!mipmap) { "Automatic mipmap generation is not supported by the Vulkan backend" }
        return VulkanTexture.createRaw(
            width,
            height,
            data,
            format,
            samplerCache.get(sampler),
            sampler,
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

    override fun setTextureSampler(texture: TextureHandle, sampler: SamplerState) {
        val vulkanTexture = texture as VulkanTexture
        vulkanTexture.requireOpen()
        vulkanTexture.attachRetirement(retirement)
        vulkanTexture.replaceSampler(samplerCache.get(sampler), sampler, newOwnsSampler = false)
    }

    override fun generateMipmaps(texture: TextureHandle) {
        throw UnsupportedOperationException("Mipmap generation is not supported by the Vulkan backend")
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
        format: RenderTargetFormat,
        sampler: SamplerState,
        useStencil: Boolean
    ): RenderTargetHandle {
        return VulkanRenderTarget.create(
            width,
            height,
            useDepth,
            format,
            samplerCache.get(sampler),
            sampler,
            useStencil,
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

    override fun blitRenderTarget(
        source: RenderTargetHandle,
        destination: RenderTargetHandle?,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int,
        dstX: Int,
        dstY: Int,
        dstWidth: Int,
        dstHeight: Int,
        filter: TextureFilter,
        copyColor: Boolean,
        copyDepth: Boolean
    ) {
        check(targetStackDepth > 0) { "Cannot blit outside a frame" }
        require(copyColor || copyDepth) { "blitRenderTarget must copy at least color or depth" }
        require(srcWidth > 0 && srcHeight > 0 && dstWidth > 0 && dstHeight > 0) {
            "Blit dimensions must be positive"
        }
        val vkSource = source as VulkanRenderTarget
        vkSource.requireOpen()
        vkSource.attachRetirement(retirement)
        val vkDestination = (destination as? VulkanRenderTarget)
            ?: checkNotNull(targetStack[targetStackDepth - 1]) {
                "Vulkan blitRenderTarget requires a destination render target or an active render target"
            }
        vkDestination.requireOpen()
        vkDestination.attachRetirement(retirement)

        require(vkSource !== vkDestination) {
            "Source and destination render targets must differ for Vulkan blitRenderTarget"
        }
        require(srcX >= 0 && srcY >= 0 && srcX + srcWidth <= vkSource.width && srcY + srcHeight <= vkSource.height) {
            "Source blit region [$srcX, $srcY, ${srcWidth}x${srcHeight}] exceeds source [${vkSource.width}x${vkSource.height}]"
        }
        require(dstX >= 0 && dstY >= 0 && dstX + dstWidth <= vkDestination.width && dstY + dstHeight <= vkDestination.height) {
            "Destination blit region [$dstX, $dstY, ${dstWidth}x${dstHeight}] exceeds destination [${vkDestination.width}x${vkDestination.height}]"
        }

        val sameSize = srcWidth == dstWidth && srcHeight == dstHeight
        if (copyDepth) {
            require(vkSource.gpuDepthTexture != null && vkDestination.gpuDepthTexture != null) {
                "Depth blit requires depth attachments on both source and destination"
            }
            require(sameSize && vkSource !== vkDestination) {
                "Scaled or self depth blit is not supported by the Vulkan backend"
            }
        }

        val nativeCopy = sameSize &&
            vkSource !== vkDestination &&
            destination != null &&
            (!copyColor || vkSource.format == vkDestination.format)

        val vertexOffset = if (nativeCopy || !copyColor) {
            -1L
        } else {
            val x0 = (dstX.toFloat() / vkDestination.width) * 2f - 1f
            val x1 = ((dstX + dstWidth).toFloat() / vkDestination.width) * 2f - 1f
            val y0 = (dstY.toFloat() / vkDestination.height) * 2f - 1f
            val y1 = ((dstY + dstHeight).toFloat() / vkDestination.height) * 2f - 1f
            val u0 = srcX.toFloat() / vkSource.width
            val u1 = (srcX + srcWidth).toFloat() / vkSource.width
            val v0 = srcY.toFloat() / vkSource.height
            val v1 = (srcY + srcHeight).toFloat() / vkSource.height
            val offset = vertexStaging.beginAlignedWrite(
                RenderPassEncoder.BLIT_QUAD_BYTES.toInt(),
                RenderPassEncoder.BLIT_QUAD_STRIDE_BYTES
            )
            val addr = vertexStaging.baseAddress + offset
            MemoryUtil.memPutFloat(addr, x0); MemoryUtil.memPutFloat(addr + 4L, y0); MemoryUtil.memPutFloat(addr + 8L, u0); MemoryUtil.memPutFloat(addr + 12L, v0)
            MemoryUtil.memPutFloat(addr + 16L, x1); MemoryUtil.memPutFloat(addr + 20L, y0); MemoryUtil.memPutFloat(addr + 24L, u1); MemoryUtil.memPutFloat(addr + 28L, v0)
            MemoryUtil.memPutFloat(addr + 32L, x1); MemoryUtil.memPutFloat(addr + 36L, y1); MemoryUtil.memPutFloat(addr + 40L, u1); MemoryUtil.memPutFloat(addr + 44L, v1)
            MemoryUtil.memPutFloat(addr + 48L, x0); MemoryUtil.memPutFloat(addr + 52L, y0); MemoryUtil.memPutFloat(addr + 56L, u0); MemoryUtil.memPutFloat(addr + 60L, v0)
            MemoryUtil.memPutFloat(addr + 64L, x1); MemoryUtil.memPutFloat(addr + 68L, y1); MemoryUtil.memPutFloat(addr + 72L, u1); MemoryUtil.memPutFloat(addr + 76L, v1)
            MemoryUtil.memPutFloat(addr + 80L, x0); MemoryUtil.memPutFloat(addr + 84L, y1); MemoryUtil.memPutFloat(addr + 88L, u0); MemoryUtil.memPutFloat(addr + 92L, v1)
            vertexStaging.commitDirect(offset, RenderPassEncoder.BLIT_QUAD_BYTES.toInt())
            offset
        }

        recorder.recordBlit(
            source = vkSource,
            destination = vkDestination,
            targetPassId = targetPassId,
            srcX = srcX,
            srcY = srcY,
            srcWidth = srcWidth,
            srcHeight = srcHeight,
            dstX = dstX,
            dstY = dstY,
            dstWidth = dstWidth,
            dstHeight = dstHeight,
            linearFilter = filter == TextureFilter.LINEAR,
            copyColor = copyColor,
            copyDepth = copyDepth,
            nativeCopy = nativeCopy,
            vertexOffset = vertexOffset
        )
    }

    override fun blend(enabled: Boolean) = renderState.blend(enabled)
    override fun blendFunction(function: BlendFunction) = renderState.blendFunction(function)
    override fun colorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) =
        renderState.colorMask(red, green, blue, alpha)
    override fun depthTest(enabled: Boolean) = renderState.depthTest(enabled)
    override fun depthWrite(enabled: Boolean) = renderState.depthWrite(enabled)
    override fun depthCompare(compare: DepthCompare) = renderState.depthCompare(compare)
    override fun cull(enabled: Boolean) = renderState.cull(enabled)
    override fun scissor(x: Int, y: Int, width: Int, height: Int) = renderState.scissor(x, y, width, height)
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

    override fun createTimestampPool(capacity: Int): GpuTimestampPool {
        require(capacity > 0) { "Timestamp pool capacity must be positive: $capacity" }
        check(preset.supportsTimestamps) { "GPU timestamp queries are not supported by ${preset.name}" }
        val pool = preset.createTimestampQueryPool(capacity)
        return VulkanTimestampPool(pool, preset.timestampPeriodNanos, retirement) { closed }
    }

    override fun writeTimestamp(pool: GpuTimestampPool, queryIndex: Int) {
        check(targetStackDepth > 0) { "Cannot write GPU timestamp outside a frame" }
        val vkPool = pool as VulkanTimestampPool
        vkPool.requireOpen()
        require(queryIndex in 0 until vkPool.capacity) { "Query index $queryIndex out of bounds 0..${vkPool.capacity - 1}" }
        recorder.recordTimestamp(vkPool.presetPool, queryIndex, targetPassId)
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

    override fun beginVertices(
        program: ProgramHandle,
        vertexCount: Int,
        cursor: VertexCursor
    ) {
        val prog = program as Program
        prog.requireOpen()
        val strideBytes = prog.strideBytes
        val bytes = Math.multiplyExact(vertexCount, strideBytes)
        vertexStaging.beginDirectAligned(bytes, strideBytes, cursor)
    }

    override fun endVertices(
        program: ProgramHandle,
        cursor: VertexCursor,
        vertexCount: Int,
        uniforms: ShaderUniforms,
        primitiveType: PrimitiveType
    ) {
        if (vertexCount <= 0) return
        val prog = program as Program
        val vertexOffset = cursor.token
        val vertexBytes = vertexCount * prog.strideBytes
        vertexStaging.commitDirect(vertexOffset, vertexBytes)
        recordImmediateDraw(prog, vertexOffset, vertexBytes, vertexCount, uniforms, primitiveType)
    }

    private fun recordImmediateDraw(
        prog: Program,
        vertexOffset: Long,
        vertexBytes: Int,
        vertexCount: Int,
        uniforms: ShaderUniforms,
        primitiveType: PrimitiveType
    ) {
        val uboBytes = prepareUniforms(prog, uniforms)
        val uboOffset = if (uboBytes > 0) prog.uboCacheOffset else 0L
        val uboBytesLong = uboBytes.toLong()
        val pipelineKey = renderState.statePipelineBits or primitiveType.ordinal.toLong()
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
            renderState.statePipelineBits or primitiveType.ordinal.toLong(),
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
            renderState.statePipelineBits or primitiveType.ordinal.toLong(),
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

    private fun getOrCreateBlitProgram(): Program {
        blitProgram?.let { return it }
        val layout = VertexLayout().apply {
            add(ShaderVertType.FLOAT, 2, 0)
            add(ShaderVertType.FLOAT, 2, 1)
        }
        val reflection = ShaderReflection(
            attributes = listOf(
                ShaderAttributeInfo(0, "vec2", "a0", "Position"),
                ShaderAttributeInfo(1, "vec2", "a1", "UV0")
            ),
            uniforms = emptyList(),
            samplers = listOf(
                ShaderSamplerInfo("uSource", "${LumaNames.SAMPLER_PREFIX}0", "sampler2D", unit = 0, binding = 0)
            )
        )
        return Program(
            "${LumaNames.SHADER_NAMESPACE}:internal_blit",
            INTERNAL_BLIT_VERT,
            INTERNAL_BLIT_FRAG,
            VertexInputLayout().binding(layout),
            schedulePostRecordAction,
            retirement,
            reflection
        ).also { blitProgram = it }
    }

    override fun close() {
        if (closed) return
        closed = true
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
        blitProgram?.close()
        blitProgram = null
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

    private companion object {
        const val INTERNAL_BLIT_VERT = """#version 450

layout(location = 0) in vec2 Position;
layout(location = 1) in vec2 UV0;
layout(location = 0) out vec2 vUv;

void main() {
    gl_Position = vec4(Position, 0.0, 1.0);
    vUv = UV0;
}
"""
        const val INTERNAL_BLIT_FRAG = """#version 450

layout(binding = 0) uniform sampler2D ${LumaNames.SAMPLER_PREFIX}0;
layout(location = 0) in vec2 vUv;
layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = texture(${LumaNames.SAMPLER_PREFIX}0, vUv);
}
"""
    }
}
