package sweetie.evaware.luma.framebuffer

import com.mojang.blaze3d.opengl.GlTexture
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.Minecraft
import sweetie.evaware.luma.wrapper.texture.TextureBinding

sealed interface WrapperFramebufferSnapshot {
    val colorView: GpuTextureView
    val depthView: GpuTextureView?
    val width: Int
    val height: Int

    fun textureBinding(): TextureBinding
}

data class OpenGlFramebufferSnapshot(
    override val colorView: GpuTextureView,
    override val depthView: GpuTextureView?,
    override val width: Int,
    override val height: Int,
    val textureId: Int
) : WrapperFramebufferSnapshot {
    override fun textureBinding() = TextureBinding.OpenGl(textureId)
}

data class VulkanFramebufferSnapshot(
    override val colorView: GpuTextureView,
    override val depthView: GpuTextureView?,
    override val width: Int,
    override val height: Int
) : WrapperFramebufferSnapshot {
    override fun textureBinding() = TextureBinding.Vulkan(colorView)
}

object FramebufferSnapshots {
    fun current(): WrapperFramebufferSnapshot? {
        val target = Minecraft.getInstance().mainRenderTarget
        val colorView = RenderSystem.outputColorTextureOverride ?: target.colorTextureView ?: return null
        val depthView = RenderSystem.outputDepthTextureOverride ?: target.depthTextureView
        val glTexture = colorView.texture() as? GlTexture
        return if (glTexture != null) {
            OpenGlFramebufferSnapshot(colorView, depthView, colorView.getWidth(0), colorView.getHeight(0), glTexture.glId())
        } else {
            VulkanFramebufferSnapshot(colorView, depthView, colorView.getWidth(0), colorView.getHeight(0))
        }
    }
}
