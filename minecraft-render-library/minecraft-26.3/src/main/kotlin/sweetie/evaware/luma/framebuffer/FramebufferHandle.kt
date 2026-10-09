package sweetie.evaware.luma.framebuffer

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.textures.GpuTextureView
import com.mojang.renderpearl.backend.opengl.FrameBufferAttachment
import com.mojang.renderpearl.backend.opengl.GlTexture
import sweetie.evaware.luma.mixin.accessor.GlDeviceAccessor
import sweetie.evaware.luma.mixin.accessor.GpuDeviceAccessor

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
