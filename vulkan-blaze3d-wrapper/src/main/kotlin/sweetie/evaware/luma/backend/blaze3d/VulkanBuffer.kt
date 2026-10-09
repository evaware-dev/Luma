package sweetie.evaware.luma.backend.blaze3d

import java.nio.ByteBuffer
import sweetie.evaware.luma.backend.blaze3d.preset.PresetCommandEncoder
import sweetie.evaware.luma.backend.blaze3d.preset.PresetGpuBuffer
import sweetie.evaware.luma.backend.blaze3d.preset.PresetGpuBufferSlice
import sweetie.evaware.luma.backend.blaze3d.preset.VulkanPreset
import sweetie.evaware.luma.backend.blaze3d.preset.VulkanPresets

class VulkanBuffer(
    private val label: () -> String,
    private val usage: Int,
    initialCapacity: Int,
    private val preset: VulkanPreset = VulkanPresets.active
) : AutoCloseable {
    var gpuBuffer: PresetGpuBuffer? = null
        private set
    var capacityBytes = initialCapacity
        private set
    private var slices = arrayOfNulls<PresetGpuBufferSlice>(2048)
    private var sliceOffsets = LongArray(2048)
    private var sliceLengths = LongArray(2048)
    private var sliceCursor = 0
    private var cachedFullSlice: PresetGpuBufferSlice? = null

    fun beginFrame() {
        sliceCursor = 0
    }

    fun ensureCapacity(bytes: Int): PresetGpuBuffer {
        val existing = gpuBuffer
        if (existing != null && bytes <= capacityBytes) return existing
        if (existing != null) VulkanResourceRetirement.defer(existing)
        var cap = capacityBytes.coerceAtLeast(256)
        while (cap < bytes) cap = Math.multiplyExact(cap, 2)
        val created = preset.createBuffer(
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

    fun write(encoder: PresetCommandEncoder, data: ByteBuffer) {
        val bytes = data.remaining()
        if (bytes == 0) return
        val buffer = ensureCapacity(bytes)
        encoder.writeToBuffer(slice(buffer, 0, bytes.toLong()), data)
    }

    fun slice(offset: Long, bytes: Long): PresetGpuBufferSlice {
        val buffer = gpuBuffer ?: error("Buffer not allocated")
        return slice(buffer, offset, bytes)
    }

    fun fullSlice(): PresetGpuBufferSlice {
        cachedFullSlice?.let { return it }
        return (gpuBuffer ?: error("Buffer not allocated")).slice().also { cachedFullSlice = it }
    }

    private fun slice(buffer: PresetGpuBuffer, offset: Long, bytes: Long): PresetGpuBufferSlice {
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
