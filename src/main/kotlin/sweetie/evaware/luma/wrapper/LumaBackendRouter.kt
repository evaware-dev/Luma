package sweetie.evaware.luma.wrapper

import sweetie.evaware.luma.gpu.ManagedGpuBackend
import sweetie.evaware.luma.opengl.OpenGlBackend
import sweetie.evaware.luma.vulkan.VulkanBackend
import sweetie.evaware.luma.vulkan.VulkanRenderHost
import sweetie.evaware.luma.wrapper.backend.RenderHost

class LumaBackendRouter(
    private val renderTypeResolver: () -> RenderApiType,
    private val renderHostProvider: () -> RenderHost
) : AutoCloseable {
    private val standaloneOpenGlBackend = OpenGlBackend()
    private val managedOpenGlBackend = ManagedGpuBackend(RenderApiType.OPEN_GL, renderHostProvider)
    private val vulkanBackend = VulkanBackend(renderHostProvider)

    fun resolve(): LumaBackend = when (renderTypeResolver()) {
        RenderApiType.OPEN_GL -> {
            val host = renderHostProvider()
            if (host is VulkanRenderHost) managedOpenGlBackend else standaloneOpenGlBackend
        }
        RenderApiType.VULKAN -> vulkanBackend
    }

    override fun close() {
        vulkanBackend.close()
        managedOpenGlBackend.close()
        standaloneOpenGlBackend.close()
    }
}
