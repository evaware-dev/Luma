package sweetie.evaware.luma.shader

import java.awt.image.BufferedImage
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.api.RenderBackend
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.SamplerState
import sweetie.evaware.luma.api.TextureFormat
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.shader.translator.RawShaderTranslator
import sweetie.evaware.luma.shader.translator.ShaderReflection
import sweetie.evaware.luma.shader.translator.ShaderTranslator
import sweetie.evaware.luma.shader.translator.TranslationResult
import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.VertexCursor
import sweetie.evaware.luma.vertex.VertexInputLayout

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

        try {
            shader.load()

            assertEquals(javaClass.classLoader.getResource("raw_shader.vert")!!.readText(), backend.vertexSource)
            assertEquals(javaClass.classLoader.getResource("raw_shader.frag")!!.readText(), backend.fragmentSource)
        } finally {
            shader.close()
            backend.close()
        }
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

        try {
            shader.load()

            assertTrue(backend.vertexSource.endsWith("#define LOCAL_VERTEX"))
            assertTrue(backend.fragmentSource.endsWith("#define LOCAL_FRAGMENT"))
            assertSame(globalTranslator, Luma.shaderTranslator)
        } finally {
            shader.close()
            backend.close()
        }
    }

    private class CapturingBackend : RenderBackend {
        private val scratch = MemoryUtil.memAllocFloat(64)
        private val scratchAddr = MemoryUtil.memAddress(scratch)
        var vertexSource = ""
        var fragmentSource = ""
        var drawCalls = 0
        var uploadedVertices = emptyList<Float>()

        override fun beginFrame() {}
        override fun endFrame() {}

        override fun createProgram(
            vertexSource: String,
            fragmentSource: String,
            layouts: VertexInputLayout,
            reflection: ShaderReflection?
        ): ProgramHandle {
            this.vertexSource = vertexSource
            this.fragmentSource = fragmentSource
            val stride = layouts.layout(0).strideBytes
            return object : ProgramHandle {
                override val vertexStrideBytes: Int = stride
                override fun close() {}
            }
        }

        override fun bindProgram(program: ProgramHandle) {}
        override fun createTexture(
            image: BufferedImage,
            mipmap: Boolean,
            format: TextureFormat,
            sampler: SamplerState
        ): TextureHandle = unsupported<TextureHandle>()

        override fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage) {
            throw UnsupportedOperationException()
        }

        override fun bindTexture(texture: TextureHandle, unit: Int) {
            throw UnsupportedOperationException()
        }

        override fun beginVertices(program: ProgramHandle, vertexCount: Int, cursor: VertexCursor) {
            val stride = program.vertexStrideBytes
            cursor.init(scratchAddr, vertexCount.toLong() * stride, stride)
        }

        override fun endVertices(
            program: ProgramHandle,
            cursor: VertexCursor,
            vertexCount: Int,
            uniforms: ShaderUniforms,
            primitiveType: PrimitiveType
        ) {
            if (vertexCount <= 0) return
            drawCalls++
            val floats = vertexCount * (program.vertexStrideBytes / Float.SIZE_BYTES)
            uploadedVertices = List(floats) { scratch.get(it) }
        }

        override fun createRenderTarget(
            width: Int,
            height: Int,
            useDepth: Boolean,
            format: RenderTargetFormat,
            sampler: SamplerState,
            useStencil: Boolean
        ): RenderTargetHandle = unsupported<RenderTargetHandle>()

        override fun beginRenderTarget(target: RenderTargetHandle, clearColor: FloatArray?) {
            throw UnsupportedOperationException()
        }

        override fun endRenderTarget() {
            throw UnsupportedOperationException()
        }

        override fun close() {
            MemoryUtil.memFree(scratch)
        }

        override fun hasContext() = true

        private fun <T> unsupported(): T = throw UnsupportedOperationException()
    }
}
