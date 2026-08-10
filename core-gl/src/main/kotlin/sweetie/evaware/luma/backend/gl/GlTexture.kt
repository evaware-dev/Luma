package sweetie.evaware.luma.backend.gl

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL33
import sweetie.evaware.luma.api.TextureHandle
import java.awt.image.BufferedImage
import sweetie.evaware.luma.texture.RgbaTransferBuffer

class GlTexture(
    val textureId: Int,
    override val width: Int,
    override val height: Int,
    private val mipmap: Boolean = false
) : TextureHandle {
    private var closed = false

    fun update(x: Int, y: Int, image: BufferedImage) {
        check(!closed) { "Texture is closed" }
        val previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        val pixelStore = capturePixelStore()
        val w = image.width
        val h = image.height
        val buffer = transfer.write(image)
        try {
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
            if (mipmap) GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D)
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture)
            restorePixelStore(pixelStore)
        }
    }

    fun bind(unit: Int) {
        check(!closed) { "Texture is closed" }
        val activeUnit = GL13.GL_TEXTURE0 + unit
        GL13.glActiveTexture(activeUnit)
        bindCurrentUnit()
        GL33.glBindSampler(unit, 0)
    }

    internal fun bindCurrentUnit() {
        check(!closed) { "Texture is closed" }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
    }

    override fun close() {
        if (closed) return
        closed = true
        GL11.glDeleteTextures(textureId)
    }

    companion object {
        fun create(image: BufferedImage, mipmap: Boolean): GlTexture {
            val textureId = GL11.glGenTextures()
            val previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
            val pixelStore = capturePixelStore()

            val w = image.width
            val h = image.height
            val buffer = transfer.write(image)
            try {
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

                return GlTexture(textureId, w, h, mipmap)
            } finally {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture)
                restorePixelStore(pixelStore)
            }
        }

        fun closeTransferBuffer() = transfer.close()

        private fun capturePixelStore() = intArrayOf(
            GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT),
            GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH),
            GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS),
            GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS)
        )

        private fun restorePixelStore(state: IntArray) {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, state[0])
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, state[1])
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, state[2])
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, state[3])
        }

        private val transfer = RgbaTransferBuffer()
    }
}
