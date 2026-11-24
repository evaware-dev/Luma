package sweetie.evaware.luma.backend.blaze3d

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.lwjgl.system.MemoryUtil

internal class StagingBuffer(initialCapacity: Int) : AutoCloseable {
    var buffer: ByteBuffer = allocate(initialCapacity)
        private set
    private var baseAddress: Long = MemoryUtil.memAddress(buffer)
    private var capacity: Int = buffer.capacity()

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
        val grownAddress = MemoryUtil.memAddress(grown)
        if (position > 0) {
            MemoryUtil.memCopy(baseAddress, grownAddress, position.toLong())
        }
        grown.position(position)
        MemoryUtil.memFree(buffer)
        buffer = grown
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
            MemoryUtil.memSet(baseAddress + position, 0, padding.toLong())
        }
        val offset = Math.addExact(position, padding)
        MemoryUtil.memCopy(sourceAddress, baseAddress + offset, bytes.toLong())
        buffer.position(offset + bytes)
        return offset.toLong()
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
            MemoryUtil.memSet(baseAddress + position, 0, padding.toLong())
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
            MemoryUtil.memSet(baseAddress + position, 0, padding.toLong())
        }
        val offset = Math.addExact(position, padding)
        buffer.position(offset)
        return offset.toLong()
    }

    private fun computePadding(position: Int, alignment: Int): Int {
        if ((alignment and (alignment - 1)) == 0) {
            return (alignment - (position and (alignment - 1))) and (alignment - 1)
        }
        val remainder = position % alignment
        return if (remainder == 0) 0 else alignment - remainder
    }

    override fun close() {
        MemoryUtil.memFree(buffer)
    }
}
