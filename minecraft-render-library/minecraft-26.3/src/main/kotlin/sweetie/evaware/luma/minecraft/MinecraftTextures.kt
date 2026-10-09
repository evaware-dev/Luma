package sweetie.evaware.luma.minecraft

import com.mojang.renderpearl.api.textures.GpuTextureView
import sweetie.evaware.luma.GraphicsBackend
import sweetie.evaware.luma.api.RenderTargetFilter
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.backend.blaze3d.VulkanTexture
import sweetie.evaware.luma.backend.gl.GlTexture

object MinecraftTextures {
    @JvmStatic
    fun borrow(view: GpuTextureView, filter: RenderTargetFilter): TextureHandle {
        return if (MinecraftRenderPlatform.activeBackend == GraphicsBackend.OPENGL) {
            val texture = view.texture() as com.mojang.renderpearl.backend.opengl.GlTexture
            GlTexture.borrow(texture.glId(), view.getWidth(0), view.getHeight(0), filter)
        } else {
            VulkanTexture.borrow(view, filter)
        }
    }
}
