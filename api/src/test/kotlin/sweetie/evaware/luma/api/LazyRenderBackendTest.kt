package sweetie.evaware.luma.api

import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.shader.translator.ShaderReflection
import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.VertexInputLayout

class LazyRenderBackendTest {
    private var originalBackend: RenderBackend? = null

    @BeforeTest
    fun setUp() {
        originalBackend = if (Luma.isBackendInitialized) Luma.backend else null
    }

    @AfterTest
    fun tearDown() {
        originalBackend?.let { Luma.backend = it }
    }

    @Test
    fun nonOperationalCallsDoNotInitializeFactory() {
        var creations = 0
        val lazy = LazyRenderBackend {
            creations++
            RecordingBackend()
        }
        Luma.backend = lazy

        assertFalse(lazy.isInitialized)
        assertFalse(lazy.hasContext())
        lazy.invalidatePipelineCache()
        lazy.close()

        assertEquals(0, creations)
        assertFalse(lazy.isInitialized)
        assertSame(lazy, Luma.backend)
    }

    @Test
    fun firstOperationalCallResolvesOnceAndSwapsLumaBackend() {
        var creations = 0
        val concrete = RecordingBackend()
        val lazy = LazyRenderBackend {
            creations++
            concrete
        }
        Luma.backend = lazy

        lazy.beginFrame()

        assertEquals(1, creations)
        assertTrue(lazy.isInitialized)
        assertSame(concrete, Luma.backend)
        assertEquals(1, concrete.beginFrameCalls)
        assertTrue(lazy.hasContext())

        // Calling through the cached lazy reference still reuses the same instance
        lazy.endFrame()
        lazy.invalidatePipelineCache()
        lazy.close()

        assertEquals(1, creations)
        assertEquals(1, concrete.endFrameCalls)
        assertEquals(1, concrete.invalidateCalls)
        assertEquals(1, concrete.closeCalls)
    }

    @Test
    fun resolvingDoesNotOverwriteExternallyReplacedLumaBackend() {
        val other = RecordingBackend()
        val concrete = RecordingBackend()
        val lazy = LazyRenderBackend { concrete }
        Luma.backend = other

        lazy.beginFrame()

        assertSame(other, Luma.backend)
        assertEquals(1, concrete.beginFrameCalls)
        assertEquals(0, other.beginFrameCalls)
    }

    @Test
    fun forwardsDebugGroupsThroughLazyBackendAndLumaHelper() {
        val concrete = RecordingBackend()
        val lazy = LazyRenderBackend { concrete }
        Luma.backend = lazy

        val value = Luma.debugGroup("PassA") {
            42
        }

        assertEquals(42, value)
        assertEquals(listOf("push:PassA", "pop"), concrete.debugEvents)
    }

    private class RecordingBackend : RenderBackend {
        var beginFrameCalls = 0
        var endFrameCalls = 0
        var invalidateCalls = 0
        var closeCalls = 0
        val debugEvents = ArrayList<String>()

        override fun beginFrame() { beginFrameCalls++ }
        override fun endFrame() { endFrameCalls++ }
        override fun createProgram(
            vertexSource: String,
            fragmentSource: String,
            layouts: VertexInputLayout,
            reflection: ShaderReflection?
        ): ProgramHandle = throw UnsupportedOperationException()
        override fun bindProgram(program: ProgramHandle) {}
        override fun createTexture(
            image: BufferedImage,
            mipmap: Boolean,
            format: TextureFormat,
            sampler: SamplerState
        ): TextureHandle = throw UnsupportedOperationException()
        override fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage) {}
        override fun bindTexture(texture: TextureHandle, unit: Int) {}
        override fun beginVertices(program: ProgramHandle, vertexCount: Int, cursor: sweetie.evaware.luma.vertex.VertexCursor) {}
        override fun endVertices(
            program: ProgramHandle,
            cursor: sweetie.evaware.luma.vertex.VertexCursor,
            vertexCount: Int,
            uniforms: ShaderUniforms,
            primitiveType: PrimitiveType
        ) {}
        override fun createRenderTarget(
            width: Int,
            height: Int,
            useDepth: Boolean,
            format: RenderTargetFormat,
            sampler: SamplerState,
            useStencil: Boolean
        ): RenderTargetHandle = throw UnsupportedOperationException()
        override fun beginRenderTarget(target: RenderTargetHandle, clearColor: FloatArray?) {}
        override fun endRenderTarget() {}
        override fun pushDebugGroup(label: String) { debugEvents.add("push:$label") }
        override fun popDebugGroup() { debugEvents.add("pop") }
        override fun invalidatePipelineCache() { invalidateCalls++ }
        override fun close() { closeCalls++ }
        override fun hasContext(): Boolean = true
    }
}
