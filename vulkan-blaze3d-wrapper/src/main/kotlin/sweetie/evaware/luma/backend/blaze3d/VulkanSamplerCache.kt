package sweetie.evaware.luma.backend.blaze3d

import sweetie.evaware.luma.backend.blaze3d.preset.PresetAddressMode
import sweetie.evaware.luma.backend.blaze3d.preset.PresetFilterMode
import sweetie.evaware.luma.backend.blaze3d.preset.PresetGpuSampler
import sweetie.evaware.luma.backend.blaze3d.preset.VulkanPreset

internal data class VulkanSamplerDescriptor(
    val addressU: PresetAddressMode,
    val addressV: PresetAddressMode,
    val minFilter: PresetFilterMode,
    val magFilter: PresetFilterMode,
    val maxAnisotropy: Int = 1
) {
    companion object {
        fun clamp(filter: PresetFilterMode) = VulkanSamplerDescriptor(
            PresetAddressMode.CLAMP_TO_EDGE,
            PresetAddressMode.CLAMP_TO_EDGE,
            filter,
            filter
        )
    }
}

internal class VulkanSamplerCache(
    private val preset: VulkanPreset,
    private val retirement: VulkanRetirementQueue
) : AutoCloseable {
    private val samplers = HashMap<VulkanSamplerDescriptor, PresetGpuSampler>()

    fun get(descriptor: VulkanSamplerDescriptor): PresetGpuSampler = samplers.getOrPut(descriptor) {
        preset.createSampler(
            descriptor.addressU,
            descriptor.minFilter,
            descriptor.maxAnisotropy
        )
    }

    override fun close() {
        for (sampler in samplers.values) {
            retirement.defer(sampler)
        }
        samplers.clear()
    }
}
