package sweetie.evaware.luma.vertex

import java.awt.image.BufferedImage
import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.*
import sweetie.evaware.luma.list.DrawList
import sweetie.evaware.luma.shader.Shader
import sweetie.evaware.luma.shader.translator.RawShaderTranslator
import sweetie.evaware.luma.shader.translator.ShaderReflection
import sweetie.evaware.luma.uniform.ShaderUniforms
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VertexCursorTest {
    private var previousBackend: RenderBackend? = null

    @BeforeTest
    fun saveBackend() {
        previousBackend = if (Luma.isBackendInitialized) Luma.backend else null
        Luma.shaderTranslator = RawShaderTranslator
    }

    @AfterTest
    fun restoreBackend() {
        previousBackend?.let { Luma.backend = it }
    }

    @Test
    fun `shader draw writes vertices zero-copy via receiver cursor`() {
        val backend = DirectCapturingBackend()
        Luma.backend = backend
        val shader = createShader()
        try {
            val written = shader.draw(3) {
                vec2(1f, 2f)
                vec2(3f, 4f)
                vec2(5f, 6f)
            }
            assertEquals(3, written)
            assertEquals(1, backend.commits)
            assertEquals(3, backend.lastVertexCount)
            assertContentEquals(floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f), backend.capturedFloats())
        } finally {
            shader.close()
            backend.close()
        }
    }

    @Test
    fun `shader draw commits exact vertex count when fewer than reserved are written`() {
        val backend = DirectCapturingBackend()
        Luma.backend = backend
        val shader = createShader()
        try {
            val written = shader.draw(6) {
                vec2(10f, 20f)
                vec2(30f, 40f)
            }
            assertEquals(2, written)
            assertEquals(1, backend.commits)
            assertEquals(2, backend.lastVertexCount)
            assertContentEquals(floatArrayOf(10f, 20f, 30f, 40f), backend.capturedFloats())
        } finally {
            shader.close()
            backend.close()
        }
    }

    @Test
    fun `shader draw supports vec1 vec2 vec3 vec4 mixed attributes`() {
        val backend = DirectCapturingBackend()
        Luma.backend = backend
        val shader = Shader("raw_shader.frag", "raw_shader.vert")
        shader.vertices.float1(0).float3(1).float4(2)
        shader.load()
        try {
            val written = shader.draw(1) {
                vec1(1f)
                vec3(2f, 3f, 4f)
                vec4(5f, 6f, 7f, 8f)
            }
            assertEquals(1, written)
            assertContentEquals(floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f), backend.capturedFloats())
        } finally {
            shader.close()
            backend.close()
        }
    }

    @Test
    fun `shader draw returns zero for non-positive vertexCount without touching backend`() {
        val backend = DirectCapturingBackend()
        Luma.backend = backend
        val shader = createShader()
        try {
            assertEquals(0, shader.draw(0) { vec2(1f, 2f) })
            assertEquals(0, shader.draw(-3) { vec2(1f, 2f) })
            assertEquals(0, backend.commits)
            assertEquals(0, backend.aborts)
        } finally {
            shader.close()
            backend.close()
        }
    }

    @Test
    fun `shader draw rejects overflow and aborts reservation cleanly`() {
        val backend = DirectCapturingBackend()
        Luma.backend = backend
        val shader = createShader()
        try {
            assertFailsWith<IllegalStateException> {
                shader.draw(2) {
                    vec2(1f, 2f)
                    vec2(3f, 4f)
                    vec2(5f, 6f)
                }
            }
            assertEquals(0, backend.commits)
            assertEquals(1, backend.aborts)
        } finally {
            shader.close()
            backend.close()
        }
    }

    @Test
    fun `shader draw rejects incomplete vertex and aborts reservation`() {
        val backend = DirectCapturingBackend()
        Luma.backend = backend
        val shader = createShader()
        try {
            assertFailsWith<IllegalStateException> {
                shader.draw(2) {
                    vec2(1f, 2f)
                    vec1(3f)
                }
            }
            assertEquals(0, backend.commits)
            assertEquals(1, backend.aborts)
        } finally {
            shader.close()
            backend.close()
        }
    }

    @Test
    fun `shader draw aborts reservation when callback throws`() {
        val backend = DirectCapturingBackend()
        Luma.backend = backend
        val shader = createShader()
        try {
            assertFailsWith<RuntimeException> {
                shader.draw(3) {
                    vec2(1f, 2f)
                    throw RuntimeException("boom")
                }
            }
            assertEquals(0, backend.commits)
            assertEquals(1, backend.aborts)
        } finally {
            shader.close()
            backend.close()
        }
    }

    @Test
    fun `shader draw works seamlessly inside DrawList record and replay`() {
        val backend = DirectCapturingBackend()
        Luma.backend = backend
        val shader = createShader()
        val list = DrawList()
        try {
            list.record(backend) {
                shader.attach()
                shader.draw(3) {
                    vec2(1f, 2f)
                    vec2(3f, 4f)
                    vec2(5f, 6f)
                }
            }
            assertTrue(!list.isEmpty)
            list.replay(backend)
            assertEquals(1, backend.commits)
            assertEquals(3, backend.lastVertexCount)
            assertContentEquals(floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f), backend.capturedFloats())
        } finally {
            list.close()
            shader.close()
            backend.close()
        }
    }

    private fun createShader(): Shader {
        val shader = Shader("raw_shader.frag", "raw_shader.vert")
        shader.vertices.float2(0)
        shader.load()
        return shader
    }

    private class DummyProgram(override val vertexStrideBytes: Int) : ProgramHandle {
        override fun close() {}
    }

    private class DirectCapturingBackend : RenderBackend {
        private val nativeBuffer = MemoryUtil.memAllocFloat(256)
        private val nativeAddress = MemoryUtil.memAddress(nativeBuffer)
        private var capturedCount = 0
        var commits = 0
        var aborts = 0
        var lastVertexCount = 0

        fun capturedFloats(): FloatArray = FloatArray(capturedCount) { nativeBuffer.get(it) }

        override fun beginFrame() {}
        override fun endFrame() {}
        override fun hasContext(): Boolean = true
        override fun createProgram(
            vertexSource: String,
            fragmentSource: String,
            layouts: VertexInputLayout,
            reflection: ShaderReflection?
        ): ProgramHandle = DummyProgram(layouts.layout(0).strideBytes)

        override fun bindProgram(program: ProgramHandle) {}
        override fun createTexture(image: BufferedImage, mipmap: Boolean, format: TextureFormat, sampler: SamplerState): TextureHandle =
            error("unused")
        override fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage) = error("unused")
        override fun bindTexture(texture: TextureHandle, unit: Int) {}
        override fun createRenderTarget(
            width: Int,
            height: Int,
            useDepth: Boolean,
            format: RenderTargetFormat,
            sampler: SamplerState,
            useStencil: Boolean
        ): RenderTargetHandle = error("unused")
        override fun beginRenderTarget(target: RenderTargetHandle, clearColor: FloatArray?) {}
        override fun endRenderTarget() {}

        override fun beginVertices(program: ProgramHandle, vertexCount: Int, cursor: VertexCursor) {
            val stride = program.vertexStrideBytes
            cursor.init(nativeAddress, vertexCount.toLong() * stride, stride, token = 42L)
        }

        override fun endVertices(
            program: ProgramHandle,
            cursor: VertexCursor,
            vertexCount: Int,
            uniforms: ShaderUniforms,
            primitiveType: PrimitiveType
        ) {
            assertEquals(42L, cursor.token)
            if (vertexCount <= 0) {
                aborts++
                return
            }
            commits++
            lastVertexCount = vertexCount
            capturedCount = vertexCount * (program.vertexStrideBytes / Float.SIZE_BYTES)
        }

        override fun close() {
            MemoryUtil.memFree(nativeBuffer)
        }
    }
}
