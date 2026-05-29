package sweetie.evaware.luma.texture

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL33C
import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.resource.GlResources
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.IntBuffer
import java.nio.ByteBuffer

object TextureUploader {
    private var uploadBuffer: IntBuffer = MemoryUtil.memAllocInt(1)
    private var byteUploadBuffer: ByteBuffer = MemoryUtil.memAlloc(4)
    private var scratchPixels = IntArray(0)

    private fun getBoundTexture2d(): Int {
        return Luma.platform.getBoundTexture2d()
    }

    fun upload(image: BufferedImage, mipmap: Boolean = true): TextureHandle {
        val textureId = GL11.glGenTextures()
        val previousUnpackAlignment = 4
        val previousUnpackRowLength = 0
        val previousUnpackSkipRows = 0
        val previousUnpackSkipPixels = 0
        val previousTexture = getBoundTexture2d()

        val pixelCount = image.width * image.height
        val pixels = pixels(image, pixelCount)
        val buffer = ensureBuffer(pixelCount)

        var uploaded = false
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

            buffer.clear()
            buffer.put(pixels, 0, pixelCount)
            buffer.flip()

            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4)
            GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, 0)
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_ROWS, 0)
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_PIXELS, 0)
            GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGBA8,
                image.width,
                image.height,
                0,
                GL12.GL_BGRA,
                GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
                buffer
            )
            if (mipmap) {
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D)
            }
            uploaded = true
            return GlResources.track(TextureHandle(textureId, image.width, image.height))
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture)
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, previousUnpackAlignment)
            GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, previousUnpackRowLength)
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_ROWS, previousUnpackSkipRows)
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_PIXELS, previousUnpackSkipPixels)
            Luma.invalidateBindings()

            if (!uploaded) {
                GL11.glDeleteTextures(textureId)
                Luma.onTextureDeleted(textureId)
            }
        }
    }

    fun uploadCoverage(image: BufferedImage): TextureHandle {
        val textureId = GL11.glGenTextures()
        val previousUnpackAlignment = 4
        val previousUnpackRowLength = 0
        val previousUnpackSkipRows = 0
        val previousUnpackSkipPixels = 0
        val previousTexture = getBoundTexture2d()

        val buffer = coverageBuffer(image)

        var uploaded = false
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)

            val swizzle = BufferUtils.createIntBuffer(4).apply {
                put(GL11.GL_ONE)
                put(GL11.GL_ONE)
                put(GL11.GL_ONE)
                put(GL11.GL_RED)
                flip()
            }
            GL11.glTexParameteriv(GL11.GL_TEXTURE_2D, GL33C.GL_TEXTURE_SWIZZLE_RGBA, swizzle)

            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1)
            GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, 0)
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_ROWS, 0)
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_PIXELS, 0)
            GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL30.GL_R8,
                image.width,
                image.height,
                0,
                GL11.GL_RED,
                GL11.GL_UNSIGNED_BYTE,
                buffer
            )
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D)
            uploaded = true
            return GlResources.track(TextureHandle(textureId, image.width, image.height))
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture)
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, previousUnpackAlignment)
            GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, previousUnpackRowLength)
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_ROWS, previousUnpackSkipRows)
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_PIXELS, previousUnpackSkipPixels)
            Luma.invalidateBindings()

            if (!uploaded) {
                GL11.glDeleteTextures(textureId)
                Luma.onTextureDeleted(textureId)
            }
        }
    }

    fun updateCoverage(textureId: Int, atlasHeight: Int, x: Int, y: Int, image: BufferedImage) {
        val previousUnpackAlignment = 4
        val previousUnpackRowLength = 0
        val previousUnpackSkipRows = 0
        val previousUnpackSkipPixels = 0
        val previousTexture = getBoundTexture2d()

        val buffer = coverageBuffer(image)

        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1)
            GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, 0)
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_ROWS, 0)
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_PIXELS, 0)
            GL11.glTexSubImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                x,
                atlasHeight - y - image.height,
                image.width,
                image.height,
                GL11.GL_RED,
                GL11.GL_UNSIGNED_BYTE,
                buffer
            )
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D)
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture)
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, previousUnpackAlignment)
            GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, previousUnpackRowLength)
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_ROWS, previousUnpackSkipRows)
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_PIXELS, previousUnpackSkipPixels)
            Luma.invalidateBindings()
        }
    }

    fun ensureCoverageFiltering(textureId: Int) {
        val previousTexture = getBoundTexture2d()
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, 0f)
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture)
            Luma.invalidateBindings()
        }
    }

    private fun pixels(image: BufferedImage, pixelCount: Int): IntArray {
        val dataBuffer = image.raster.dataBuffer
        if (image.type == BufferedImage.TYPE_INT_ARGB && dataBuffer is DataBufferInt) {
            return dataBuffer.data
        }

        if (scratchPixels.size < pixelCount) {
            scratchPixels = IntArray(nextCapacity(pixelCount, scratchPixels.size.coerceAtLeast(1)))
        }
        image.getRGB(0, 0, image.width, image.height, scratchPixels, 0, image.width)
        return scratchPixels
    }

    private fun ensureBuffer(requiredPixels: Int): IntBuffer {
        if (requiredPixels <= uploadBuffer.capacity()) {
            return uploadBuffer
        }

        uploadBuffer = MemoryUtil.memRealloc(uploadBuffer, nextCapacity(requiredPixels, uploadBuffer.capacity().coerceAtLeast(1)))
        uploadBuffer.clear()
        return uploadBuffer
    }

    private fun coverageBuffer(image: BufferedImage): ByteBuffer {
        val requiredBytes = image.width * image.height
        if (requiredBytes > byteUploadBuffer.capacity()) {
            byteUploadBuffer = MemoryUtil.memRealloc(byteUploadBuffer, nextCapacity(requiredBytes, byteUploadBuffer.capacity().coerceAtLeast(4)))
        }
        byteUploadBuffer.clear()

        val pixels = pixels(image, image.width * image.height)
        var row = image.height - 1
        while (row >= 0) {
            val rowOffset = row * image.width
            var x = 0
            while (x < image.width) {
                val coverage = ((pixels[rowOffset + x] ushr 24) and 0xFF).toByte()
                byteUploadBuffer.put(coverage)
                x++
            }
            row--
        }

        byteUploadBuffer.flip()
        return byteUploadBuffer
    }

    private fun nextCapacity(required: Int, current: Int): Int {
        var capacity = current
        while (capacity < required) {
            capacity = capacity shl 1
        }
        return capacity
    }

    fun close() {
        MemoryUtil.memFree(uploadBuffer)
        MemoryUtil.memFree(byteUploadBuffer)
        uploadBuffer = MemoryUtil.memAllocInt(1)
        byteUploadBuffer = MemoryUtil.memAlloc(4)
        scratchPixels = IntArray(0)
    }
}
