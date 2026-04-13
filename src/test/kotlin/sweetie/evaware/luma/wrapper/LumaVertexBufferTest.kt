package sweetie.evaware.luma.wrapper

import kotlin.test.Test
import kotlin.test.assertEquals

class LumaVertexBufferTest {
    @Test
    fun `vertex buffer tracks float and vertex counts`() {
        val buffer = LumaVertexBuffer(floatsPerVertex = 6)

        buffer.put2(1f, 2f)
        buffer.put4(3f, 4f, 5f, 6f)
        buffer.completeVertex()

        assertEquals(6, buffer.floatCount())
        assertEquals(1, buffer.vertexCount())

        buffer.close()
    }

    @Test
    fun `fast vertex path appends full 14-float vertex`() {
        val buffer = LumaVertexBuffer(floatsPerVertex = 14)

        buffer.putVertex14(
            1f, 2f, 3f, 4f, 5f, 6f, 7f,
            8f, 9f, 10f, 11f, 12f, 13f, 14f
        )

        assertEquals(14, buffer.floatCount())
        assertEquals(1, buffer.vertexCount())

        buffer.close()
    }
}
