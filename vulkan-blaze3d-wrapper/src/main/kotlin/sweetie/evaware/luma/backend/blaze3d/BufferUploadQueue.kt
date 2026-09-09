package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.systems.CommandEncoder
import java.nio.ByteBuffer
import org.lwjgl.system.MemoryUtil

internal class BufferUploadQueue(initialCapacity: Int) : AutoCloseable {
    private class Upload {
        var buffer: VulkanUserBuffer? = null
        var targetOffset = 0L
        var sourceOffset = 0
        var size = 0
    }

    private val staging = StagingBuffer(initialCapacity)
    private val uploads = ArrayList<Upload>()
    private var size = 0

    fun beginFrame() {
        staging.reset()
        size = 0
    }

    fun add(buffer: VulkanUserBuffer, targetOffset: Long, data: ByteBuffer) {
        buffer.requireOpen()
        val bytes = data.remaining()
        require(targetOffset >= 0L && targetOffset + bytes <= buffer.sizeBytes) { "Buffer update is out of bounds" }
        val upload = if (size < uploads.size) uploads[size] else Upload().also(uploads::add)
        upload.buffer = buffer
        upload.targetOffset = targetOffset
        upload.sourceOffset = staging.appendFromAddress(MemoryUtil.memAddress(data), bytes).toInt()
        upload.size = bytes
        size++
    }

    fun hasPending(): Boolean = size > 0

    fun record(encoder: CommandEncoder) {
        if (size == 0) return
        staging.flip()
        val source = staging.buffer
        for (index in 0 until size) {
            val upload = uploads[index]
            source.limit(upload.sourceOffset + upload.size)
            source.position(upload.sourceOffset)
            encoder.writeToBuffer(
                upload.buffer!!.slice(upload.targetOffset, upload.size.toLong()),
                source
            )
            upload.buffer = null
        }
        size = 0
    }

    override fun close() {
        for (index in uploads.indices) uploads[index].buffer = null
        size = 0
        staging.close()
    }
}
