package sweetie.evaware.luma.vulkan

import sweetie.evaware.luma.gpu.ManagedGpuRenderHost
import sweetie.evaware.luma.wrapper.LumaBackend
import sweetie.evaware.luma.wrapper.LumaVertexBuffer
import sweetie.evaware.luma.wrapper.RenderApiType
import sweetie.evaware.luma.wrapper.backend.LumaPipeline
import sweetie.evaware.luma.wrapper.backend.RenderHost
import sweetie.evaware.luma.wrapper.frame.FrameInfo
import sweetie.evaware.luma.wrapper.resource.LumaRenderTarget
import sweetie.evaware.luma.wrapper.texture.SampledTexture
import sweetie.evaware.luma.wrapper.texture.TextureBinding

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

    override fun draw(pipeline: LumaPipeline, vertices: LumaVertexBuffer, texture: TextureBinding?) {
        runtime().draw(pipeline, vertices, texture)
    }

    override fun createRenderTarget(debugName: String, width: Int, height: Int): LumaRenderTarget {
        return runtime().createRenderTarget(debugName, width, height)
    }

    override fun drawTo(target: LumaRenderTarget, pipeline: LumaPipeline, vertices: LumaVertexBuffer, texture: SampledTexture?) {
        runtime().drawTo(target, pipeline, vertices, texture)
    }

    override fun close() {
        (hostProvider() as? ManagedGpuRenderHost)?.managedGpuRuntime()?.close()
    }

    private fun runtime(): VulkanRuntime {
        val host = hostProvider()
        return (host as? ManagedGpuRenderHost)?.managedGpuRuntime()
            ?: error("Current render host does not expose a Vulkan runtime")
    }
}
