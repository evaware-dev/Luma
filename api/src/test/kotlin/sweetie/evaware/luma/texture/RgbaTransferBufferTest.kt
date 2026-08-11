package sweetie.evaware.luma.texture

import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals

class RgbaTransferBufferTest {
    @Test
    fun `writes subimages without leaking parent stride`() {
        val parent = BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB)
        parent.setRGB(1, 0, 0x7F123456)
        parent.setRGB(2, 0, 0xFFABCDEF.toInt())
        val subimage = parent.getSubimage(1, 0, 2, 1)

        RgbaTransferBuffer(initialPixelCapacity = 1).use { transfer ->
            val actual = ByteArray(8)
            transfer.write(subimage).get(actual)
            assertContentEquals(
                byteArrayOf(
                    0x12, 0x34, 0x56, 0x7F,
                    0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte(), 0xFF.toByte()
                ),
                actual
            )
        }
    }

    @Test
    fun `can be reused after close`() {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).apply {
            setRGB(0, 0, 0xFF010203.toInt())
        }
        val transfer = RgbaTransferBuffer()
        transfer.close()

        val actual = ByteArray(4)
        transfer.write(image).get(actual)
        assertContentEquals(byteArrayOf(1, 2, 3, 0xFF.toByte()), actual)
        transfer.close()
    }

    @Test
    fun `writes a larger fallback image directly into a caller buffer`() {
        val image = BufferedImage(4, 2, BufferedImage.TYPE_4BYTE_ABGR).apply {
            setRGB(3, 1, 0x7F123456)
        }

        RgbaTransferBuffer(initialPixelCapacity = 1).use { transfer ->
            val target = ByteBuffer.allocateDirect(32)
            val result = transfer.write(image, target)
            val lastPixel = ByteArray(4)
            result.position(28)
            result.get(lastPixel)

            assertContentEquals(byteArrayOf(0x12, 0x34, 0x56, 0x7F), lastPixel)

            val internal = ByteArray(32)
            transfer.write(image).get(internal)
            assertContentEquals(lastPixel, internal.copyOfRange(28, 32))
        }
    }
}
