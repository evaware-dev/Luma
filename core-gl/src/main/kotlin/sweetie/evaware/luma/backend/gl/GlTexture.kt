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

    internal fun requireOpen() = check(!closed) { "Texture is closed" }

    fun update(x: Int, y: Int, image: BufferedImage) {
        requireOpen()
        val previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        val pixelStore = capturePixelStore(pixelStoreState.get())
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
            setRequiredPixelStore()
            updateBound(x, y, image, transfer.get())
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture)
            restorePixelStore(pixelStore)
        }
    }

    fun bind(unit: Int) {
        requireOpen()
        val activeUnit = GL13.GL_TEXTURE0 + unit
        GL13.glActiveTexture(activeUnit)
        bindCurrentUnit()
        GL33.glBindSampler(unit, 0)
    }

    internal fun bindCurrentUnit() {
        requireOpen()
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
    }

    internal fun initializeBound(image: BufferedImage, transfer: RgbaTransferBuffer) {
        if (!mipmap) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MIN_LOD, 0)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD, 0)
        }
        GL11.glTexParameteri(
            GL11.GL_TEXTURE_2D,
            GL11.GL_TEXTURE_MIN_FILTER,
            if (mipmap) GL11.GL_LINEAR_MIPMAP_LINEAR else GL11.GL_LINEAR
        )
        GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, 0f)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        val buffer = transfer.write(image)
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_RGBA8,
            image.width,
            image.height,
            0,
            GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE,
            buffer
        )
        if (mipmap) GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D)
    }

    internal fun updateBound(x: Int, y: Int, image: BufferedImage, transfer: RgbaTransferBuffer) {
        requireOpen()
        val buffer = transfer.write(image)
        GL11.glTexSubImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            x,
            y,
            image.width,
            image.height,
            GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE,
            buffer
        )
        if (mipmap) GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D)
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
            val pixelStore = capturePixelStore(pixelStoreState.get())

            val texture = GlTexture(textureId, image.width, image.height, mipmap)
            try {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
                setRequiredPixelStore()
                texture.initializeBound(image, transfer.get())
                return texture
            } catch (failure: Throwable) {
                GL11.glDeleteTextures(textureId)
                throw failure
            } finally {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture)
                restorePixelStore(pixelStore)
            }
        }

        fun closeTransferBuffer() {
            transfer.get().close()
            transfer.remove()
            pixelStoreState.remove()
        }

        private fun setRequiredPixelStore() {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4)
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0)
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0)
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0)
        }

        private fun capturePixelStore(state: IntArray): IntArray {
            state[0] = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT)
            state[1] = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH)
            state[2] = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS)
            state[3] = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS)
            return state
        }

        private fun restorePixelStore(state: IntArray) {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, state[0])
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, state[1])
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, state[2])
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, state[3])
        }

        private val transfer = ThreadLocal.withInitial(::RgbaTransferBuffer)
        private val pixelStoreState = ThreadLocal.withInitial { IntArray(4) }
    }
}
