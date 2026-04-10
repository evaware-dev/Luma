package sweetie.evaware.luma.platform.minecraft.opengl

import com.mojang.blaze3d.opengl.GlConst
import com.mojang.blaze3d.opengl.GlDevice
import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.opengl.GlTexture
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.Minecraft

internal object MinecraftOpenGlFramebufferBridge {
    fun bindMainFramebuffer() {
        val target = Minecraft.getInstance().mainRenderTarget
        val colorTexture = target.getColorTextureView() ?: return
        val depthTexture = target.getDepthTextureView()
        GlStateManager._glBindFramebuffer(
            GlConst.GL_FRAMEBUFFER,
            resolve(colorTexture, depthTexture)
        )
        GlStateManager._viewport(0, 0, colorTexture.getWidth(0), colorTexture.getHeight(0))
    }

    fun resolve(colorTexture: GpuTextureView, depthTexture: GpuTextureView?): Int {
        val backend = RenderSystem.getDevice().backend as? GlDevice
            ?: error("OpenGL framebuffer handle is unavailable on a non-OpenGL backend")
        val colorHandle = colorTexture.texture() as? GlTexture
            ?: error("Expected GlTexture for the OpenGL color target")
        return colorHandle.getFbo(backend.directStateAccess(), depthTexture?.texture())
    }
}
