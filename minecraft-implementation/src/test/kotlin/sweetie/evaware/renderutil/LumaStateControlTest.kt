package sweetie.evaware.renderutil

import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.RenderPlatform
import sweetie.evaware.luma.DefaultRenderPlatform
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.RenderBackend
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.VertexLayout
import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LumaStateControlTest {

    private class MockPlatform : RenderPlatform {
        var blendState = false
        var cullState = false
        var depthState = false

        override fun getGuiScaledWidth(): Float = 960f
        override fun getGuiScaledHeight(): Float = 540f
        override fun getGuiScale(): Float = 1f
        override fun getWindowHeight(): Float = 540f
        override fun getViewport(viewport: IntArray): Boolean = true
        override fun swapToMainFramebuffer(luma: Luma) {}

        override fun enableBlend() { blendState = true }
        override fun disableBlend() { blendState = false }
        override fun enableDepthTest() { depthState = true }
        override fun disableDepthTest() { depthState = false }
        override fun enableCull() { cullState = true }
        override fun disableCull() { cullState = false }
    }

    private class MockBackend : RenderBackend {
        var framesBegun = 0
        var framesEnded = 0
        var depthState = false
        var cullState = false

        override fun beginFrame() { framesBegun++ }
        override fun endFrame() { framesEnded++ }
        override fun createProgram(vertexSource: String, fragmentSource: String, layout: VertexLayout): ProgramHandle = error("mock")
        override fun bindProgram(program: ProgramHandle) {}
        override fun createTexture(image: BufferedImage, mipmap: Boolean): TextureHandle = error("mock")
        override fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage) {}
        override fun bindTexture(texture: TextureHandle, unit: Int) {}
        override fun draw(program: ProgramHandle, vertices: FloatBuffer, vertexCount: Int, uniforms: ShaderUniforms, primitiveType: PrimitiveType) {}
        override fun createRenderTarget(width: Int, height: Int, useDepth: Boolean, format: RenderTargetFormat): RenderTargetHandle = error("mock")
        override fun beginRenderTarget(target: RenderTargetHandle, clearColor: FloatArray?) {}
        override fun endRenderTarget() {}
        override fun depthTest(enabled: Boolean) { depthState = enabled }
        override fun cull(enabled: Boolean) { cullState = enabled }
        override fun close() {}
        override fun hasContext(): Boolean = true
    }

    private var originalPlatform: RenderPlatform? = null
    private var originalBackend: RenderBackend? = null

    @BeforeTest
    fun setup() {
        originalPlatform = Luma.platform
        originalBackend = try { Luma.backend } catch (e: Exception) { null }
    }

    @AfterTest
    fun tearDown() {
        Luma.frameActive = false
        originalPlatform?.let { Luma.platform = it }
        originalBackend?.let { Luma.backend = it }
    }

    @Test
    fun `test luma state delegation functions`() {
        val platform = MockPlatform()
        val backend = MockBackend()
        Luma.platform = platform
        Luma.backend = backend

        Luma.enableBlend()
        assertTrue(platform.blendState)

        Luma.disableBlend()
        assertEquals(false, platform.blendState)

        Luma.enableDepthTest()
        assertTrue(platform.depthState)
        assertTrue(backend.depthState)

        Luma.disableDepthTest()
        assertEquals(false, platform.depthState)
        assertEquals(false, backend.depthState)

        Luma.enableCull()
        assertTrue(platform.cullState)
        assertTrue(backend.cullState)

        Luma.disableCull()
        assertEquals(false, platform.cullState)
        assertEquals(false, backend.cullState)
    }

    @Test
    fun `test luma render block context execution`() {
        val backend = MockBackend()
        Luma.backend = backend

        var actionExecuted = false
        Luma.render {
            actionExecuted = true
        }

        assertEquals(1, backend.framesBegun)
        assertEquals(1, backend.framesEnded)
        assertTrue(actionExecuted)
    }

    @Test
    fun `nested render shares one backend frame`() {
        val backend = MockBackend()
        Luma.backend = backend

        Luma.render {
            assertTrue(Luma.frameActive)
            Luma.render { assertTrue(Luma.frameActive) }
        }

        assertEquals(1, backend.framesBegun)
        assertEquals(1, backend.framesEnded)
        assertEquals(false, Luma.frameActive)
    }
}
