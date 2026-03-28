package sweetie.evaware.luma.framebuffer

import com.mojang.blaze3d.opengl.GlDevice
import com.mojang.blaze3d.opengl.GlTexture
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTextureView

object FramebufferHandle {
    fun resolve(colorTexture: GpuTextureView, depthTexture: GpuTextureView?): Int {
        val directStateAccess = (RenderSystem.getDevice() as GlDevice).directStateAccess()
        return (colorTexture.texture() as GlTexture).getFbo(directStateAccess, depthTexture?.texture())
    }
}
