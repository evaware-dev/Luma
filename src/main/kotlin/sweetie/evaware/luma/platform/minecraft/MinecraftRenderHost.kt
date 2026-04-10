package sweetie.evaware.luma.platform.minecraft

import sweetie.evaware.luma.platform.minecraft.opengl.MinecraftOpenGlFramebufferBridge
import sweetie.evaware.luma.platform.minecraft.vulkan.MinecraftVulkanRuntime
import sweetie.evaware.luma.vulkan.VulkanRenderHost
import sweetie.evaware.luma.vulkan.VulkanRuntime
import sweetie.evaware.luma.wrapper.RenderApiType
import sweetie.evaware.luma.wrapper.backend.RenderHost
import sweetie.evaware.luma.wrapper.frame.FrameInfo

object MinecraftRenderHost : RenderHost, VulkanRenderHost {
    private val vulkanRuntime = MinecraftVulkanRuntime()

    override fun renderType(): RenderApiType = MinecraftRenderTypeResolver.current()

    override fun frameInfo(): FrameInfo = MinecraftFrameInfoProvider.current()

    override fun beginFrame(renderType: RenderApiType) {
        if (renderType == RenderApiType.OPEN_GL) {
            MinecraftOpenGlFramebufferBridge.bindMainFramebuffer()
        }
    }

    override fun vulkanRuntime(): VulkanRuntime = vulkanRuntime
}
