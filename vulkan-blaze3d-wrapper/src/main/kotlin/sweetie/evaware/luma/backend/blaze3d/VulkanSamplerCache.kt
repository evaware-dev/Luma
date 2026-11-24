package sweetie.evaware.luma.backend.blaze3d

import sweetie.evaware.luma.api.SamplerState
import sweetie.evaware.luma.api.TextureFilter
import sweetie.evaware.luma.api.TextureWrap
import sweetie.evaware.luma.backend.blaze3d.preset.PresetAddressMode
import sweetie.evaware.luma.backend.blaze3d.preset.PresetFilterMode
import sweetie.evaware.luma.backend.blaze3d.preset.PresetGpuSampler
import sweetie.evaware.luma.backend.blaze3d.preset.VulkanPreset

internal class VulkanSamplerCache(
    private val preset: VulkanPreset,
    private val retirement: VulkanRetirementQueue
) : AutoCloseable {
    private val samplers = HashMap<SamplerState, PresetGpuSampler>()

    fun get(state: SamplerState): PresetGpuSampler = samplers.getOrPut(state) {
        preset.createSampler(
            addressU = state.wrapU.toPreset(),
            addressV = state.wrapV.toPreset(),
            minFilter = state.minFilter.toPreset(),
            magFilter = state.magFilter.toPreset(),
            maxAnisotropy = state.maxAnisotropy,
            maxLod = state.maxLod
        )
    }

    override fun close() {
        for (sampler in samplers.values) {
            retirement.defer(sampler)
        }
        samplers.clear()
    }

    companion object {
        internal fun TextureWrap.toPreset(): PresetAddressMode = when (this) {
            TextureWrap.CLAMP_TO_EDGE -> PresetAddressMode.CLAMP_TO_EDGE
            TextureWrap.REPEAT -> PresetAddressMode.REPEAT
        }

        internal fun TextureFilter.toPreset(): PresetFilterMode = when (this) {
            TextureFilter.NEAREST -> PresetFilterMode.NEAREST
            TextureFilter.LINEAR -> PresetFilterMode.LINEAR
        }
    }
}
