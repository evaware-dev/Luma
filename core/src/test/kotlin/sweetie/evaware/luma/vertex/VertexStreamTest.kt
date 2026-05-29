package sweetie.evaware.luma.vertex

import kotlin.test.assertFailsWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VertexStreamTest {
    @Test
    fun `close is idempotent`() {
        val stream = VertexStream()
        stream.close()
        stream.close()
    }

    @Test
    fun `write after close fails fast`() {
        val stream = VertexStream()
        stream.close()

        assertFailsWith<IllegalStateException> {
            VertexWriter(stream).vec2(2, 1f, 2f)
        }
    }

    @Test
    fun `attribute fast path validates layout count`() {
        val layout = VertexLayout().apply {
            add(ShaderVertType.FLOAT, 4, 0)
        }
        val stream = VertexStream()

        try {
            assertFailsWith<IllegalArgumentException> {
                stream.putAttribute2(layout, 0, 1f, 2f)
            }
            assertFalse(stream.hasVertices())
        } finally {
            stream.close()
        }
    }

    @Test
    fun `grouped writer completes vertex at stride boundary`() {
        val stream = VertexStream()
        val writer = VertexWriter(stream)

        try {
            writer.vec2(6, 1f, 2f)
            assertFalse(stream.hasVertices())

            writer.vec4(6, 1f, 1f, 1f, 1f)
            assertTrue(stream.hasVertices())
        } finally {
            stream.close()
        }
    }

    @Test
    fun `grouped writer rejects vertex stride overflow`() {
        val stream = VertexStream()
        val writer = VertexWriter(stream)

        try {
            writer.vec4(6, 1f, 2f, 3f, 4f)

            assertFailsWith<IllegalStateException> {
                writer.vec4(6, 5f, 6f, 7f, 8f)
            }
            assertFalse(stream.hasVertices())
        } finally {
            stream.close()
        }
    }

    @Test
    fun `texture vertex staging stays inside cpu frame budget`() {
        val layout = textureLayout()
        val stream = VertexStream()
        val writer = VertexWriter(stream)
        val quadsPerFrame = 2048
        val verticesPerFrame = quadsPerFrame * 6
        val frames = 120

        try {
            assertEquals(12, layout.strideFloats)
            repeat(20) {
                stageTextureFrame(writer, quadsPerFrame)
                assertTrue(stream.hasVertices())
                stream.clear()
            }

            val started = System.nanoTime()
            repeat(frames) {
                stageTextureFrame(writer, quadsPerFrame)
                stream.clear()
            }
            val elapsedNanos = System.nanoTime() - started
            val averageFrameNanos = elapsedNanos / frames
            val averageVertexNanos = elapsedNanos / (frames * verticesPerFrame)
            val estimatedFps = frames * 1_000_000_000.0 / elapsedNanos.toDouble()

            assertTrue(
                estimatedFps >= 144.0,
                "CPU staging budget missed: ${"%.1f".format(estimatedFps)} fps, ${averageFrameNanos / 1_000_000.0} ms/frame"
            )
            assertTrue(
                averageVertexNanos <= 1_100L,
                "CPU staging regression: $averageVertexNanos ns/vertex for $verticesPerFrame vertices/frame"
            )
        } finally {
            stream.close()
        }
    }

    @Test
    fun `rounded rect vertex staging stays inside cpu frame budget`() {
        val layout = roundedRectLayout()
        val stream = VertexStream()
        val writer = VertexWriter(stream)
        val quadsPerFrame = 2048
        val verticesPerFrame = quadsPerFrame * 6
        val frames = 120

        try {
            assertEquals(18, layout.strideFloats)
            repeat(20) {
                stageRoundedRectFrame(writer, quadsPerFrame)
                assertTrue(stream.hasVertices())
                stream.clear()
            }

            val started = System.nanoTime()
            repeat(frames) {
                stageRoundedRectFrame(writer, quadsPerFrame)
                stream.clear()
            }
            val elapsedNanos = System.nanoTime() - started
            val averageVertexNanos = elapsedNanos / (frames * verticesPerFrame)
            val estimatedFps = frames * 1_000_000_000.0 / elapsedNanos.toDouble()

            assertTrue(
                estimatedFps >= 144.0,
                "Rounded rect CPU staging budget missed: ${"%.1f".format(estimatedFps)} fps"
            )
            assertTrue(
                averageVertexNanos <= 1_400L,
                "Rounded rect CPU staging regression: $averageVertexNanos ns/vertex for $verticesPerFrame vertices/frame"
            )
        } finally {
            stream.close()
        }
    }

    private fun textureLayout() = VertexLayout().apply {
        add(ShaderVertType.FLOAT, 2, 0)
        add(ShaderVertType.FLOAT, 2, 1)
        add(ShaderVertType.FLOAT, 4, 2)
        add(ShaderVertType.FLOAT, 4, 3)
    }

    private fun roundedRectLayout() = VertexLayout().apply {
        add(ShaderVertType.FLOAT, 2, 0)
        add(ShaderVertType.FLOAT, 2, 1)
        add(ShaderVertType.FLOAT, 2, 2)
        add(ShaderVertType.FLOAT, 4, 3)
        add(ShaderVertType.FLOAT, 4, 4)
        add(ShaderVertType.FLOAT, 4, 5)
    }

    private fun stageTextureFrame(writer: VertexWriter, quads: Int) {
        var index = 0
        while (index < quads) {
            val x = (index and 31).toFloat()
            val y = (index shr 5).toFloat()
            putTextureVertex(writer, x, y, 0f, 0f)
            putTextureVertex(writer, x, y + 1f, 0f, 1f)
            putTextureVertex(writer, x + 1f, y + 1f, 1f, 1f)
            putTextureVertex(writer, x, y, 0f, 0f)
            putTextureVertex(writer, x + 1f, y + 1f, 1f, 1f)
            putTextureVertex(writer, x + 1f, y, 1f, 0f)
            index++
        }
    }

    private fun stageRoundedRectFrame(writer: VertexWriter, quads: Int) {
        var index = 0
        while (index < quads) {
            val x = (index and 31).toFloat()
            val y = (index shr 5).toFloat()
            putRoundedRectVertex(writer, x, y, 0f, 0f)
            putRoundedRectVertex(writer, x, y + 1f, 0f, 1f)
            putRoundedRectVertex(writer, x + 1f, y + 1f, 1f, 1f)
            putRoundedRectVertex(writer, x, y, 0f, 0f)
            putRoundedRectVertex(writer, x + 1f, y + 1f, 1f, 1f)
            putRoundedRectVertex(writer, x + 1f, y, 1f, 0f)
            index++
        }
    }

    private fun putTextureVertex(writer: VertexWriter, x: Float, y: Float, u: Float, v: Float) {
        writer
            .vec2(12, x, y)
            .vec2(12, u, v)
            .vec4(12, 1f, 1f, 1f, 1f)
            .vec4(12, 0f, 0f, 0f, 0f)
    }

    private fun putRoundedRectVertex(writer: VertexWriter, x: Float, y: Float, localX: Float, localY: Float) {
        writer
            .vec2(18, x, y)
            .vec2(18, localX, localY)
            .vec2(18, 1f, 1f)
            .vec4(18, 4f, 4f, 4f, 4f)
            .vec4(18, 1f, 1f, 1f, 1f)
            .vec4(18, 0f, 0f, 0f, 1f)
    }
}
