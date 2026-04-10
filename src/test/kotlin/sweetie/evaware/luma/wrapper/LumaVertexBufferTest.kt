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
}
