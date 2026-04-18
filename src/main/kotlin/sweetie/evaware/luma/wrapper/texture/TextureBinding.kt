package sweetie.evaware.luma.wrapper.texture

import com.mojang.blaze3d.textures.GpuTextureView

sealed interface TextureBinding {
    data class Managed(val handle: TextureHandle) : TextureBinding

    data class OpenGl(val textureId: Int) : TextureBinding

    data class Vulkan(val view: GpuTextureView) : TextureBinding
}
