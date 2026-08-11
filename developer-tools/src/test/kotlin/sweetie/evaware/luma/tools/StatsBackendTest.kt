package sweetie.evaware.luma.tools

import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.api.RenderBackend
import sweetie.evaware.luma.api.RenderTargetFilter
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.ShaderVertType
import sweetie.evaware.luma.vertex.VertexLayout

class StatsBackendTest {
    @Test
    fun countsFrameOperationsWithoutReplacingSnapshots() {
        val delegate = FakeBackend()
        val backend = StatsBackend(delegate)
        val layout = VertexLayout().apply { add(ShaderVertType.FLOAT, 2, 0) }
        val program = backend.createProgram("", "", layout)
        val texture = backend.createTexture(BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB), false)
        val target = backend.createRenderTarget(
            32,
            32,
            false,
            RenderTargetFormat.RGBA8,
            RenderTargetFilter.LINEAR
        )
        val lastFrame = backend.lastFrame

        backend.beginFrame()
        backend.bindTexture(texture, 0)
        backend.bindTexture(texture, 0)
        backend.updateTexture(texture, 0, 0, BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB))
        backend.beginRenderTarget(target, null)
        backend.draw(program, FloatBuffer.allocate(12), 6, ShaderUniforms(), PrimitiveType.TRIANGLES)
        backend.draw(program, FloatBuffer.allocate(12), 6, ShaderUniforms(), PrimitiveType.TRIANGLES)
        backend.endRenderTarget()
        backend.endFrame()

        assertSame(lastFrame, backend.lastFrame)
        assertEquals(1L, lastFrame.frameIndex)
        assertEquals(2L, lastFrame.drawCalls)
        assertEquals(12L, lastFrame.vertices)
        assertEquals(4L, lastFrame.primitives)
        assertEquals(1L, lastFrame.programChanges)
        assertEquals(2L, lastFrame.textureBindCalls)
        assertEquals(1L, lastFrame.textureBindingChanges)
        assertEquals(1L, lastFrame.renderTargetPasses)
        assertEquals(1L, lastFrame.textureUploads)
        assertEquals(32L, lastFrame.uploadedBytes)
        assertEquals(RenderTargetFilter.LINEAR, delegate.lastFilter)

        backend.beginFrame()
        backend.endFrame()

        assertSame(lastFrame, backend.lastFrame)
        assertEquals(2L, lastFrame.frameIndex)
        assertEquals(0L, lastFrame.drawCalls)
        assertEquals(2L, backend.lifetime.drawCalls)
        assertEquals(1L, backend.lifetime.programsCreated)
        assertEquals(1L, backend.lifetime.texturesCreated)
        assertEquals(1L, backend.lifetime.renderTargetsCreated)
        assertEquals(2L, backend.lifetime.textureUploads)
        assertEquals(64L, backend.lifetime.uploadedBytes)
    }

    @Test
    fun countsLargeInstancedDraws() {
        val backend = StatsBackend(FakeBackend())
        val layout = VertexLayout().apply {
            add(ShaderVertType.FLOAT, 4, 0)
            markInstanced(6)
        }
        val program = backend.createProgram("", "", layout)

        backend.beginFrame()
        backend.draw(program, FloatBuffer.allocate(160_000), 40_000, ShaderUniforms(), PrimitiveType.TRIANGLES)
        backend.endFrame()

        assertEquals(1L, backend.lastFrame.drawCalls)
        assertEquals(40_000L, backend.lastFrame.instances)
        assertEquals(240_000L, backend.lastFrame.vertices)
        assertEquals(80_000L, backend.lastFrame.primitives)
    }

    @Test
    fun rejectsProgramsFromAnotherStatsBackend() {
        val first = StatsBackend(FakeBackend())
        val second = StatsBackend(FakeBackend())
        val layout = VertexLayout().apply { add(ShaderVertType.FLOAT, 2, 0) }
        val program = first.createProgram("", "", layout)

        assertFailsWith<IllegalArgumentException> {
            second.draw(program, FloatBuffer.allocate(6), 3, ShaderUniforms(), PrimitiveType.TRIANGLES)
        }
        assertFailsWith<IllegalArgumentException> {
            second.bindProgram(program)
        }
    }

    @Test
    fun tracksBindingsPerFrameAndGrowsTextureUnits() {
        val backend = StatsBackend(FakeBackend())
        val texture = backend.createTexture(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), false)

        backend.beginFrame()
        backend.bindTexture(texture, 24)
        backend.bindTexture(texture, 24)
        backend.endFrame()

        assertEquals(2L, backend.lastFrame.textureBindCalls)
        assertEquals(1L, backend.lastFrame.textureBindingChanges)

        backend.beginFrame()
        backend.bindTexture(texture, 24)
        backend.endFrame()

        assertEquals(1L, backend.lastFrame.textureBindCalls)
        assertEquals(1L, backend.lastFrame.textureBindingChanges)
    }

    @Test
    fun doesNotPublishFailedFrames() {
        val delegate = FakeBackend()
        val backend = StatsBackend(delegate)

        backend.beginFrame()
        delegate.failEndFrame = true
        assertFailsWith<IllegalStateException> { backend.endFrame() }

        assertEquals(0L, backend.lastFrame.frameIndex)
        assertEquals(0L, backend.lifetime.frames)

        delegate.failEndFrame = false
        backend.beginFrame()
        backend.endFrame()
        assertEquals(1L, backend.lastFrame.frameIndex)
    }

    @Test
    fun countsOnlySuccessfullyCreatedResources() {
        val delegate = FakeBackend().apply { failCreation = true }
        val backend = StatsBackend(delegate)
        val layout = VertexLayout().apply { add(ShaderVertType.FLOAT, 2, 0) }

        assertFailsWith<IllegalStateException> { backend.createProgram("", "", layout) }
        assertFailsWith<IllegalStateException> {
            backend.createTexture(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), false)
        }
        assertFailsWith<IllegalStateException> {
            backend.createRenderTarget(1, 1, false, RenderTargetFormat.RGBA8)
        }

        assertEquals(0L, backend.lifetime.programsCreated)
        assertEquals(0L, backend.lifetime.texturesCreated)
        assertEquals(0L, backend.lifetime.renderTargetsCreated)
    }

    private class FakeBackend : RenderBackend {
        var lastFilter: RenderTargetFilter? = null
        var failCreation = false
        var failEndFrame = false

        override fun beginFrame() {}
        override fun endFrame() {
            check(!failEndFrame)
        }
        override fun createProgram(
            vertexSource: String,
            fragmentSource: String,
            layout: VertexLayout
        ): ProgramHandle {
            check(!failCreation)
            return FakeProgram()
        }

        override fun bindProgram(program: ProgramHandle) {}
        override fun createTexture(image: BufferedImage, mipmap: Boolean): TextureHandle {
            check(!failCreation)
            return FakeTexture(image.width, image.height)
        }

        override fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage) {}
        override fun bindTexture(texture: TextureHandle, unit: Int) {}
        override fun draw(
            program: ProgramHandle,
            vertices: FloatBuffer,
            vertexCount: Int,
            uniforms: ShaderUniforms,
            primitiveType: PrimitiveType
        ) {}

        override fun createRenderTarget(
            width: Int,
            height: Int,
            useDepth: Boolean,
            format: RenderTargetFormat
        ): RenderTargetHandle {
            check(!failCreation)
            return FakeTarget(width, height)
        }

        override fun createRenderTarget(
            width: Int,
            height: Int,
            useDepth: Boolean,
            format: RenderTargetFormat,
            filter: RenderTargetFilter
        ): RenderTargetHandle {
            lastFilter = filter
            return FakeTarget(width, height)
        }

        override fun beginRenderTarget(target: RenderTargetHandle, clearColor: FloatArray?) {}
        override fun endRenderTarget() {}
        override fun close() {}
        override fun hasContext() = true
    }

    private class FakeProgram : ProgramHandle {
        override fun close() {}
    }

    private class FakeTexture(
        override val width: Int,
        override val height: Int
    ) : TextureHandle {
        override fun close() {}
    }

    private class FakeTarget(
        override val width: Int,
        override val height: Int
    ) : RenderTargetHandle {
        override val colorTexture = FakeTexture(width, height)
        override fun close() {}
    }
}
