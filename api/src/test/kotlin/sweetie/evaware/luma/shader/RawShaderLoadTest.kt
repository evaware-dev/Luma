package sweetie.evaware.luma.shader

import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.api.RenderBackend
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.shader.translator.RawShaderTranslator
import sweetie.evaware.luma.shader.translator.ShaderTranslator
import sweetie.evaware.luma.shader.translator.TranslationResult
import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.VertexLayout

class RawShaderLoadTest {
    private var originalBackend: RenderBackend? = null
    private lateinit var originalTranslator: ShaderTranslator

    @BeforeTest
    fun setUp() {
        originalBackend = runCatching { Luma.backend }.getOrNull()
        originalTranslator = Luma.shaderTranslator
    }

    @AfterTest
    fun tearDown() {
        originalBackend?.let { Luma.backend = it }
        Luma.shaderTranslator = originalTranslator
    }

    @Test
    fun loadsRawGlslWithoutTheTranslatorModule() {
        val backend = CapturingBackend()
        Luma.backend = backend
        Luma.shaderTranslator = RawShaderTranslator
        val shader = Shader("raw_shader.frag", "raw_shader.vert")
        shader.vertices.float(2, 0)

        shader.load()

        assertEquals(javaClass.classLoader.getResource("raw_shader.vert")!!.readText(), backend.vertexSource)
        assertEquals(javaClass.classLoader.getResource("raw_shader.frag")!!.readText(), backend.fragmentSource)
        shader.close()
    }

    @Test
    fun usesShaderSpecificTranslatorWithoutChangingGlobalState() {
        val backend = CapturingBackend()
        val globalTranslator = Luma.shaderTranslator
        val localTranslator = ShaderTranslator { vertex, fragment, _ ->
            TranslationResult("$vertex\n#define LOCAL_VERTEX", "$fragment\n#define LOCAL_FRAGMENT")
        }
        Luma.backend = backend
        val shader = Shader("raw_shader.frag", "raw_shader.vert")
            .translator(localTranslator)
        shader.vertices.float(2, 0)

        shader.load()

        kotlin.test.assertTrue(backend.vertexSource.endsWith("#define LOCAL_VERTEX"))
        kotlin.test.assertTrue(backend.fragmentSource.endsWith("#define LOCAL_FRAGMENT"))
        kotlin.test.assertSame(globalTranslator, Luma.shaderTranslator)
        shader.close()
    }

    @Test
    fun preparesVerticesOnAnotherThreadAndDrawsWithoutConsumingThem() {
        val backend = CapturingBackend()
        Luma.backend = backend
        Luma.shaderTranslator = RawShaderTranslator
        val shader = Shader("raw_shader.frag", "raw_shader.vert")
        shader.vertices.float2(0)
        val prepared = shader.prepareVertices(2)
        val executor = Executors.newSingleThreadExecutor()

        try {
            executor.submit {
                prepared.vec2(1f, 2f).vec2(3f, 4f).seal()
            }.get()
            shader.load()

            assertEquals(2, shader.draw(prepared))
            assertEquals(2, shader.draw(prepared))
            assertEquals(2, prepared.vertexCount)
            assertEquals(listOf(1f, 2f, 3f, 4f), backend.uploadedVertices)
            assertEquals(2, backend.drawCalls)
        } finally {
            executor.shutdownNow()
            prepared.close()
            shader.close()
        }
    }

    @Test
    fun preparedVerticesEnforceHandoffAndLayoutCompatibility() {
        val backend = CapturingBackend()
        Luma.backend = backend
        Luma.shaderTranslator = RawShaderTranslator
        val shader = Shader("raw_shader.frag", "raw_shader.vert")
        shader.vertices.float2(0)
        val incompatible = Shader("raw_shader.frag", "raw_shader.vert")
        incompatible.vertices.float3(0)
        val prepared = shader.prepareVertices().vec2(1f, 2f)

        try {
            assertFailsWith<IllegalStateException> { shader.draw(prepared) }
            prepared.seal()
            assertFailsWith<IllegalStateException> { prepared.vec2(3f, 4f) }
            incompatible.load()
            assertFailsWith<IllegalArgumentException> { incompatible.draw(prepared) }

            prepared.clear().vec2(3f, 4f).seal()
            assertTrue(prepared.isSealed)
            assertEquals(1, prepared.vertexCount)
        } finally {
            prepared.close()
            shader.close()
            incompatible.close()
        }
    }

    private class CapturingBackend : RenderBackend {
        var vertexSource = ""
        var fragmentSource = ""
        var drawCalls = 0
        var uploadedVertices = emptyList<Float>()

        override fun beginFrame() {}
        override fun endFrame() {}

        override fun createProgram(
            vertexSource: String,
            fragmentSource: String,
            layout: VertexLayout
        ): ProgramHandle {
            this.vertexSource = vertexSource
            this.fragmentSource = fragmentSource
            return object : ProgramHandle {
                override fun close() {}
            }
        }

        override fun bindProgram(program: ProgramHandle) {}
        override fun createTexture(image: BufferedImage, mipmap: Boolean): TextureHandle =
            unsupported<TextureHandle>()

        override fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage) {
            throw UnsupportedOperationException()
        }

        override fun bindTexture(texture: TextureHandle, unit: Int) {
            throw UnsupportedOperationException()
        }
        override fun draw(
            program: ProgramHandle,
            vertices: FloatBuffer,
            vertexCount: Int,
            uniforms: ShaderUniforms,
            primitiveType: PrimitiveType
        ) {
            drawCalls++
            val copy = vertices.duplicate()
            uploadedVertices = List(copy.remaining()) { copy.get() }
        }

        override fun createRenderTarget(
            width: Int,
            height: Int,
            useDepth: Boolean,
            format: RenderTargetFormat
        ): RenderTargetHandle = unsupported<RenderTargetHandle>()

        override fun beginRenderTarget(target: RenderTargetHandle, clearColor: FloatArray?) {
            throw UnsupportedOperationException()
        }

        override fun endRenderTarget() {
            throw UnsupportedOperationException()
        }
        override fun close() {}
        override fun hasContext() = true

        private fun <T> unsupported(): T = throw UnsupportedOperationException()
    }
}
