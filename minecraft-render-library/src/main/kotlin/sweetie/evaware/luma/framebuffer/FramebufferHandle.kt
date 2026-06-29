package sweetie.evaware.luma.framebuffer

import com.mojang.blaze3d.opengl.FrameBufferAttachment
import com.mojang.blaze3d.opengl.GlDevice
import com.mojang.blaze3d.opengl.GlTexture
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTextureView

object FramebufferHandle {
    fun resolve(colorTexture: GpuTextureView, depthTexture: GpuTextureView?): Int {
        val backend = RenderSystem.getDevice().backend as GlDevice
        val color = colorTexture.texture() as GlTexture
        val depth = depthTexture?.texture() as? GlTexture
        return backend.frameBufferCache().getFbo(
            backend.directStateAccess(),
            listOf<FrameBufferAttachment>(color),
            depth
        )
    }
}
