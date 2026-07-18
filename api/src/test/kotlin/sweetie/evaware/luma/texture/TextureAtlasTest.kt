package sweetie.evaware.luma.texture

import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
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
    private lateinit var atlas: TextureAtlas

    private class MockTextureHandle(
        override val width: Int,
        override val height: Int
    ) : TextureHandle {
        override fun close() {}
    }

    private class MockBackend : RenderBackend {
        var lastCreatedImage: BufferedImage? = null
        var createdTextures = 0
        var textureUpdates = 0
        override fun beginFrame() {}
        override fun endFrame() {}
        override fun createProgram(vertexSource: String, fragmentSource: String, layout: VertexLayout): ProgramHandle = error("mock")
        override fun bindProgram(program: ProgramHandle) {}
        override fun createTexture(image: BufferedImage, mipmap: Boolean): TextureHandle {
            lastCreatedImage = image
            createdTextures++
            return MockTextureHandle(image.width, image.height)
        }
        override fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage) {
            textureUpdates++
        }
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
        TextureAtlasManager.close()
        atlas = TextureAtlasManager.create("test:main")
        try {
            originalBackend = Luma.backend
        } catch (_: Throwable) {}
    }

    @AfterTest
    fun tearDown() {
        TextureAtlasManager.close()
        originalBackend?.let { Luma.backend = it }
    }

    @Test
    fun testAtlasPreparation() {
        val mock = MockBackend()
        Luma.backend = mock

        atlas.register("t1") {
            BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        }
        atlas.register("t2") {
            BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        }

        atlas.prepare()

        val atlasImage = mock.lastCreatedImage
        assertTrue(atlasImage != null)
        assertTrue(atlasImage.width >= 16)
        assertTrue(atlasImage.height >= 16)

        val r1 = atlas.region("t1")
        val r2 = atlas.region("t2")

        assertTrue(r1.uScale > 0f)
        assertTrue(r2.uScale > 0f)
    }

    @Test
    fun testNamedAtlasesAreIndependent() {
        val mock = MockBackend()
        Luma.backend = mock
        val secondary = TextureAtlasManager.create("test:secondary")

        atlas.register("main") { BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB) }
        secondary.register("secondary") { BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB) }
        atlas.prepare()
        secondary.prepare()

        assertSame(atlas, TextureAtlasManager["test:main"])
        assertSame(secondary, TextureAtlasManager["test:secondary"])
        assertEquals(setOf("test:main", "test:secondary"), TextureAtlasManager.ids())
        assertFailsWith<IllegalStateException> { atlas.region("secondary") }
        assertFailsWith<IllegalStateException> { secondary.region("main") }
    }

    @Test
    fun testRemovedAtlasDoesNotAffectOthers() {
        val secondary = TextureAtlasManager.create("test:secondary")

        assertSame(atlas, TextureAtlasManager.remove("test:main", close = false))
        assertEquals(null, TextureAtlasManager.find("test:main"))
        assertSame(secondary, TextureAtlasManager["test:secondary"])
    }

    @Test
    fun testDynamicGrowthAndIncrementalUpload() {
        TextureAtlasManager.remove("test:main")
        val dynamic = TextureAtlasManager.create(
            "test:dynamic",
            TextureAtlasConfig(
                initialSize = TextureAtlasSize(8, 8),
                maximumSize = TextureAtlasSize(64, 64),
                padding = 0
            )
        )
        val mock = MockBackend()
        Luma.backend = mock

        val original = dynamic.put("original", BufferedImage(7, 7, BufferedImage.TYPE_INT_ARGB))
        dynamic.prepare()
        val originalScale = original.uScale

        val growing = dynamic.put("growing", BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB))
        assertTrue(!growing.isReady)
        dynamic.processPending()

        assertTrue(growing.isReady)
        assertTrue(dynamic.width > 8 || dynamic.height > 8)
        assertTrue(original.uScale < originalScale)
        assertEquals(2, mock.createdTextures)

        val incremental = dynamic.put("incremental", BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))
        dynamic.processPending()

        assertTrue(incremental.isReady)
        assertEquals(2, mock.createdTextures)
        assertEquals(1, mock.textureUpdates)
    }
}
