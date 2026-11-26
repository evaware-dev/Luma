package sweetie.evaware.luma.texture

import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.api.TextureFormat
import java.awt.image.BufferedImage
import java.awt.image.ComponentSampleModel
import java.awt.image.DataBufferByte
import java.awt.image.DataBufferInt
import java.awt.image.SinglePixelPackedSampleModel
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RgbaTransferBuffer(
    initialPixelCapacity: Int = DEFAULT_PIXEL_CAPACITY
) : AutoCloseable {
    private var pixels: IntArray = EMPTY_INT_ARRAY
    private var buffer: ByteBuffer? = allocate(initialPixelCapacity.coerceAtLeast(1) * RGBA_BYTES)

    fun write(image: BufferedImage): ByteBuffer = write(image, TextureFormat.RGBA8)

    fun write(image: BufferedImage, format: TextureFormat): ByteBuffer {
        require(format.supportsBufferedImageUpload) {
            "BufferedImage upload is not supported for format $format; upload raw ByteBuffer data instead"
        }
        val requiredBytes = format.byteSize(image.width, image.height)
        ensureBufferCapacity(requiredBytes)
        val target = requireNotNull(buffer)
        return writePixels(image, target, format, requiredBytes)
    }

    fun write(image: BufferedImage, target: ByteBuffer): ByteBuffer = write(image, target, TextureFormat.RGBA8)

    fun write(image: BufferedImage, target: ByteBuffer, format: TextureFormat): ByteBuffer {
        require(format.supportsBufferedImageUpload) {
            "BufferedImage upload is not supported for format $format; upload raw ByteBuffer data instead"
        }
        val requiredBytes = format.byteSize(image.width, image.height)
        require(target.capacity() >= requiredBytes) {
            "Target buffer capacity ${target.capacity()} is smaller than required $requiredBytes bytes"
        }
        target.order(ByteOrder.nativeOrder())
        return writePixels(image, target, format, requiredBytes)
    }

    override fun close() {
        buffer?.let(MemoryUtil::memFree)
        buffer = null
        pixels = EMPTY_INT_ARRAY
    }

    private fun ensureBufferCapacity(requiredBytes: Int) {
        val current = buffer
        if (current != null && current.capacity() >= requiredBytes) return
        val currentBytes = current?.capacity() ?: 0
        val capacityBytes = grow(currentBytes, requiredBytes)
        buffer = current?.let { MemoryUtil.memRealloc(it, capacityBytes).order(ByteOrder.nativeOrder()) }
            ?: allocate(capacityBytes)
    }

    private fun ensurePixelScratch(requiredPixels: Int): IntArray {
        val current = pixels
        if (current.size >= requiredPixels) return current
        val grown = IntArray(grow(current.size, requiredPixels))
        pixels = grown
        return grown
    }

    private fun grow(current: Int, required: Int): Int {
        var capacity = current.coerceAtLeast(DEFAULT_PIXEL_CAPACITY)
        while (capacity < required) capacity = Math.multiplyExact(capacity, 2)
        return capacity
    }

    private fun allocate(bytes: Int): ByteBuffer = MemoryUtil.memAlloc(bytes).order(ByteOrder.nativeOrder())

    private fun writePixels(
        image: BufferedImage,
        target: ByteBuffer,
        format: TextureFormat,
        requiredBytes: Int
    ): ByteBuffer {
        val width = image.width
        val height = image.height
        val pixelCount = Math.multiplyExact(width, height)
        target.clear()

        if (format == TextureFormat.R8) {
            writeR8Pixels(image, target, width, height, pixelCount)
            target.position(0)
            target.limit(requiredBytes)
            return target
        }

        val raster = image.raster
        val data = raster.dataBuffer
        val model = raster.sampleModel
        val imageType = image.type
        val direct = target.isDirect

        if (
            data is DataBufferInt &&
            model is SinglePixelPackedSampleModel &&
            (imageType == BufferedImage.TYPE_INT_ARGB ||
                imageType == BufferedImage.TYPE_INT_RGB ||
                imageType == BufferedImage.TYPE_INT_BGR)
        ) {
            val source = data.data
            val start = data.offset + model.getOffset(
                raster.minX - raster.sampleModelTranslateX,
                raster.minY - raster.sampleModelTranslateY
            )
            val stride = model.scanlineStride
            if (direct) {
                writeIntDataDirect(source, start, stride, width, height, imageType, format, MemoryUtil.memAddress(target))
            } else {
                writeIntDataBuffer(source, start, stride, width, height, imageType, format, target)
            }
            target.position(0)
            target.limit(requiredBytes)
            return target
        }

        if (
            data is DataBufferByte &&
            model is ComponentSampleModel &&
            (imageType == BufferedImage.TYPE_4BYTE_ABGR || imageType == BufferedImage.TYPE_3BYTE_BGR)
        ) {
            val expectedPixelStride = if (imageType == BufferedImage.TYPE_4BYTE_ABGR) 4 else 3
            val bandOffsets = model.bandOffsets
            val bandsMatch = if (imageType == BufferedImage.TYPE_4BYTE_ABGR) {
                bandOffsets.size == 4 &&
                    bandOffsets[0] == 3 && bandOffsets[1] == 2 && bandOffsets[2] == 1 && bandOffsets[3] == 0
            } else {
                bandOffsets.size == 3 &&
                    bandOffsets[0] == 2 && bandOffsets[1] == 1 && bandOffsets[2] == 0
            }
            if (direct && model.pixelStride == expectedPixelStride && bandsMatch) {
                val source = data.data
                val stride = model.scanlineStride
                val start = data.offset +
                    (raster.minY - raster.sampleModelTranslateY) * stride +
                    (raster.minX - raster.sampleModelTranslateX) * expectedPixelStride
                writeByteDataDirect(source, start, stride, width, height, imageType, format, MemoryUtil.memAddress(target))
                target.position(0)
                target.limit(requiredBytes)
                return target
            }
        }

        // Generic fallback for exotic BufferedImage color models
        val scratch = ensurePixelScratch(pixelCount)
        image.getRGB(0, 0, width, height, scratch, 0, width)
        if (direct) {
            var dst = MemoryUtil.memAddress(target)
            if (format == TextureFormat.RGBA8) {
                for (index in 0 until pixelCount) {
                    MemoryUtil.memPutInt(dst, toNativeRgba(scratch[index]))
                    dst += 4L
                }
            } else {
                for (index in 0 until pixelCount) {
                    val argb = scratch[index]
                    MemoryUtil.memPutByte(dst, ((argb ushr 16) and 0xFF).toByte())
                    MemoryUtil.memPutByte(dst + 1L, ((argb ushr 8) and 0xFF).toByte())
                    dst += 2L
                }
            }
        } else {
            if (format == TextureFormat.RGBA8) {
                for (index in 0 until pixelCount) target.putInt(toNativeRgba(scratch[index]))
            } else {
                for (index in 0 until pixelCount) {
                    val argb = scratch[index]
                    target.put(((argb ushr 16) and 0xFF).toByte())
                    target.put(((argb ushr 8) and 0xFF).toByte())
                }
            }
        }
        target.position(0)
        target.limit(requiredBytes)
        return target
    }

    private fun writeIntDataDirect(
        source: IntArray,
        start: Int,
        stride: Int,
        width: Int,
        height: Int,
        imageType: Int,
        format: TextureFormat,
        dstBase: Long
    ) {
        var dst = dstBase
        if (format == TextureFormat.RGBA8) {
            when (imageType) {
                BufferedImage.TYPE_INT_ARGB -> {
                    for (row in 0 until height) {
                        var offset = start + row * stride
                        val end = offset + width
                        while (offset < end) {
                            MemoryUtil.memPutInt(dst, toNativeRgba(source[offset++]))
                            dst += 4L
                        }
                    }
                }
                BufferedImage.TYPE_INT_RGB -> {
                    for (row in 0 until height) {
                        var offset = start + row * stride
                        val end = offset + width
                        while (offset < end) {
                            MemoryUtil.memPutInt(dst, toNativeRgba(source[offset++] or OPAQUE_ALPHA))
                            dst += 4L
                        }
                    }
                }
                else -> {
                    // TYPE_INT_BGR: 0x00BBGGRR -> little-endian RGBA is 0xFFBBGGRR
                    for (row in 0 until height) {
                        var offset = start + row * stride
                        val end = offset + width
                        while (offset < end) {
                            val bgr = source[offset++]
                            val rgba = bgr or OPAQUE_ALPHA
                            MemoryUtil.memPutInt(dst, if (LITTLE_ENDIAN) rgba else Integer.reverseBytes(rgba))
                            dst += 4L
                        }
                    }
                }
            }
        } else {
            // RG8
            if (imageType == BufferedImage.TYPE_INT_BGR) {
                for (row in 0 until height) {
                    var offset = start + row * stride
                    val end = offset + width
                    while (offset < end) {
                        val bgr = source[offset++]
                        MemoryUtil.memPutByte(dst, (bgr and 0xFF).toByte())
                        MemoryUtil.memPutByte(dst + 1L, ((bgr ushr 8) and 0xFF).toByte())
                        dst += 2L
                    }
                }
            } else {
                for (row in 0 until height) {
                    var offset = start + row * stride
                    val end = offset + width
                    while (offset < end) {
                        val argb = source[offset++]
                        MemoryUtil.memPutByte(dst, ((argb ushr 16) and 0xFF).toByte())
                        MemoryUtil.memPutByte(dst + 1L, ((argb ushr 8) and 0xFF).toByte())
                        dst += 2L
                    }
                }
            }
        }
    }

    private fun writeIntDataBuffer(
        source: IntArray,
        start: Int,
        stride: Int,
        width: Int,
        height: Int,
        imageType: Int,
        format: TextureFormat,
        target: ByteBuffer
    ) {
        if (format == TextureFormat.RGBA8) {
            for (row in 0 until height) {
                var offset = start + row * stride
                val end = offset + width
                while (offset < end) {
                    val p = source[offset++]
                    val nativeRgba = when (imageType) {
                        BufferedImage.TYPE_INT_ARGB -> toNativeRgba(p)
                        BufferedImage.TYPE_INT_RGB -> toNativeRgba(p or OPAQUE_ALPHA)
                        else -> {
                            val rgba = p or OPAQUE_ALPHA
                            if (LITTLE_ENDIAN) rgba else Integer.reverseBytes(rgba)
                        }
                    }
                    target.putInt(nativeRgba)
                }
            }
        } else {
            for (row in 0 until height) {
                var offset = start + row * stride
                val end = offset + width
                while (offset < end) {
                    val p = source[offset++]
                    if (imageType == BufferedImage.TYPE_INT_BGR) {
                        target.put((p and 0xFF).toByte())
                        target.put(((p ushr 8) and 0xFF).toByte())
                    } else {
                        target.put(((p ushr 16) and 0xFF).toByte())
                        target.put(((p ushr 8) and 0xFF).toByte())
                    }
                }
            }
        }
    }

    private fun writeByteDataDirect(
        source: ByteArray,
        start: Int,
        stride: Int,
        width: Int,
        height: Int,
        imageType: Int,
        format: TextureFormat,
        dstBase: Long
    ) {
        var dst = dstBase
        if (imageType == BufferedImage.TYPE_4BYTE_ABGR) {
            if (format == TextureFormat.RGBA8) {
                for (row in 0 until height) {
                    var offset = start + row * stride
                    val end = offset + width * 4
                    while (offset < end) {
                        val a = source[offset]
                        val b = source[offset + 1]
                        val g = source[offset + 2]
                        val r = source[offset + 3]
                        offset += 4
                        MemoryUtil.memPutByte(dst, r)
                        MemoryUtil.memPutByte(dst + 1L, g)
                        MemoryUtil.memPutByte(dst + 2L, b)
                        MemoryUtil.memPutByte(dst + 3L, a)
                        dst += 4L
                    }
                }
            } else {
                for (row in 0 until height) {
                    var offset = start + row * stride
                    val end = offset + width * 4
                    while (offset < end) {
                        MemoryUtil.memPutByte(dst, source[offset + 3])
                        MemoryUtil.memPutByte(dst + 1L, source[offset + 2])
                        offset += 4
                        dst += 2L
                    }
                }
            }
        } else {
            // TYPE_3BYTE_BGR
            if (format == TextureFormat.RGBA8) {
                for (row in 0 until height) {
                    var offset = start + row * stride
                    val end = offset + width * 3
                    while (offset < end) {
                        val b = source[offset]
                        val g = source[offset + 1]
                        val r = source[offset + 2]
                        offset += 3
                        MemoryUtil.memPutByte(dst, r)
                        MemoryUtil.memPutByte(dst + 1L, g)
                        MemoryUtil.memPutByte(dst + 2L, b)
                        MemoryUtil.memPutByte(dst + 3L, 0xFF.toByte())
                        dst += 4L
                    }
                }
            } else {
                for (row in 0 until height) {
                    var offset = start + row * stride
                    val end = offset + width * 3
                    while (offset < end) {
                        MemoryUtil.memPutByte(dst, source[offset + 2])
                        MemoryUtil.memPutByte(dst + 1L, source[offset + 1])
                        offset += 3
                        dst += 2L
                    }
                }
            }
        }
    }

    private fun writeR8Pixels(image: BufferedImage, target: ByteBuffer, width: Int, height: Int, pixelCount: Int) {
        val raster = image.raster
        val data = raster.dataBuffer
        if (
            image.type == BufferedImage.TYPE_BYTE_GRAY &&
            data is DataBufferByte &&
            raster.sampleModel.numBands == 1 &&
            raster.width == width &&
            raster.height == height &&
            raster.minX == 0 &&
            raster.minY == 0 &&
            raster.sampleModelTranslateX == 0 &&
            raster.sampleModelTranslateY == 0 &&
            data.data.size >= data.offset + pixelCount
        ) {
            target.put(data.data, data.offset, pixelCount)
            return
        }
        val scratch = ensurePixelScratch(pixelCount)
        image.getRGB(0, 0, width, height, scratch, 0, width)
        if (target.isDirect) {
            var dst = MemoryUtil.memAddress(target)
            for (index in 0 until pixelCount) {
                MemoryUtil.memPutByte(dst++, ((scratch[index] ushr 16) and 0xFF).toByte())
            }
        } else {
            for (index in 0 until pixelCount) {
                target.put(((scratch[index] ushr 16) and 0xFF).toByte())
            }
        }
    }

    private fun toNativeRgba(argb: Int): Int {
        val rgba = Integer.rotateLeft(argb, 8)
        return if (LITTLE_ENDIAN) Integer.reverseBytes(rgba) else rgba
    }

    private companion object {
        const val RGBA_BYTES = 4
        const val DEFAULT_PIXEL_CAPACITY = 64
        const val OPAQUE_ALPHA = 0xFF000000.toInt()
        val EMPTY_INT_ARRAY = IntArray(0)
        val LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
    }
}
