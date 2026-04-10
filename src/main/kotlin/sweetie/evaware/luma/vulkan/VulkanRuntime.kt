package sweetie.evaware.luma.vulkan

import sweetie.evaware.luma.wrapper.LumaVertexBuffer
import sweetie.evaware.luma.wrapper.backend.LumaPipeline
import sweetie.evaware.luma.wrapper.frame.FrameInfo
import sweetie.evaware.luma.wrapper.texture.TextureHandle

interface VulkanRuntime : AutoCloseable {
    fun beginFrame(frameInfo: FrameInfo) = Unit

    fun endFrame() = Unit

    fun draw(pipeline: LumaPipeline, vertices: LumaVertexBuffer, texture: TextureHandle? = null)
}
