package sweetie.evaware.luma.backend.blaze3d

import sweetie.evaware.luma.api.BufferUsage
import sweetie.evaware.luma.api.IndexBufferHandle
import sweetie.evaware.luma.api.IndexType
import sweetie.evaware.luma.api.VertexBufferHandle
import sweetie.evaware.luma.backend.blaze3d.preset.PresetGpuBuffer
import sweetie.evaware.luma.backend.blaze3d.preset.PresetGpuBufferSlice
import sweetie.evaware.luma.backend.blaze3d.preset.VulkanPreset
import sweetie.evaware.luma.backend.blaze3d.preset.VulkanPresets

internal sealed class VulkanUserBuffer(
    val sizeBytes: Long,
    usageFlag: Int,
    usage: BufferUsage,
    preset: VulkanPreset = VulkanPresets.active
) : AutoCloseable {
    val gpuBuffer: PresetGpuBuffer = preset.createBuffer(
        { "Luma user buffer" },
        usageFlag or 8 or usage.hint,
        sizeBytes
    )
    private var closed = false
    private var cachedSlice: PresetGpuBufferSlice? = null
    private var cachedOffset = -1L
    private var cachedLength = -1L

    fun requireOpen() = check(!closed) { "Buffer is closed" }

    fun slice(offset: Long, length: Long): PresetGpuBufferSlice {
        val cached = cachedSlice
        if (cached != null && cachedOffset == offset && cachedLength == length) return cached
        return gpuBuffer.slice(offset, length).also {
            cachedSlice = it
            cachedOffset = offset
            cachedLength = length
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        cachedSlice = null
        VulkanResourceRetirement.defer(gpuBuffer)
    }
}

internal class VulkanVertexBuffer(
    sizeBytes: Long,
    usage: BufferUsage,
    preset: VulkanPreset = VulkanPresets.active
) : VulkanUserBuffer(sizeBytes, 32, usage, preset), VertexBufferHandle

internal class VulkanIndexBuffer(
    sizeBytes: Long,
    override val indexType: IndexType,
    usage: BufferUsage,
    preset: VulkanPreset = VulkanPresets.active
) : VulkanUserBuffer(sizeBytes, 64, usage, preset), IndexBufferHandle

private val BufferUsage.hint: Int
    get() = when (this) {
        BufferUsage.STATIC -> 0
        BufferUsage.DYNAMIC, BufferUsage.STREAM -> 4
    }
