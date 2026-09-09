package sweetie.evaware.luma.backend.blaze3d

import java.nio.ByteBuffer
import org.lwjgl.system.MemoryUtil
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class StagingBufferTest {
    @Test
    fun `address appends remain contiguous`() {
        val source = MemoryUtil.memAlloc(4)
        try {
            val staging = StagingBuffer(4)
            source.put(0, 1).put(1, 2)
            assertEquals(0L, staging.appendFromAddress(MemoryUtil.memAddress(source), 2))
            source.put(0, 3).put(1, 4)
            assertEquals(2L, staging.appendFromAddress(MemoryUtil.memAddress(source), 2))

            staging.flip()
            val contents = ByteArray(staging.buffer.remaining())
            staging.buffer.get(contents)
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), contents)
        } finally {
            MemoryUtil.memFree(source)
        }
    }

    @Test
    fun `aligned append pads offset without changing payload`() {
        val staging = StagingBuffer(8)

        assertEquals(0L, staging.append(ByteBuffer.wrap(byteArrayOf(1, 2, 3))))
        assertEquals(8L, staging.appendAligned(ByteBuffer.wrap(byteArrayOf(4, 5)), 8))

        staging.flip()
        val contents = ByteArray(staging.buffer.remaining())
        staging.buffer.get(contents)
        assertArrayEquals(byteArrayOf(1, 2, 3, 0, 0, 0, 0, 0, 4, 5), contents)
    }

    @Test
    fun `aligned address append aligns independent vertex layouts`() {
        val source = MemoryUtil.memAlloc(4)
        val staging = StagingBuffer(8)
        try {
            source.putInt(0, 0x01020304)
            assertEquals(0L, staging.appendFromAddressAligned(MemoryUtil.memAddress(source), 3, 3))
            assertEquals(4L, staging.appendFromAddressAligned(MemoryUtil.memAddress(source), 4, 4))
        } finally {
            staging.close()
            MemoryUtil.memFree(source)
        }
    }
}
