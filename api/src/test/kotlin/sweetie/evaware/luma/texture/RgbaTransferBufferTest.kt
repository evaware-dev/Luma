package sweetie.evaware.luma.texture

import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import sweetie.evaware.luma.api.TextureFormat

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

    @Test
    fun `writes R8 and RG8 formats with tight stride and rejects RGBA16F BufferedImage`() {
        val image = BufferedImage(3, 1, BufferedImage.TYPE_INT_ARGB).apply {
            setRGB(0, 0, 0xFF102030.toInt())
            setRGB(1, 0, 0xFF405060.toInt())
            setRGB(2, 0, 0xFF708090.toInt())
        }

        RgbaTransferBuffer(initialPixelCapacity = 1).use { transfer ->
            val r8 = ByteArray(3)
            val r8Buf = transfer.write(image, TextureFormat.R8)
            assertEquals(3, r8Buf.remaining())
            r8Buf.get(r8)
            assertContentEquals(byteArrayOf(0x10, 0x40, 0x70), r8)

            val rg8 = ByteArray(6)
            val rg8Buf = transfer.write(image, TextureFormat.RG8)
            assertEquals(6, rg8Buf.remaining())
            rg8Buf.get(rg8)
            assertContentEquals(byteArrayOf(0x10, 0x20, 0x40, 0x50, 0x70, 0x80.toByte()), rg8)

            assertFailsWith<IllegalArgumentException> {
                transfer.write(image, TextureFormat.RGBA16F)
            }
        }
    }

    @Test
    fun `writes BYTE_GRAY fast path directly for R8`() {
        val gray = BufferedImage(3, 2, BufferedImage.TYPE_BYTE_GRAY)
        val raster = gray.raster
        for (i in 0 until 6) raster.setSample(i % 3, i / 3, 0, (i + 1) * 10)

        RgbaTransferBuffer().use { transfer ->
            val actual = ByteArray(6)
            transfer.write(gray, TextureFormat.R8).get(actual)
            assertContentEquals(byteArrayOf(10, 20, 30, 40, 50, 60), actual)
        }
    }
}
