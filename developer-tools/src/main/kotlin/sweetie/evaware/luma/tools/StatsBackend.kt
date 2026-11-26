package sweetie.evaware.luma.tools

import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import sweetie.evaware.luma.api.*
import sweetie.evaware.luma.shader.translator.ShaderReflection
import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.VertexCursor
import sweetie.evaware.luma.vertex.VertexInputLayout
import sweetie.evaware.luma.vertex.VertexLayout

class StatsBackend(
    val delegate: RenderBackend,
    val gpuTimers: Boolean = false
) : RenderBackend {
    val currentFrame = FrameStats()
    val lastFrame = FrameStats()
    val lifetime = LifetimeStats()

    private var boundTextures = arrayOfNulls<TextureHandle>(16)
    private var lastDrawProgram: TrackedProgram? = null
    private var frameStartNanos = 0L
    private var frameActive = false
    private var gpuTimerTracker: GpuTimerTracker? = null
    private var gpuTimersChecked = false

    val lastGpuFrameNanos: Long
        get() = gpuTimerTracker?.lastGpuFrameNanos ?: 0L

    val lastGpuFrameIndex: Long
        get() = gpuTimerTracker?.lastGpuFrameIndex ?: 0L

    val gpuZoneCount: Int
        get() = gpuTimerTracker?.completedZoneCount ?: 0

    val gpuZones: List<GpuZoneTiming>
        get() = gpuTimerTracker?.gpuZonesView ?: emptyList()

    fun pollGpuTimers(): Boolean {
        val tracker = gpuTimerTracker ?: return false
        val harvested = tracker.harvestCompleted()
        if (harvested) {
            lastFrame.setGpuFrameNanos(tracker.lastGpuFrameNanos)
        }
        return harvested
    }

    override val supportsStencil: Boolean get() = delegate.supportsStencil
    override val supportsGpuTimestamps: Boolean get() = delegate.supportsGpuTimestamps
    override val isDepthTestEnabled: Boolean get() = delegate.isDepthTestEnabled
    override val isDepthWriteEnabled: Boolean get() = delegate.isDepthWriteEnabled
    override val currentDepthCompare: DepthCompare get() = delegate.currentDepthCompare
    override val colorMaskRed: Boolean get() = delegate.colorMaskRed
    override val colorMaskGreen: Boolean get() = delegate.colorMaskGreen
    override val colorMaskBlue: Boolean get() = delegate.colorMaskBlue
    override val colorMaskAlpha: Boolean get() = delegate.colorMaskAlpha

    override fun beginFrame() {
        check(!frameActive) { "Frame is already active" }
        val index = lifetime.frames + 1L
        currentFrame.reset(index)
        lastDrawProgram = null
        boundTextures.fill(null)
        frameStartNanos = System.nanoTime()
        delegate.beginFrame()
        frameActive = true
        if (gpuTimers) {
            try {
                val tracker = ensureGpuTimerTracker()
                if (tracker != null) {
                    tracker.beginFrame(index)
                    lastFrame.setGpuFrameNanos(tracker.lastGpuFrameNanos)
                }
            } catch (failure: Throwable) {
                frameActive = false
                try {
                    delegate.endFrame()
                } catch (endFailure: Throwable) {
                    failure.addSuppressed(endFailure)
                }
                throw failure
            }
        }
    }

    override fun endFrame() {
        check(frameActive) { "No active frame" }
        val tracker = gpuTimerTracker
        var succeeded = false
        try {
            tracker?.beforeDelegateEndFrame()
            delegate.endFrame()
            succeeded = true
            currentFrame.finish(System.nanoTime() - frameStartNanos)
            if (tracker != null) {
                currentFrame.setGpuFrameNanos(tracker.lastGpuFrameNanos)
            }
            lastFrame.copyFrom(currentFrame)
            lifetime.frames++
        } finally {
            tracker?.afterDelegateEndFrame(succeeded)
            frameActive = false
        }
    }

    override fun createProgram(
        vertexSource: String,
        fragmentSource: String,
        layouts: VertexInputLayout,
        reflection: ShaderReflection?
    ): ProgramHandle {
        val program = delegate.createProgram(vertexSource, fragmentSource, layouts, reflection)
        countFrame(FrameStats::programCreated)
        lifetime.programsCreated++
        val singleLayout = if (layouts.size() == 1) layouts.layout(0) else null
        return TrackedProgram(this, program, singleLayout)
    }

    override fun createProgram(
        vertexSource: String,
        fragmentSource: String,
        layout: VertexLayout,
        reflection: ShaderReflection?
    ): ProgramHandle {
        val program = delegate.createProgram(vertexSource, fragmentSource, layout, reflection)
        countFrame(FrameStats::programCreated)
        lifetime.programsCreated++
        return TrackedProgram(this, program, layout)
    }

    override fun bindProgram(program: ProgramHandle) {
        delegate.bindProgram(program.unwrap())
    }

    override fun createTexture(
        image: BufferedImage,
        mipmap: Boolean,
        format: TextureFormat,
        sampler: SamplerState
    ): TextureHandle {
        val texture = delegate.createTexture(image, mipmap, format, sampler)
        recordTextureCreated(image.width.toLong() * image.height.toLong() * format.bytesPerPixel)
        return texture
    }

    override fun createTexture(
        width: Int,
        height: Int,
        data: ByteBuffer?,
        format: TextureFormat,
        mipmap: Boolean,
        sampler: SamplerState
    ): TextureHandle {
        val texture = delegate.createTexture(width, height, data, format, mipmap, sampler)
        val bytes = if (data != null) width.toLong() * height.toLong() * format.bytesPerPixel else 0L
        countFrame(FrameStats::textureCreated)
        lifetime.texturesCreated++
        if (bytes > 0L) {
            if (frameActive) currentFrame.textureUpload(bytes)
            lifetime.textureUploads++
            lifetime.uploadedBytes += bytes
        }
        return texture
    }

    override fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage) {
        val bytes = image.width.toLong() * image.height.toLong() * texture.format.bytesPerPixel
        delegate.updateTexture(texture, x, y, image)
        if (frameActive) currentFrame.textureUpload(bytes)
        lifetime.textureUploads++
        lifetime.uploadedBytes += bytes
    }

    override fun updateTexture(
        texture: TextureHandle,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        data: ByteBuffer
    ) {
        val bytes = width.toLong() * height.toLong() * texture.format.bytesPerPixel
        delegate.updateTexture(texture, x, y, width, height, data)
        if (frameActive) currentFrame.textureUpload(bytes)
        lifetime.textureUploads++
        lifetime.uploadedBytes += bytes
    }

    override fun setTextureSampler(texture: TextureHandle, sampler: SamplerState) =
        delegate.setTextureSampler(texture, sampler)

    override fun generateMipmaps(texture: TextureHandle) = delegate.generateMipmaps(texture)

    private fun recordTextureCreated(bytes: Long) {
        countFrame(FrameStats::textureCreated)
        if (frameActive) currentFrame.textureUpload(bytes)
        lifetime.texturesCreated++
        lifetime.textureUploads++
        lifetime.uploadedBytes += bytes
    }

    override fun bindTexture(texture: TextureHandle, unit: Int) {
        require(unit >= 0) { "Texture unit must be non-negative: $unit" }
        ensureTextureCapacity(unit + 1)
        val changed = boundTextures[unit] !== texture
        delegate.bindTexture(texture, unit)
        boundTextures[unit] = texture
        if (frameActive) currentFrame.textureBind(changed)
    }

    override fun createVertexBuffer(sizeBytes: Long, usage: BufferUsage) =
        delegate.createVertexBuffer(sizeBytes, usage)

    override fun createIndexBuffer(sizeBytes: Long, indexType: IndexType, usage: BufferUsage) =
        delegate.createIndexBuffer(sizeBytes, indexType, usage)

    override fun updateVertexBuffer(buffer: VertexBufferHandle, offsetBytes: Long, data: ByteBuffer) =
        delegate.updateVertexBuffer(buffer, offsetBytes, data)

    override fun updateIndexBuffer(buffer: IndexBufferHandle, offsetBytes: Long, data: ByteBuffer) =
        delegate.updateIndexBuffer(buffer, offsetBytes, data)

    override fun bindVertexBuffer(binding: Int, buffer: VertexBufferHandle, offsetBytes: Long) =
        delegate.bindVertexBuffer(binding, buffer, offsetBytes)

    override fun bindIndexBuffer(buffer: IndexBufferHandle) = delegate.bindIndexBuffer(buffer)

    override fun beginVertices(
        program: ProgramHandle,
        vertexCount: Int,
        cursor: VertexCursor
    ) {
        val tracked = program.tracked()
        delegate.beginVertices(tracked.delegate, vertexCount, cursor)
    }

    override fun endVertices(
        program: ProgramHandle,
        cursor: VertexCursor,
        vertexCount: Int,
        uniforms: ShaderUniforms,
        primitiveType: PrimitiveType
    ) {
        val tracked = program.tracked()
        delegate.endVertices(tracked.delegate, cursor, vertexCount, uniforms, primitiveType)
        if (vertexCount <= 0) return
        val layout = requireNotNull(tracked.layout) { "Streaming draw requires a single vertex layout" }
        val instances = if (layout.instanced) vertexCount.toLong() else 0L
        val submittedVertices = if (layout.instanced) {
            layout.baseVertexCount.toLong() * vertexCount.toLong()
        } else {
            vertexCount.toLong()
        }
        val primitives = primitiveCount(primitiveType, submittedVertices)
        val programChanged = lastDrawProgram !== tracked
        lastDrawProgram = tracked
        if (frameActive) currentFrame.draw(submittedVertices, instances, primitives, programChanged)
        lifetime.drawCalls++
        lifetime.vertices += submittedVertices
        lifetime.instances += instances
        lifetime.primitives += primitives
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
        val tracked = program.tracked()
        delegate.draw(
            tracked.delegate, uniforms, primitiveType, firstVertex, vertexCount, instanceCount, firstInstance
        )
        countDirectDraw(tracked, primitiveType, vertexCount, instanceCount)
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
        val tracked = program.tracked()
        delegate.drawIndexed(
            tracked.delegate,
            uniforms,
            primitiveType,
            firstIndex,
            indexCount,
            vertexOffset,
            instanceCount,
            firstInstance
        )
        countDirectDraw(tracked, primitiveType, indexCount, instanceCount)
    }

    private fun countDirectDraw(
        program: TrackedProgram,
        primitiveType: PrimitiveType,
        elementCount: Int,
        instanceCount: Int
    ) {
        val submitted = elementCount.toLong() * instanceCount
        val instances = if (instanceCount > 1) instanceCount.toLong() else 0L
        val primitives = primitiveCount(primitiveType, submitted)
        val programChanged = lastDrawProgram !== program
        lastDrawProgram = program
        if (frameActive) currentFrame.draw(submitted, instances, primitives, programChanged)
        lifetime.drawCalls++
        lifetime.vertices += submitted
        lifetime.instances += instances
        lifetime.primitives += primitives
    }

    override fun createRenderTarget(
        width: Int,
        height: Int,
        useDepth: Boolean,
        format: RenderTargetFormat,
        sampler: SamplerState,
        useStencil: Boolean
    ): RenderTargetHandle {
        val target = delegate.createRenderTarget(width, height, useDepth, format, sampler, useStencil)
        countRenderTargetCreation()
        return target
    }

    override fun beginRenderTarget(target: RenderTargetHandle, clearColor: FloatArray?) {
        delegate.beginRenderTarget(target, clearColor)
        if (frameActive) currentFrame.renderTargetPass()
    }

    override fun endRenderTarget() = delegate.endRenderTarget()

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
    ) = delegate.blitRenderTarget(
        source, destination, srcX, srcY, srcWidth, srcHeight, dstX, dstY, dstWidth, dstHeight, filter, copyColor, copyDepth
    )

    override fun blend(enabled: Boolean) = delegate.blend(enabled)
    override fun blendFunction(function: BlendFunction) = delegate.blendFunction(function)
    override fun colorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) =
        delegate.colorMask(red, green, blue, alpha)
    override fun depthTest(enabled: Boolean) = delegate.depthTest(enabled)
    override fun depthWrite(enabled: Boolean) = delegate.depthWrite(enabled)
    override fun depthCompare(compare: DepthCompare) = delegate.depthCompare(compare)
    override fun stencilTest(enabled: Boolean) = delegate.stencilTest(enabled)
    override fun stencilFunc(compare: StencilCompare, ref: Int, readMask: Int) =
        delegate.stencilFunc(compare, ref, readMask)
    override fun stencilOp(stencilFail: StencilOp, depthFail: StencilOp, pass: StencilOp) =
        delegate.stencilOp(stencilFail, depthFail, pass)
    override fun stencilMask(writeMask: Int) = delegate.stencilMask(writeMask)
    override fun cull(enabled: Boolean) = delegate.cull(enabled)
    override fun scissor(x: Int, y: Int, width: Int, height: Int) = delegate.scissor(x, y, width, height)
    override fun disableScissor() = delegate.disableScissor()
    override fun clearColor(red: Float, green: Float, blue: Float, alpha: Float) =
        delegate.clearColor(red, green, blue, alpha)
    override fun clearDepth(depth: Double) = delegate.clearDepth(depth)
    override fun clearStencil(value: Int) = delegate.clearStencil(value)
    override fun pushDebugGroup(label: String) {
        delegate.pushDebugGroup(label)
        if (frameActive) gpuTimerTracker?.pushZone(label)
    }

    override fun popDebugGroup() {
        if (frameActive) gpuTimerTracker?.popZone()
        delegate.popDebugGroup()
    }

    override fun createTimestampPool(capacity: Int): GpuTimestampPool = delegate.createTimestampPool(capacity)

    override fun writeTimestamp(pool: GpuTimestampPool, queryIndex: Int) = delegate.writeTimestamp(pool, queryIndex)

    override fun invalidatePipelineCache() = delegate.invalidatePipelineCache()

    fun closeGpuTimers() {
        val tracker = gpuTimerTracker ?: return
        gpuTimerTracker = null
        gpuTimersChecked = false
        tracker.close()
    }

    override fun close() {
        var trackerFailure: Throwable? = null
        try {
            closeGpuTimers()
        } catch (throwable: Throwable) {
            trackerFailure = throwable
        }
        try {
            delegate.close()
        } catch (delegateFailure: Throwable) {
            val existing = trackerFailure
            if (existing != null) {
                existing.addSuppressed(delegateFailure)
                throw existing
            }
            throw delegateFailure
        }
        trackerFailure?.let { throw it }
    }

    override fun hasContext(): Boolean = delegate.hasContext()

    private fun ensureGpuTimerTracker(): GpuTimerTracker? {
        gpuTimerTracker?.let { return it }
        if (gpuTimersChecked) return null
        gpuTimersChecked = true
        if (!delegate.supportsGpuTimestamps) return null
        return GpuTimerTracker(delegate).also { gpuTimerTracker = it }
    }

    private fun countRenderTargetCreation() {
        countFrame(FrameStats::renderTargetCreated)
        lifetime.renderTargetsCreated++
    }

    private fun countFrame(action: FrameStats.() -> Unit) {
        if (frameActive) currentFrame.action()
    }

    private fun ensureTextureCapacity(required: Int) {
        if (required <= boundTextures.size) return
        boundTextures = boundTextures.copyOf(maxOf(required, boundTextures.size shl 1))
    }

    private fun ProgramHandle.unwrap(): ProgramHandle {
        if (this !is TrackedProgram) return this
        require(owner === this@StatsBackend) { "Program was not created by this StatsBackend" }
        return delegate
    }

    private fun ProgramHandle.tracked(): TrackedProgram =
        (this as? TrackedProgram)?.takeIf { it.owner === this@StatsBackend }
            ?: throw IllegalArgumentException("Program was not created by this StatsBackend")

    private fun primitiveCount(type: PrimitiveType, vertices: Long): Long = when (type) {
        PrimitiveType.TRIANGLES -> vertices / 3L
        PrimitiveType.LINES -> vertices / 2L
        PrimitiveType.QUADS -> vertices / 4L
    }

    private class TrackedProgram(
        val owner: StatsBackend,
        val delegate: ProgramHandle,
        val layout: VertexLayout?
    ) : ProgramHandle {
        override val vertexStrideBytes: Int get() = delegate.vertexStrideBytes
        override fun close() = delegate.close()
    }
}
