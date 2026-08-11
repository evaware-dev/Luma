package sweetie.evaware.luma

import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.api.RenderBackend
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.texture.TextureAtlasManager
import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.VertexLayout

class LumaLifecycleTest {
    private var originalBackend: RenderBackend? = null
    private var originalPlatform: RenderPlatform? = null

    @BeforeTest
    fun setUp() {
        TextureAtlasManager.close()
        originalBackend = runCatching { Luma.backend }.getOrNull()
        originalPlatform = Luma.platform
        Luma.platform = DefaultRenderPlatform
        Luma.frameActive = false
    }

    @AfterTest
    fun tearDown() {
        TextureAtlasManager.close()
        originalBackend?.let { Luma.backend = it }
        originalPlatform?.let { Luma.platform = it }
        Luma.frameActive = false
    }

    @Test
    fun renderPreservesActionFailureWhenEndFrameAlsoFails() {
        val actionFailure = IllegalStateException("action")
        val endFailure = IllegalArgumentException("end")
        val backend = FailingBackend(endFrameFailure = endFailure)
        Luma.backend = backend

        val thrown = kotlin.test.assertFailsWith<IllegalStateException> {
            Luma.render { throw actionFailure }
        }

        assertSame(actionFailure, thrown)
        assertEquals(listOf(endFailure), thrown.suppressed.toList())
        assertEquals(1, backend.beginCalls)
        assertEquals(1, backend.endCalls)
        assertFalse(Luma.frameActive)
    }

    @Test
    fun beginMainFramebufferFramePreservesPendingFailureWhenEndFrameAlsoFails() {
        val pendingFailure = IllegalStateException("pending")
        val endFailure = IllegalArgumentException("end")
        val backend = FailingBackend(endFrameFailure = endFailure)
        val atlas = TextureAtlasManager.create("test:lifecycle")
        Luma.backend = backend

        atlas.put("ready", BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))
        atlas.prepare()
        atlas.register("broken") { throw pendingFailure }

        val thrown = kotlin.test.assertFailsWith<IllegalStateException> {
            Luma.beginMainFramebufferFrame()
        }

        assertSame(pendingFailure, thrown)
        assertEquals(listOf(endFailure), thrown.suppressed.toList())
        assertEquals(1, backend.beginCalls)
        assertEquals(1, backend.endCalls)
        assertFalse(Luma.frameActive)
        assertTrue(atlas.isPrepared)
    }

    private class FailingBackend(
        private val endFrameFailure: Throwable
    ) : RenderBackend {
        var beginCalls = 0
        var endCalls = 0

        override fun beginFrame() {
            beginCalls++
        }

        override fun endFrame() {
            endCalls++
            throw endFrameFailure
        }

        override fun createProgram(
            vertexSource: String,
            fragmentSource: String,
            layout: VertexLayout
        ): ProgramHandle = unsupported()

        override fun bindProgram(program: ProgramHandle) = unsupported<Unit>()

        override fun createTexture(image: BufferedImage, mipmap: Boolean): TextureHandle =
            object : TextureHandle {
                override val width = image.width
                override val height = image.height
                override fun close() {}
            }

        override fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage) = unsupported<Unit>()

        override fun bindTexture(texture: TextureHandle, unit: Int) = unsupported<Unit>()

        override fun draw(
            program: ProgramHandle,
            vertices: FloatBuffer,
            vertexCount: Int,
            uniforms: ShaderUniforms,
            primitiveType: PrimitiveType
        ) = unsupported<Unit>()

        override fun createRenderTarget(
            width: Int,
            height: Int,
            useDepth: Boolean,
            format: RenderTargetFormat
        ): RenderTargetHandle = unsupported()

        override fun beginRenderTarget(target: RenderTargetHandle, clearColor: FloatArray?) = unsupported<Unit>()

        override fun endRenderTarget() = unsupported<Unit>()

        override fun close() {}

        override fun hasContext() = true

        private fun <T> unsupported(): T = throw UnsupportedOperationException()
    }
}
