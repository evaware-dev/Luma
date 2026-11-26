package sweetie.evaware.luma.texture

import java.awt.image.BufferedImage
import sweetie.evaware.luma.api.TextureFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class RgbaTransferBufferFastPathTest {
    @Test
    fun `fast paths match expected RGBA8 bytes across image types including subimages`() {
        val types = intArrayOf(
            BufferedImage.TYPE_INT_ARGB,
            BufferedImage.TYPE_INT_RGB,
            BufferedImage.TYPE_INT_BGR,
            BufferedImage.TYPE_4BYTE_ABGR,
            BufferedImage.TYPE_3BYTE_BGR
        )
        RgbaTransferBuffer().use { transfer ->
            for (type in types) {
                val hasAlpha = type == BufferedImage.TYPE_INT_ARGB || type == BufferedImage.TYPE_4BYTE_ABGR
                val full = BufferedImage(4, 4, type)
                val expectedAlpha = if (hasAlpha) 0x80 else 0xFF
                val argb0 = (expectedAlpha shl 24) or (0x12 shl 16) or (0x34 shl 8) or 0x56
                val argb1 = (expectedAlpha shl 24) or (0xAB shl 16) or (0xCD shl 8) or 0xEF
                full.setRGB(1, 1, argb0)
                full.setRGB(2, 1, argb1)

                val sub = full.getSubimage(1, 1, 2, 1)
                val buf = transfer.write(sub, TextureFormat.RGBA8)
                assertEquals(8, buf.remaining(), "type=$type")
                assertEquals(0x12, buf.get(0).toInt() and 0xFF, "r0 type=$type")
                assertEquals(0x34, buf.get(1).toInt() and 0xFF, "g0 type=$type")
                assertEquals(0x56, buf.get(2).toInt() and 0xFF, "b0 type=$type")
                assertEquals(expectedAlpha, buf.get(3).toInt() and 0xFF, "a0 type=$type")
                assertEquals(0xAB, buf.get(4).toInt() and 0xFF, "r1 type=$type")
                assertEquals(0xCD, buf.get(5).toInt() and 0xFF, "g1 type=$type")
                assertEquals(0xEF, buf.get(6).toInt() and 0xFF, "b1 type=$type")
                assertEquals(expectedAlpha, buf.get(7).toInt() and 0xFF, "a1 type=$type")

                val rg = transfer.write(sub, TextureFormat.RG8)
                assertEquals(4, rg.remaining(), "rg8 type=$type")
                assertEquals(0x12, rg.get(0).toInt() and 0xFF, "rg r0 type=$type")
                assertEquals(0x34, rg.get(1).toInt() and 0xFF, "rg g0 type=$type")
                assertEquals(0xAB, rg.get(2).toInt() and 0xFF, "rg r1 type=$type")
                assertEquals(0xCD, rg.get(3).toInt() and 0xFF, "rg g1 type=$type")
            }
        }
    }
}
