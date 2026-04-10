package sweetie.evaware.luma.wrapper.vertex

import kotlin.test.Test

class VertexStreamTest {
    @Test
    fun `close is idempotent`() {
        val stream = VertexStream()
        stream.close()
        stream.close()
    }
}
