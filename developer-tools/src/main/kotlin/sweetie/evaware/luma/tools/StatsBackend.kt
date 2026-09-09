package sweetie.evaware.luma.tools

import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.FloatBuffer
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
import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.VertexLayout
import sweetie.evaware.luma.vertex.VertexInputLayout

class StatsBackend(
    val delegate: RenderBackend
) : RenderBackend {
    val currentFrame = FrameStats()
    val lastFrame = FrameStats()
    val lifetime = LifetimeStats()

    private var boundTextures = arrayOfNulls<TextureHandle>(16)
    private var lastDrawProgram: TrackedProgram? = null
    private var frameStartNanos = 0L
    private var frameActive = false

    override fun beginFrame() {
        check(!frameActive) { "Frame is already active" }
        val index = lifetime.frames + 1L
        currentFrame.reset(index)
        lastDrawProgram = null
        boundTextures.fill(null)
        frameStartNanos = System.nanoTime()
        delegate.beginFrame()
        frameActive = true
    }

    override fun endFrame() {
        check(frameActive) { "No active frame" }
        try {
            delegate.endFrame()
            currentFrame.finish(System.nanoTime() - frameStartNanos)
            lastFrame.copyFrom(currentFrame)
            lifetime.frames++
        } finally {
            frameActive = false
        }
    }

    override fun createProgram(
        vertexSource: String,
        fragmentSource: String,
        layout: VertexLayout
    ): ProgramHandle {
        val program = delegate.createProgram(vertexSource, fragmentSource, layout)
        countFrame(FrameStats::programCreated)
        lifetime.programsCreated++
        return TrackedProgram(this, program, layout)
    }

    override fun createProgram(
        vertexSource: String,
        fragmentSource: String,
        layouts: VertexInputLayout
    ): ProgramHandle {
        val program = delegate.createProgram(vertexSource, fragmentSource, layouts)
        countFrame(FrameStats::programCreated)
        lifetime.programsCreated++
        return TrackedProgram(this, program, null)
    }

    override fun bindProgram(program: ProgramHandle) {
        delegate.bindProgram(program.unwrap())
    }

    override fun createTexture(image: BufferedImage, mipmap: Boolean): TextureHandle {
        val texture = delegate.createTexture(image, mipmap)
        val bytes = image.width.toLong() * image.height.toLong() * Integer.BYTES
        countFrame(FrameStats::textureCreated)
        if (frameActive) currentFrame.textureUpload(bytes)
        lifetime.texturesCreated++
        lifetime.textureUploads++
        lifetime.uploadedBytes += bytes
        return texture
    }

    override fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage) {
        val bytes = image.width.toLong() * image.height.toLong() * Integer.BYTES
        delegate.updateTexture(texture, x, y, image)
        if (frameActive) currentFrame.textureUpload(bytes)
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

    override fun draw(
        program: ProgramHandle,
        vertices: FloatBuffer,
        vertexCount: Int,
        uniforms: ShaderUniforms,
        primitiveType: PrimitiveType
    ) {
        val tracked = (program as? TrackedProgram)?.takeIf { it.owner === this }
            ?: throw IllegalArgumentException("Program was not created by this StatsBackend")
        val layout = requireNotNull(tracked.layout) { "Streaming draw requires a single vertex layout" }
        val instances = if (layout.instanced) vertexCount.toLong() else 0L
        val submittedVertices = if (layout.instanced) {
            layout.baseVertexCount.toLong() * vertexCount.toLong()
        } else {
            vertexCount.toLong()
        }
        val primitives = primitiveCount(primitiveType, submittedVertices)
        delegate.draw(tracked.delegate, vertices, vertexCount, uniforms, primitiveType)

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
        format: RenderTargetFormat
    ): RenderTargetHandle {
        val target = delegate.createRenderTarget(width, height, useDepth, format)
        countRenderTargetCreation()
        return target
    }

    override fun createRenderTarget(
        width: Int,
        height: Int,
        useDepth: Boolean,
        format: RenderTargetFormat,
        filter: RenderTargetFilter
    ): RenderTargetHandle {
        val target = delegate.createRenderTarget(width, height, useDepth, format, filter)
        countRenderTargetCreation()
        return target
    }

    override fun beginRenderTarget(target: RenderTargetHandle, clearColor: FloatArray?) {
        delegate.beginRenderTarget(target, clearColor)
        if (frameActive) currentFrame.renderTargetPass()
    }

    override fun endRenderTarget() = delegate.endRenderTarget()

    override fun blend(enabled: Boolean) = delegate.blend(enabled)
    override fun blendFunction(function: BlendFunction) = delegate.blendFunction(function)

    override fun depthTest(enabled: Boolean) = delegate.depthTest(enabled)

    override fun depthWrite(enabled: Boolean) = delegate.depthWrite(enabled)

    override fun depthCompare(compare: DepthCompare) = delegate.depthCompare(compare)

    override fun cull(enabled: Boolean) = delegate.cull(enabled)

    override fun scissor(x: Int, y: Int, width: Int, height: Int) = delegate.scissor(x, y, width, height)

    override fun disableScissor() = delegate.disableScissor()

    override fun clearColor(red: Float, green: Float, blue: Float, alpha: Float) =
        delegate.clearColor(red, green, blue, alpha)

    override fun clearDepth(depth: Double) = delegate.clearDepth(depth)

    override fun close() = delegate.close()

    override fun hasContext(): Boolean = delegate.hasContext()

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
        override fun close() = delegate.close()
    }
}
