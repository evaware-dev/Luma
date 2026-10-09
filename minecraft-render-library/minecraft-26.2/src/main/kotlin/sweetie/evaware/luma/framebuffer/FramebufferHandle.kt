package sweetie.evaware.luma.framebuffer

import com.mojang.blaze3d.opengl.FrameBufferAttachment
import com.mojang.blaze3d.opengl.GlTexture
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTextureView
import sweetie.evaware.luma.mixin.accessor.GpuDeviceAccessor
import sweetie.evaware.luma.mixin.accessor.GlDeviceAccessor

object FramebufferHandle {
    fun resolve(colorTexture: GpuTextureView, depthTexture: GpuTextureView?): Int {
        val device = RenderSystem.getDevice() as GpuDeviceAccessor
        val backend = device.backend as GlDeviceAccessor
        val color = colorTexture.texture() as GlTexture
        val depth = depthTexture?.texture() as? GlTexture
        return backend.invokerFrameBufferCache().getFbo(
            backend.invokerDirectStateAccess(),
            listOf<FrameBufferAttachment>(color),
            depth
        )
    }
}
