package sweetie.evaware.luma.texture

import org.lwjgl.system.MemoryUtil
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.awt.image.SinglePixelPackedSampleModel
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer

class RgbaTransferBuffer(
    initialPixelCapacity: Int = DEFAULT_PIXEL_CAPACITY
) : AutoCloseable {
    private var pixels = IntArray(initialPixelCapacity.coerceAtLeast(1))
    private var buffer: ByteBuffer? = allocate(pixels.size * RGBA_BYTES)
    private var words: IntBuffer? = buffer?.asIntBuffer()

    fun write(image: BufferedImage): ByteBuffer {
        val pixelCount = Math.multiplyExact(image.width, image.height)
        ensureCapacity(pixelCount)
        val target = requireNotNull(buffer)
        return write(image, target, requireNotNull(words))
    }

    fun write(image: BufferedImage, target: ByteBuffer): ByteBuffer {
        val pixelCount = Math.multiplyExact(image.width, image.height)
        val requiredBytes = Math.multiplyExact(pixelCount, RGBA_BYTES)
        require(target.capacity() >= requiredBytes) {
            "Target buffer capacity ${target.capacity()} is smaller than required $requiredBytes bytes"
        }
        ensurePixelCapacity(pixelCount)
        target.order(ByteOrder.nativeOrder())
        target.clear()
        target.limit(requiredBytes)
        return write(image, target, target.asIntBuffer())
    }

    override fun close() {
        buffer?.let(MemoryUtil::memFree)
        buffer = null
        words = null
        pixels = IntArray(0)
    }

    private fun ensureCapacity(requiredPixels: Int) {
        val requiredBytes = Math.multiplyExact(requiredPixels, RGBA_BYTES)
        if (pixels.size >= requiredPixels && buffer?.capacity()?.let { it >= requiredBytes } == true) return

        val currentBufferPixels = (buffer?.capacity() ?: 0) / RGBA_BYTES
        val capacity = grow(currentBufferPixels, requiredPixels)
        ensurePixelCapacity(capacity)
        val capacityBytes = Math.multiplyExact(capacity, RGBA_BYTES)
        buffer = buffer?.let { MemoryUtil.memRealloc(it, capacityBytes).order(ByteOrder.nativeOrder()) }
            ?: allocate(capacityBytes)
        words = buffer?.asIntBuffer()
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

    private fun write(image: BufferedImage, target: ByteBuffer, targetWords: IntBuffer): ByteBuffer {
        val pixelCount = Math.multiplyExact(image.width, image.height)
        target.clear()
        targetWords.clear()

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
            for (row in 0 until image.height) {
                var offset = start + row * model.scanlineStride
                val end = offset + image.width
                while (offset < end) targetWords.put(toNativeRgba(source[offset++]))
            }
        } else {
            image.getRGB(0, 0, image.width, image.height, pixels, 0, image.width)
            for (index in 0 until pixelCount) targetWords.put(toNativeRgba(pixels[index]))
        }
        target.position(pixelCount * RGBA_BYTES)
        target.flip()
        return target
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
