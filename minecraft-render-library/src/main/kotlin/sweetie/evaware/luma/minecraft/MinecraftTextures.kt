package sweetie.evaware.luma.minecraft

import com.mojang.blaze3d.opengl.GlTexture as MinecraftGlTexture
import com.mojang.blaze3d.textures.GpuTextureView
import sweetie.evaware.luma.GraphicsBackend
import sweetie.evaware.luma.api.RenderTargetFilter
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.backend.blaze3d.VulkanTexture
import sweetie.evaware.luma.backend.gl.GlTexture

object MinecraftTextures {
    @JvmStatic
    fun borrow(view: GpuTextureView, filter: RenderTargetFilter): TextureHandle {
        return if (MinecraftRenderPlatform.activeBackend == GraphicsBackend.OPENGL) {
            val texture = view.texture() as MinecraftGlTexture
            GlTexture.borrow(texture.glId(), view.getWidth(0), view.getHeight(0), filter)
        } else {
            VulkanTexture.borrow(view, filter)
        }
    }
}
