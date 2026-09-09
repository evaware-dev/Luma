package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderSystem
import sweetie.evaware.luma.api.BufferUsage
import sweetie.evaware.luma.api.IndexBufferHandle
import sweetie.evaware.luma.api.IndexType
import sweetie.evaware.luma.api.VertexBufferHandle

internal sealed class VulkanUserBuffer(
    val sizeBytes: Long,
    usageFlag: Int,
    usage: BufferUsage
) : AutoCloseable {
    val gpuBuffer: GpuBuffer = RenderSystem.getDevice().createBuffer(
        { "Luma user buffer" },
        usageFlag or GpuBuffer.USAGE_COPY_DST or usage.hint,
        sizeBytes
    )
    private var closed = false
    private var cachedSlice: GpuBufferSlice? = null
    private var cachedOffset = -1L
    private var cachedLength = -1L

    fun requireOpen() = check(!closed) { "Buffer is closed" }

    fun slice(offset: Long, length: Long): GpuBufferSlice {
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

internal class VulkanVertexBuffer(sizeBytes: Long, usage: BufferUsage) :
    VulkanUserBuffer(sizeBytes, GpuBuffer.USAGE_VERTEX, usage), VertexBufferHandle

internal class VulkanIndexBuffer(
    sizeBytes: Long,
    override val indexType: IndexType,
    usage: BufferUsage
) : VulkanUserBuffer(sizeBytes, GpuBuffer.USAGE_INDEX, usage), IndexBufferHandle

private val BufferUsage.hint: Int
    get() = when (this) {
        BufferUsage.STATIC -> 0
        BufferUsage.DYNAMIC, BufferUsage.STREAM -> GpuBuffer.USAGE_HINT_CLIENT_STORAGE
    }
