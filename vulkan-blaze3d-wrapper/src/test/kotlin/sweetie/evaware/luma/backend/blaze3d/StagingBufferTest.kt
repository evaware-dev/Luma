package sweetie.evaware.luma.backend.blaze3d

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import sweetie.evaware.luma.vertex.VertexCursor

class StagingBufferTest {
    @Test
    fun `appendBytes writes contiguous payloads and grows cleanly`() {
        val staging = StagingBuffer(4)
        try {
            assertEquals(0L, staging.appendBytes(ByteBuffer.wrap(byteArrayOf(1, 2)), 2))
            assertEquals(2L, staging.appendBytes(ByteBuffer.wrap(byteArrayOf(3, 4, 5)), 3))

            staging.flip()
            val contents = ByteArray(staging.buffer.remaining())
            staging.buffer.get(contents)
            assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), contents)
        } finally {
            staging.close()
        }
    }

    @Test
    fun `beginDirectAligned pads offset to stride alignment and commits exact bytes`() {
        val staging = StagingBuffer(8)
        val cursor = VertexCursor()
        try {
            staging.beginDirectAligned(8, 8, cursor)
            assertEquals(0L, cursor.token)
            cursor.vec2(1f, 2f)
            staging.commitDirect(cursor.token, 8)

            // Reserve with 12-byte stride after 8 bytes -> padded to 12
            staging.beginDirectAligned(12, 12, cursor)
            assertEquals(12L, cursor.token)
            cursor.vec3(3f, 4f, 5f)
            staging.commitDirect(cursor.token, 12)

            staging.flip()
            assertEquals(24, staging.buffer.remaining())
        } finally {
            staging.close()
        }
    }

    @Test
    fun `beginAlignedWrite zero-pads to requested alignment`() {
        val staging = StagingBuffer(8)
        try {
            assertEquals(0L, staging.appendBytes(ByteBuffer.wrap(byteArrayOf(1, 2, 3)), 3))
            assertEquals(8L, staging.beginAlignedWrite(4, 8))
            staging.buffer.put(4).put(5)

            staging.flip()
            val contents = ByteArray(staging.buffer.remaining())
            staging.buffer.get(contents)
            assertArrayEquals(byteArrayOf(1, 2, 3, 0, 0, 0, 0, 0, 4, 5), contents)
        } finally {
            staging.close()
        }
    }
}
