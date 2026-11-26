package sweetie.evaware.luma.backend.blaze3d

import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import org.lwjgl.system.MemoryUtil

internal class StagingBuffer(initialCapacity: Int) : AutoCloseable {
    var buffer: ByteBuffer = allocate(initialCapacity)
        private set
    private var segment: MemorySegment = MemorySegment.ofBuffer(buffer)
    private var baseAddress: Long = MemoryUtil.memAddress(buffer)
    private var capacity: Int = buffer.capacity()

    private val srcFloatBuffers = arrayOfNulls<FloatBuffer>(SRC_CACHE_SIZE)
    private val srcFloatSegments = arrayOfNulls<MemorySegment>(SRC_CACHE_SIZE)
    private var srcFloatNextSlot = 0
    private val srcByteBuffers = arrayOfNulls<ByteBuffer>(SRC_CACHE_SIZE)
    private val srcByteSegments = arrayOfNulls<MemorySegment>(SRC_CACHE_SIZE)
    private var srcByteNextSlot = 0

    private fun allocate(capacity: Int): ByteBuffer =
        MemoryUtil.memAlloc(capacity).order(ByteOrder.nativeOrder())

    fun reset() {
        buffer.clear()
    }

    fun flip() {
        buffer.flip()
    }

    private fun ensure(position: Int, additionalBytes: Int) {
        val required = position + additionalBytes
        if (required <= capacity) return

        var newCapacity = Math.multiplyExact(capacity, 2)
        while (newCapacity < required) newCapacity = Math.multiplyExact(newCapacity, 2)

        val grown = allocate(newCapacity)
        val grownSegment = MemorySegment.ofBuffer(grown)
        val grownAddress = MemoryUtil.memAddress(grown)
        if (position > 0) {
            MemorySegment.copy(segment, 0L, grownSegment, 0L, position.toLong())
        }
        grown.position(position)
        MemoryUtil.memFree(buffer)
        buffer = grown
        segment = grownSegment
        baseAddress = grownAddress
        capacity = newCapacity
    }

    fun appendFromAddress(sourceAddress: Long, bytes: Int): Long {
        val position = buffer.position()
        ensure(position, bytes)
        MemoryUtil.memCopy(sourceAddress, baseAddress + position, bytes.toLong())
        buffer.position(position + bytes)
        return position.toLong()
    }

    fun appendFromAddressAligned(sourceAddress: Long, bytes: Int, alignment: Int): Long {
        require(alignment > 0) { "Alignment must be positive" }
        val position = buffer.position()
        val padding = computePadding(position, alignment)
        val total = Math.addExact(padding, bytes)
        ensure(position, total)
        if (padding > 0) {
            zeroBytes(segment, position.toLong(), padding)
        }
        val offset = Math.addExact(position, padding)
        MemoryUtil.memCopy(sourceAddress, baseAddress + offset, bytes.toLong())
        buffer.position(offset + bytes)
        return offset.toLong()
    }

    fun appendFloatsAligned(source: FloatBuffer, bytes: Int, alignment: Int): Long {
        require(alignment > 0) { "Alignment must be positive" }
        val position = buffer.position()
        val padding = computePadding(position, alignment)
        val total = Math.addExact(padding, bytes)
        ensure(position, total)
        if (padding > 0) {
            zeroBytes(segment, position.toLong(), padding)
        }
        val offset = Math.addExact(position, padding)
        if (source.isDirect) {
            val srcSeg = resolveFloatSegment(source)
            MemorySegment.copy(srcSeg, source.position().toLong() shl 2, segment, offset.toLong(), bytes.toLong())
        } else {
            val floatCount = bytes ushr 2
            val srcPos = source.position()
            var dst = offset.toLong()
            for (i in 0 until floatCount) {
                segment.set(ValueLayout.JAVA_FLOAT_UNALIGNED, dst, source.get(srcPos + i))
                dst += Float.SIZE_BYTES
            }
        }
        buffer.position(offset + bytes)
        return offset.toLong()
    }

    fun appendSegmentAligned(source: MemorySegment, sourceOffset: Long, bytes: Int, alignment: Int): Long {
        require(alignment > 0) { "Alignment must be positive" }
        val position = buffer.position()
        val padding = computePadding(position, alignment)
        val total = Math.addExact(padding, bytes)
        ensure(position, total)
        if (padding > 0) {
            zeroBytes(segment, position.toLong(), padding)
        }
        val offset = Math.addExact(position, padding)
        MemorySegment.copy(source, sourceOffset, segment, offset.toLong(), bytes.toLong())
        buffer.position(offset + bytes)
        return offset.toLong()
    }

    fun appendBytes(source: ByteBuffer, bytes: Int): Long {
        val position = buffer.position()
        ensure(position, bytes)
        if (source.isDirect) {
            val srcSeg = resolveByteSegment(source)
            MemorySegment.copy(srcSeg, source.position().toLong(), segment, position.toLong(), bytes.toLong())
        } else {
            val srcPos = source.position()
            var dst = position.toLong()
            for (i in 0 until bytes) {
                segment.set(ValueLayout.JAVA_BYTE, dst++, source.get(srcPos + i))
            }
        }
        buffer.position(position + bytes)
        return position.toLong()
    }

    fun append(source: ByteBuffer): Long {
        val position = buffer.position()
        ensure(position, source.remaining())
        buffer.put(source)
        return position.toLong()
    }

    fun appendAligned(source: ByteBuffer, alignment: Int): Long {
        require(alignment > 0) { "Alignment must be positive" }
        val position = buffer.position()
        val padding = computePadding(position, alignment)
        ensure(position, Math.addExact(padding, source.remaining()))
        if (padding > 0) {
            zeroBytes(segment, position.toLong(), padding)
        }
        val offset = Math.addExact(position, padding)
        buffer.position(offset)
        buffer.put(source)
        return offset.toLong()
    }

    fun beginAlignedWrite(maximumBytes: Int, alignment: Int): Long {
        require(maximumBytes >= 0) { "Maximum byte count must not be negative" }
        require(alignment > 0) { "Alignment must be positive" }
        val position = buffer.position()
        val padding = computePadding(position, alignment)
        ensure(position, Math.addExact(padding, maximumBytes))
        if (padding > 0) {
            zeroBytes(segment, position.toLong(), padding)
        }
        val offset = Math.addExact(position, padding)
        buffer.position(offset)
        return offset.toLong()
    }

    private fun resolveFloatSegment(source: FloatBuffer): MemorySegment {
        val buffers = srcFloatBuffers
        for (i in 0 until SRC_CACHE_SIZE) {
            if (buffers[i] === source) return srcFloatSegments[i]!!
        }
        val savedPos = source.position()
        val savedLim = source.limit()
        source.clear()
        val seg = MemorySegment.ofBuffer(source)
        source.limit(savedLim)
        source.position(savedPos)
        val slot = srcFloatNextSlot
        buffers[slot] = source
        srcFloatSegments[slot] = seg
        srcFloatNextSlot = (slot + 1) and (SRC_CACHE_SIZE - 1)
        return seg
    }

    private fun resolveByteSegment(source: ByteBuffer): MemorySegment {
        val buffers = srcByteBuffers
        for (i in 0 until SRC_CACHE_SIZE) {
            if (buffers[i] === source) return srcByteSegments[i]!!
        }
        val savedPos = source.position()
        val savedLim = source.limit()
        source.clear()
        val seg = MemorySegment.ofBuffer(source)
        source.limit(savedLim)
        source.position(savedPos)
        val slot = srcByteNextSlot
        buffers[slot] = source
        srcByteSegments[slot] = seg
        srcByteNextSlot = (slot + 1) and (SRC_CACHE_SIZE - 1)
        return seg
    }

    private fun zeroBytes(seg: MemorySegment, offset: Long, length: Int) {
        var pos = offset
        val end = offset + length
        while (pos + 8L <= end) {
            seg.set(ValueLayout.JAVA_LONG_UNALIGNED, pos, 0L)
            pos += 8L
        }
        while (pos < end) {
            seg.set(ValueLayout.JAVA_BYTE, pos++, 0.toByte())
        }
    }

    private fun computePadding(position: Int, alignment: Int): Int {
        if ((alignment and (alignment - 1)) == 0) {
            return (alignment - (position and (alignment - 1))) and (alignment - 1)
        }
        val remainder = position % alignment
        return if (remainder == 0) 0 else alignment - remainder
    }

    override fun close() {
        srcFloatBuffers.fill(null)
        srcFloatSegments.fill(null)
        srcByteBuffers.fill(null)
        srcByteSegments.fill(null)
        MemoryUtil.memFree(buffer)
    }

    private companion object {
        const val SRC_CACHE_SIZE = 4
    }
}
