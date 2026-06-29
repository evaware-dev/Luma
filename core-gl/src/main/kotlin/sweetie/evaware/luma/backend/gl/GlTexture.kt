package sweetie.evaware.luma.backend.gl

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.api.TextureHandle
import java.awt.image.BufferedImage

class GlTexture(
    val textureId: Int,
    override val width: Int,
    override val height: Int
) : TextureHandle {

    fun update(x: Int, y: Int, image: BufferedImage) {
        val previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        val w = image.width
        val h = image.height
        val pixelCount = w * h
        val pixels = getArgbPixels(image)

        val buffer = MemoryUtil.memAlloc(pixelCount * 4)
        try {
            for (p in pixels) {
                buffer.put((p ushr 16 and 0xFF).toByte())
                buffer.put((p ushr 8 and 0xFF).toByte())
                buffer.put((p and 0xFF).toByte())
                buffer.put((p ushr 24 and 0xFF).toByte())
            }
            buffer.flip()

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4)
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0)
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0)
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0)
            GL11.glTexSubImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                x,
                y,
                w,
                h,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                buffer
            )
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture)
            MemoryUtil.memFree(buffer)
        }
    }

    fun bind(unit: Int) {
        val activeUnit = GL13.GL_TEXTURE0 + unit
        GL13.glActiveTexture(activeUnit)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
    }

    override fun close() {
        GL11.glDeleteTextures(textureId)
    }

    companion object {
        fun create(image: BufferedImage, mipmap: Boolean): GlTexture {
            val textureId = GL11.glGenTextures()
            val previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)

            val w = image.width
            val h = image.height
            val pixelCount = w * h
            val pixels = getArgbPixels(image)

            val buffer = MemoryUtil.memAlloc(pixelCount * 4)
            try {
                for (p in pixels) {
                    buffer.put((p ushr 16 and 0xFF).toByte())
                    buffer.put((p ushr 8 and 0xFF).toByte())
                    buffer.put((p and 0xFF).toByte())
                    buffer.put((p ushr 24 and 0xFF).toByte())
                }
                buffer.flip()

                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
                if (mipmap) {
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR)
                } else {
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0)
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MIN_LOD, 0)
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD, 0)
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
                }
                GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, 0f)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)

                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4)
                GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0)
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0)
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0)
                GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    GL11.GL_RGBA8,
                    w,
                    h,
                    0,
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    buffer
                )

                if (mipmap) {
                    GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D)
                }

                return GlTexture(textureId, w, h)
            } finally {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture)
                MemoryUtil.memFree(buffer)
            }
        }

        private fun getArgbPixels(image: BufferedImage): IntArray {
            return (image.raster.dataBuffer as? java.awt.image.DataBufferInt)?.data
                ?: image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        }
    }
}
