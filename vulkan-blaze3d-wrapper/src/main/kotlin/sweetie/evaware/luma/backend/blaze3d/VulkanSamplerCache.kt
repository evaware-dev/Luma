package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.systems.GpuDevice
import com.mojang.blaze3d.textures.AddressMode
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import java.util.OptionalDouble

internal data class VulkanSamplerDescriptor(
    val addressU: AddressMode,
    val addressV: AddressMode,
    val minFilter: FilterMode,
    val magFilter: FilterMode,
    val maxAnisotropy: Int = 1
) {
    companion object {
        fun clamp(filter: FilterMode) = VulkanSamplerDescriptor(
            AddressMode.CLAMP_TO_EDGE,
            AddressMode.CLAMP_TO_EDGE,
            filter,
            filter
        )
    }
}

internal class VulkanSamplerCache(
    private val device: GpuDevice
) : AutoCloseable {
    private val samplers = HashMap<VulkanSamplerDescriptor, GpuSampler>()

    fun get(descriptor: VulkanSamplerDescriptor): GpuSampler = samplers.getOrPut(descriptor) {
        device.createSampler(
            descriptor.addressU,
            descriptor.addressV,
            descriptor.minFilter,
            descriptor.magFilter,
            descriptor.maxAnisotropy,
            OptionalDouble.empty()
        )
    }

    override fun close() {
        samplers.values.forEach(VulkanResourceRetirement::defer)
        samplers.clear()
    }
}
