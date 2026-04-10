package sweetie.evaware.luma.vulkan

import sweetie.evaware.luma.wrapper.LumaBackend
import sweetie.evaware.luma.wrapper.LumaVertexBuffer
import sweetie.evaware.luma.wrapper.RenderApiType
import sweetie.evaware.luma.wrapper.backend.LumaPipeline
import sweetie.evaware.luma.wrapper.backend.RenderHost
import sweetie.evaware.luma.wrapper.frame.FrameInfo
import sweetie.evaware.luma.wrapper.texture.TextureHandle

internal class VulkanBackend(
    private val hostProvider: () -> RenderHost
) : LumaBackend {
    override val type = RenderApiType.VULKAN

    override fun beginFrame(frameInfo: FrameInfo, host: RenderHost) {
        runtime().beginFrame(frameInfo)
    }

    override fun endFrame() {
        runtime().endFrame()
    }

    override fun draw(pipeline: LumaPipeline, vertices: LumaVertexBuffer, texture: TextureHandle?) {
        runtime().draw(pipeline, vertices, texture)
    }

    override fun close() {
        (hostProvider() as? VulkanRenderHost)?.vulkanRuntime()?.close()
    }

    private fun runtime(): VulkanRuntime {
        val host = hostProvider()
        return (host as? VulkanRenderHost)?.vulkanRuntime()
            ?: error("Current render host does not expose a Vulkan runtime")
    }
}
