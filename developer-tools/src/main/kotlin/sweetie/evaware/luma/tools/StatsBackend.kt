package sweetie.evaware.luma.tools

import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.api.RenderBackend
import sweetie.evaware.luma.api.RenderTargetFilter
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.VertexLayout

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

    override fun draw(
        program: ProgramHandle,
        vertices: FloatBuffer,
        vertexCount: Int,
        uniforms: ShaderUniforms,
        primitiveType: PrimitiveType
    ) {
        val tracked = (program as? TrackedProgram)?.takeIf { it.owner === this }
            ?: throw IllegalArgumentException("Program was not created by this StatsBackend")
        val layout = tracked.layout
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

    override fun depthTest(enabled: Boolean) = delegate.depthTest(enabled)

    override fun cull(enabled: Boolean) = delegate.cull(enabled)

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

    private fun primitiveCount(type: PrimitiveType, vertices: Long): Long = when (type) {
        PrimitiveType.TRIANGLES -> vertices / 3L
        PrimitiveType.LINES -> vertices / 2L
        PrimitiveType.QUADS -> vertices / 4L
    }

    private class TrackedProgram(
        val owner: StatsBackend,
        val delegate: ProgramHandle,
        val layout: VertexLayout
    ) : ProgramHandle {
        override fun close() = delegate.close()
    }
}
