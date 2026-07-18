package sweetie.evaware.luma.texture

import org.lwjgl.system.MemoryUtil
import java.awt.image.BufferedImage
import java.nio.ByteBuffer

class RgbaTransferBuffer(
    initialPixelCapacity: Int = DEFAULT_PIXEL_CAPACITY
) : AutoCloseable {
    private var pixels = IntArray(initialPixelCapacity.coerceAtLeast(1))
    private var buffer: ByteBuffer? = MemoryUtil.memAlloc(pixels.size * RGBA_BYTES)

    fun write(image: BufferedImage): ByteBuffer {
        val pixelCount = Math.multiplyExact(image.width, image.height)
        ensureCapacity(pixelCount)
        image.getRGB(0, 0, image.width, image.height, pixels, 0, image.width)

        val target = requireNotNull(buffer)
        target.clear()
        for (index in 0 until pixelCount) {
            val pixel = pixels[index]
            target.put((pixel ushr 16 and 0xFF).toByte())
            target.put((pixel ushr 8 and 0xFF).toByte())
            target.put((pixel and 0xFF).toByte())
            target.put((pixel ushr 24 and 0xFF).toByte())
        }
        target.flip()
        return target
    }

    override fun close() {
        buffer?.let(MemoryUtil::memFree)
        buffer = null
        pixels = IntArray(0)
    }

    private fun ensureCapacity(requiredPixels: Int) {
        if (pixels.size >= requiredPixels && buffer != null) return

        val capacity = grow(pixels.size, requiredPixels)
        if (pixels.size < capacity) pixels = IntArray(capacity)
        val requiredBytes = Math.multiplyExact(capacity, RGBA_BYTES)
        buffer = buffer?.let { MemoryUtil.memRealloc(it, requiredBytes) }
            ?: MemoryUtil.memAlloc(requiredBytes)
    }

    private fun grow(current: Int, required: Int): Int {
        var capacity = current.coerceAtLeast(DEFAULT_PIXEL_CAPACITY)
        while (capacity < required) capacity = Math.multiplyExact(capacity, 2)
        return capacity
    }

    private companion object {
        const val RGBA_BYTES = 4
        const val DEFAULT_PIXEL_CAPACITY = 64
    }
}
