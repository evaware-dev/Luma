package sweetie.evaware.luma.minecraft

import com.mojang.blaze3d.pipeline.RenderTarget
import sweetie.evaware.luma.GraphicsBackend
import sweetie.evaware.luma.api.RenderTargetFilter
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.backend.blaze3d.VulkanRenderTarget
import sweetie.evaware.luma.backend.gl.GlRenderTarget
import sweetie.evaware.luma.backend.gl.GlTexture
import sweetie.evaware.luma.framebuffer.FramebufferHandle

object MinecraftRenderTargets {
    @JvmStatic
    @JvmOverloads
    fun borrow(
        target: RenderTarget,
        filter: RenderTargetFilter = RenderTargetFilter.NEAREST
    ): RenderTargetHandle {
        val colorView = requireNotNull(target.colorTextureView) {
            "Render target has no color texture"
        }
        val depthView = target.depthTextureView
        return if (MinecraftRenderPlatform.activeBackend == GraphicsBackend.OPENGL) {
            val width = colorView.getWidth(0)
            val height = colorView.getHeight(0)
            val color = MinecraftTextures.borrow(colorView, filter) as GlTexture
            var depth: GlTexture? = null
            try {
                depth = depthView?.let {
                    MinecraftTextures.borrow(it, RenderTargetFilter.NEAREST) as GlTexture
                }
                GlRenderTarget.borrow(
                    FramebufferHandle.resolve(colorView, depthView),
                    width,
                    height,
                    color,
                    depth
                )
            } catch (failure: Throwable) {
                depth?.close()
                color.close()
                throw failure
            }
        } else {
            VulkanRenderTarget.borrow(colorView, depthView, filter)
        }
    }
}
