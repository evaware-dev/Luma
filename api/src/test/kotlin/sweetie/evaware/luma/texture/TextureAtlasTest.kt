package sweetie.evaware.luma.texture

import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.RenderBackend
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.VertexLayout

class TextureAtlasTest {
    private var originalBackend: RenderBackend? = null

    private class MockTextureHandle(
        override val width: Int,
        override val height: Int
    ) : TextureHandle {
        override fun close() {}
    }

    private class MockBackend : RenderBackend {
        var lastCreatedImage: BufferedImage? = null
        override fun beginFrame() {}
        override fun endFrame() {}
        override fun createProgram(vertexSource: String, fragmentSource: String, layout: VertexLayout): ProgramHandle = error("mock")
        override fun bindProgram(program: ProgramHandle) {}
        override fun createTexture(image: BufferedImage, mipmap: Boolean): TextureHandle {
            lastCreatedImage = image
            return MockTextureHandle(image.width, image.height)
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
        ): RenderTargetHandle = error("mock")
        override fun beginRenderTarget(target: RenderTargetHandle, clearColor: FloatArray?) {}
        override fun endRenderTarget() {}
        override fun close() {}
        override fun hasContext() = true
    }

    @BeforeTest
    fun setUp() {
        try {
            originalBackend = Luma.backend
        } catch (_: Throwable) {}
    }

    @AfterTest
    fun tearDown() {
        originalBackend?.let { Luma.backend = it }
        TextureAtlas.close()
    }

    @Test
    fun testAtlasPreparation() {
        val mock = MockBackend()
        Luma.backend = mock

        TextureAtlas.register("t1") {
            BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        }
        TextureAtlas.register("t2") {
            BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        }

        TextureAtlas.prepare()

        val atlas = mock.lastCreatedImage
        assertTrue(atlas != null)
        assertTrue(atlas.width >= 16)
        assertTrue(atlas.height >= 16)

        val r1 = TextureAtlas.region("t1")
        val r2 = TextureAtlas.region("t2")

        assertTrue(r1.uScale > 0f)
        assertTrue(r2.uScale > 0f)
    }
}
