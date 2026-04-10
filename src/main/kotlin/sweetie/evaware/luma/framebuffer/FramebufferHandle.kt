package sweetie.evaware.luma.framebuffer

import com.mojang.blaze3d.textures.GpuTextureView
import sweetie.evaware.luma.platform.minecraft.opengl.MinecraftOpenGlFramebufferBridge

object FramebufferHandle {
    fun resolve(colorTexture: GpuTextureView, depthTexture: GpuTextureView?) =
        MinecraftOpenGlFramebufferBridge.resolve(colorTexture, depthTexture)
}
