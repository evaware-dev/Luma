package sweetie.evaware.luma.wrapper.texture

import sweetie.evaware.luma.wrapper.LumaMetadata

data class WrapperTextureBinding(
    val openGl: OpenGlTextureBinding = OpenGlTextureBinding(),
    val vulkan: VulkanTextureBinding = VulkanTextureBinding()
)

data class OpenGlTextureBinding(
    val uniform: String = LumaMetadata.defaultOpenGlTextureUniform
)

data class VulkanTextureBinding(
    val sampler: String = LumaMetadata.defaultVulkanTextureSampler
)
