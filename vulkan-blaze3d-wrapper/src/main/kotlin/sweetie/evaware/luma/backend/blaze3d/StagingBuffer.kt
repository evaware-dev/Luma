package sweetie.evaware.luma.backend.blaze3d

import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class StagingBuffer(initialCapacity: Int) {
    var buffer: ByteBuffer = allocate(initialCapacity)
        private set

    private fun allocate(capacity: Int): ByteBuffer =
        ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder())

    fun reset() {
        buffer.clear()
    }

    fun flip() {
        buffer.flip()
    }

    private fun ensure(additionalBytes: Int) {
        val required = buffer.position() + additionalBytes
        if (required <= buffer.capacity()) return

        var newCapacity = buffer.capacity() * 2
        while (newCapacity < required) newCapacity *= 2

        val grown = allocate(newCapacity)
        val keptPosition = buffer.position()
        buffer.flip()
        grown.put(buffer)
        grown.position(keptPosition)
        buffer = grown
    }

    fun appendFromAddress(sourceAddress: Long, bytes: Int): Long {
        ensure(bytes)
        val offset = buffer.position().toLong()
        MemoryUtil.memCopy(sourceAddress, MemoryUtil.memAddress(buffer) + buffer.position(), bytes.toLong())
        buffer.position(buffer.position() + bytes)
        return offset
    }

    fun append(source: ByteBuffer): Long {
        ensure(source.remaining())
        val offset = buffer.position().toLong()
        buffer.put(source)
        return offset
    }
}
