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
    private var slices = arrayOfNulls<GpuBufferSlice>(16)
    private var sliceOffsets = LongArray(16)
    private var sliceLengths = LongArray(16)
    private var sliceCursor = 0
    private var cachedFullSlice: GpuBufferSlice? = null

    fun beginFrame() {
        sliceCursor = 0
    }

    fun ensureCapacity(bytes: Int): GpuBuffer {
        val existing = gpuBuffer
        if (existing != null && bytes <= capacityBytes) return existing
        if (existing != null) VulkanResourceRetirement.defer(existing)
        var cap = capacityBytes.coerceAtLeast(256)
        while (cap < bytes) cap = Math.multiplyExact(cap, 2)
        val created = RenderSystem.getDevice().createBuffer(
            label,
            usage,
            cap.toLong()
        )
        gpuBuffer = created
        capacityBytes = cap
        slices.fill(null)
        sliceCursor = 0
        cachedFullSlice = null
        return created
    }

    fun write(encoder: CommandEncoder, data: ByteBuffer) {
        val bytes = data.remaining()
        if (bytes == 0) return
        val buffer = ensureCapacity(bytes)
        encoder.writeToBuffer(slice(buffer, 0, bytes.toLong()), data)
    }

    fun slice(offset: Long, bytes: Long): GpuBufferSlice {
        val buffer = gpuBuffer ?: error("Buffer not allocated")
        return slice(buffer, offset, bytes)
    }

    fun fullSlice(): GpuBufferSlice {
        cachedFullSlice?.let { return it }
        return (gpuBuffer ?: error("Buffer not allocated")).slice().also { cachedFullSlice = it }
    }

    private fun slice(buffer: GpuBuffer, offset: Long, bytes: Long): GpuBufferSlice {
        if (sliceCursor == slices.size) {
            val capacity = slices.size shl 1
            slices = slices.copyOf(capacity)
            sliceOffsets = sliceOffsets.copyOf(capacity)
            sliceLengths = sliceLengths.copyOf(capacity)
        }
        val index = sliceCursor++
        val cached = slices[index]
        if (cached != null && sliceOffsets[index] == offset && sliceLengths[index] == bytes) return cached
        val created = buffer.slice(offset, bytes)
        slices[index] = created
        sliceOffsets[index] = offset
        sliceLengths[index] = bytes
        return created
    }

    override fun close() {
        gpuBuffer?.let(VulkanResourceRetirement::defer)
        gpuBuffer = null
        slices.fill(null)
        sliceCursor = 0
        cachedFullSlice = null
    }
}
