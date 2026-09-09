package sweetie.evaware.luma.backend.blaze3d

import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class StagingBuffer(initialCapacity: Int) : AutoCloseable {
    var buffer: ByteBuffer = allocate(initialCapacity)
        private set

    private fun allocate(capacity: Int): ByteBuffer =
        MemoryUtil.memAlloc(capacity).order(ByteOrder.nativeOrder())

    fun reset() {
        buffer.clear()
    }

    fun flip() {
        buffer.flip()
    }

    private fun ensure(additionalBytes: Int) {
        val required = buffer.position() + additionalBytes
        if (required <= buffer.capacity()) return

        var newCapacity = Math.multiplyExact(buffer.capacity(), 2)
        while (newCapacity < required) newCapacity = Math.multiplyExact(newCapacity, 2)

        val grown = allocate(newCapacity)
        val keptPosition = buffer.position()
        buffer.flip()
        grown.put(buffer)
        grown.position(keptPosition)
        MemoryUtil.memFree(buffer)
        buffer = grown
    }

    fun appendFromAddress(sourceAddress: Long, bytes: Int): Long {
        ensure(bytes)
        val offset = buffer.position().toLong()
        MemoryUtil.memCopy(sourceAddress, MemoryUtil.memAddress(buffer), bytes.toLong())
        buffer.position(buffer.position() + bytes)
        return offset
    }

    fun append(source: ByteBuffer): Long {
        ensure(source.remaining())
        val offset = buffer.position().toLong()
        buffer.put(source)
        return offset
    }

    fun appendAligned(source: ByteBuffer, alignment: Int): Long {
        require(alignment > 0) { "Alignment must be positive" }
        val position = buffer.position()
        val remainder = position % alignment
        val padding = if (remainder == 0) 0 else alignment - remainder
        ensure(Math.addExact(padding, source.remaining()))
        buffer.position(Math.addExact(position, padding))
        val offset = buffer.position().toLong()
        buffer.put(source)
        return offset
    }

    override fun close() {
        MemoryUtil.memFree(buffer)
    }

}
