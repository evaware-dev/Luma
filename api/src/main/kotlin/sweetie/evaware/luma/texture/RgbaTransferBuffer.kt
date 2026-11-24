package sweetie.evaware.luma.texture

import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.api.TextureFormat
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.awt.image.DataBufferInt
import java.awt.image.SinglePixelPackedSampleModel
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RgbaTransferBuffer(
    initialPixelCapacity: Int = DEFAULT_PIXEL_CAPACITY
) : AutoCloseable {
    private var pixels = IntArray(initialPixelCapacity.coerceAtLeast(1))
    private var buffer: ByteBuffer? = allocate(pixels.size * RGBA_BYTES)

    fun write(image: BufferedImage): ByteBuffer = write(image, TextureFormat.RGBA8)

    fun write(image: BufferedImage, format: TextureFormat): ByteBuffer {
        require(format.supportsBufferedImageUpload) {
            "BufferedImage upload is not supported for format $format; upload raw ByteBuffer data instead"
        }
        val pixelCount = Math.multiplyExact(image.width, image.height)
        val requiredBytes = format.byteSize(image.width, image.height)
        ensureCapacity(pixelCount, requiredBytes)
        val target = requireNotNull(buffer)
        return writePixels(image, target, format)
    }

    fun write(image: BufferedImage, target: ByteBuffer): ByteBuffer = write(image, target, TextureFormat.RGBA8)

    fun write(image: BufferedImage, target: ByteBuffer, format: TextureFormat): ByteBuffer {
        require(format.supportsBufferedImageUpload) {
            "BufferedImage upload is not supported for format $format; upload raw ByteBuffer data instead"
        }
        val pixelCount = Math.multiplyExact(image.width, image.height)
        val requiredBytes = format.byteSize(image.width, image.height)
        require(target.capacity() >= requiredBytes) {
            "Target buffer capacity ${target.capacity()} is smaller than required $requiredBytes bytes"
        }
        ensurePixelCapacity(pixelCount)
        target.order(ByteOrder.nativeOrder())
        target.clear()
        target.limit(requiredBytes)
        return writePixels(image, target, format)
    }

    override fun close() {
        buffer?.let(MemoryUtil::memFree)
        buffer = null
        pixels = IntArray(0)
    }

    private fun ensureCapacity(requiredPixels: Int, requiredBytes: Int) {
        if (pixels.size >= requiredPixels && buffer?.capacity()?.let { it >= requiredBytes } == true) return

        val currentBytes = buffer?.capacity() ?: 0
        val newPixelCapacity = grow(pixels.size, requiredPixels)
        ensurePixelCapacity(newPixelCapacity)
        val capacityBytes = grow(currentBytes, requiredBytes)
        buffer = buffer?.let { MemoryUtil.memRealloc(it, capacityBytes).order(ByteOrder.nativeOrder()) }
            ?: allocate(capacityBytes)
    }

    private fun ensurePixelCapacity(requiredPixels: Int) {
        if (pixels.size < requiredPixels) pixels = IntArray(grow(pixels.size, requiredPixels))
    }

    private fun grow(current: Int, required: Int): Int {
        var capacity = current.coerceAtLeast(DEFAULT_PIXEL_CAPACITY)
        while (capacity < required) capacity = Math.multiplyExact(capacity, 2)
        return capacity
    }

    private fun allocate(bytes: Int): ByteBuffer = MemoryUtil.memAlloc(bytes).order(ByteOrder.nativeOrder())

    private fun writePixels(image: BufferedImage, target: ByteBuffer, format: TextureFormat): ByteBuffer {
        val pixelCount = Math.multiplyExact(image.width, image.height)
        target.clear()

        if (format == TextureFormat.R8) {
            writeR8Pixels(image, target, pixelCount)
            target.flip()
            return target
        }

        val raster = image.raster
        val data = raster.dataBuffer
        val model = raster.sampleModel
        if (
            image.type == BufferedImage.TYPE_INT_ARGB &&
            data is DataBufferInt &&
            model is SinglePixelPackedSampleModel
        ) {
            val source = data.data
            val start = data.offset + model.getOffset(
                raster.minX - raster.sampleModelTranslateX,
                raster.minY - raster.sampleModelTranslateY
            )
            if (format == TextureFormat.RGBA8) {
                for (row in 0 until image.height) {
                    var offset = start + row * model.scanlineStride
                    val end = offset + image.width
                    while (offset < end) target.putInt(toNativeRgba(source[offset++]))
                }
            } else {
                // RG8
                for (row in 0 until image.height) {
                    var offset = start + row * model.scanlineStride
                    val end = offset + image.width
                    while (offset < end) {
                        val argb = source[offset++]
                        target.put(((argb ushr 16) and 0xFF).toByte())
                        target.put(((argb ushr 8) and 0xFF).toByte())
                    }
                }
            }
        } else {
            image.getRGB(0, 0, image.width, image.height, pixels, 0, image.width)
            if (format == TextureFormat.RGBA8) {
                for (index in 0 until pixelCount) target.putInt(toNativeRgba(pixels[index]))
            } else {
                // RG8
                for (index in 0 until pixelCount) {
                    val argb = pixels[index]
                    target.put(((argb ushr 16) and 0xFF).toByte())
                    target.put(((argb ushr 8) and 0xFF).toByte())
                }
            }
        }
        target.flip()
        return target
    }

    private fun writeR8Pixels(image: BufferedImage, target: ByteBuffer, pixelCount: Int) {
        val raster = image.raster
        val data = raster.dataBuffer
        if (
            image.type == BufferedImage.TYPE_BYTE_GRAY &&
            data is DataBufferByte &&
            raster.sampleModel.numBands == 1 &&
            raster.width == image.width &&
            raster.height == image.height &&
            raster.minX == 0 &&
            raster.minY == 0 &&
            raster.sampleModelTranslateX == 0 &&
            raster.sampleModelTranslateY == 0 &&
            data.data.size >= data.offset + pixelCount
        ) {
            target.put(data.data, data.offset, pixelCount)
            return
        }
        image.getRGB(0, 0, image.width, image.height, pixels, 0, image.width)
        for (index in 0 until pixelCount) {
            target.put(((pixels[index] ushr 16) and 0xFF).toByte())
        }
    }

    private fun toNativeRgba(argb: Int): Int {
        val rgba = Integer.rotateLeft(argb, 8)
        return if (LITTLE_ENDIAN) Integer.reverseBytes(rgba) else rgba
    }

    private companion object {
        const val RGBA_BYTES = 4
        const val DEFAULT_PIXEL_CAPACITY = 64
        val LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
    }
}
