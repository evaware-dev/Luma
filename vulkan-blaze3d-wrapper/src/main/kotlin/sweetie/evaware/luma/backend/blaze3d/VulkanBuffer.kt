package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.CommandEncoder
import com.mojang.blaze3d.systems.RenderSystem
import java.nio.ByteBuffer

class VulkanBuffer(
    private val label: () -> String,
    private val usage: Int,
    initialCapacity: Int
) : AutoCloseable {
    var gpuBuffer: GpuBuffer? = null
        private set
    var capacityBytes = initialCapacity
        private set

    fun ensureCapacity(bytes: Int): GpuBuffer {
        val existing = gpuBuffer
        if (existing != null && bytes <= capacityBytes) return existing
        existing?.close()
        var cap = capacityBytes.coerceAtLeast(256)
        while (cap < bytes) cap = cap shl 1
        val created = RenderSystem.getDevice().createBuffer(
            label,
            usage,
            cap.toLong()
        )
        gpuBuffer = created
        capacityBytes = cap
        return created
    }

    fun write(encoder: CommandEncoder, data: ByteBuffer) {
        val bytes = data.remaining()
        val buffer = ensureCapacity(bytes)
        encoder.writeToBuffer(buffer.slice(0, bytes.toLong()), data)
    }

    fun slice(offset: Long, bytes: Long): GpuBufferSlice {
        val buffer = gpuBuffer ?: error("Buffer not allocated")
        return buffer.slice(offset, bytes)
    }

    override fun close() {
        gpuBuffer?.close()
        gpuBuffer = null
    }
}
