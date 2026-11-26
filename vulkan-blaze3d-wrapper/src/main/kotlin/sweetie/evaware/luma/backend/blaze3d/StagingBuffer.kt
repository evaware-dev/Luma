package sweetie.evaware.luma.backend.blaze3d

import java.lang.foreign.MemorySegment
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.vertex.VertexCursor

internal class StagingBuffer(initialCapacity: Int) : AutoCloseable {
    var buffer: ByteBuffer = allocate(initialCapacity)
        private set
    private var segment: MemorySegment = MemorySegment.ofBuffer(buffer)
    internal var baseAddress: Long = MemoryUtil.memAddress(buffer)
        private set
    private var capacity: Int = buffer.capacity()

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

    fun beginDirectAligned(bytes: Int, alignment: Int, cursor: VertexCursor) {
        require(bytes >= 0) { "Byte count must not be negative" }
        require(alignment > 0) { "Alignment must be positive" }
        val position = buffer.position()
        val padding = computePadding(position, alignment)
        val total = Math.addExact(padding, bytes)
        ensure(position, total)
        if (padding > 0) {
            zeroBytes(baseAddress + position, padding)
        }
        val offset = Math.addExact(position, padding)
        cursor.init(
            baseAddress = baseAddress + offset,
            byteLength = bytes.toLong(),
            strideBytes = alignment,
            token = offset.toLong(),
            segment = segment,
            segmentBaseAddress = baseAddress
        )
    }

    fun commitDirect(offset: Long, bytes: Int) {
        buffer.position(offset.toInt() + bytes)
    }

    fun appendBytes(source: ByteBuffer, bytes: Int): Long {
        val position = buffer.position()
        ensure(position, bytes)
        if (source.isDirect) {
            val srcSeg = resolveByteSegment(source)
            MemorySegment.copy(srcSeg, source.position().toLong(), segment, position.toLong(), bytes.toLong())
        } else {
            val srcPos = source.position()
            var dst = baseAddress + position
            for (i in 0 until bytes) {
                MemoryUtil.memPutByte(dst++, source.get(srcPos + i))
            }
        }
        buffer.position(position + bytes)
        return position.toLong()
    }

    fun beginAlignedWrite(maximumBytes: Int, alignment: Int): Long {
        require(maximumBytes >= 0) { "Maximum byte count must not be negative" }
        require(alignment > 0) { "Alignment must be positive" }
        val position = buffer.position()
        val padding = computePadding(position, alignment)
        ensure(position, Math.addExact(padding, maximumBytes))
        if (padding > 0) {
            zeroBytes(baseAddress + position, padding)
        }
        val offset = Math.addExact(position, padding)
        buffer.position(offset)
        return offset.toLong()
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

    private fun zeroBytes(address: Long, length: Int) {
        var pos = address
        val end = address + length
        while (pos + 8L <= end) {
            MemoryUtil.memPutLong(pos, 0L)
            pos += 8L
        }
        while (pos < end) {
            MemoryUtil.memPutByte(pos++, 0.toByte())
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
        srcByteBuffers.fill(null)
        srcByteSegments.fill(null)
        MemoryUtil.memFree(buffer)
    }

    private companion object {
        const val SRC_CACHE_SIZE = 4
    }
}
