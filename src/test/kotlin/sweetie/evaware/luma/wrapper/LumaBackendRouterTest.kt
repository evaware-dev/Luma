package sweetie.evaware.luma.wrapper

import sweetie.evaware.luma.gpu.ManagedGpuBackend
import sweetie.evaware.luma.vulkan.VulkanRenderHost
import sweetie.evaware.luma.vulkan.VulkanRuntime
import sweetie.evaware.luma.wrapper.backend.RenderHost
import sweetie.evaware.luma.wrapper.frame.FrameInfo
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

class LumaBackendRouterTest {
    @Test
    fun `router reuses backend instances per render type`() {
        var renderType = RenderApiType.OPEN_GL
        val host = object : RenderHost {
            override fun frameInfo() = FrameInfo(0f, 0f, 0f, 0f, 1f)
        }
        val router = LumaBackendRouter(
            renderTypeResolver = { renderType },
            renderHostProvider = { host }
        )

        val openGlBackend = router.resolve()
        assertSame(openGlBackend, router.resolve())

        renderType = RenderApiType.VULKAN
        val vulkanBackend = router.resolve()
        assertSame(vulkanBackend, router.resolve())
    }

    @Test
    fun `router uses managed gpu backend for open gl on managed gpu hosts`() {
        val host = object : RenderHost, VulkanRenderHost {
            override fun frameInfo() = FrameInfo(0f, 0f, 0f, 0f, 1f)

            override fun vulkanRuntime(): VulkanRuntime = object : VulkanRuntime {
                override fun beginFrame(frameInfo: FrameInfo) = Unit

                override fun draw(
                    pipeline: sweetie.evaware.luma.wrapper.backend.LumaPipeline,
                    vertices: sweetie.evaware.luma.wrapper.LumaVertexBuffer,
                    texture: sweetie.evaware.luma.wrapper.texture.TextureHandle?
                ) = Unit

                override fun endFrame() = Unit

                override fun close() = Unit
            }
        }
        val router = LumaBackendRouter(
            renderTypeResolver = { RenderApiType.OPEN_GL },
            renderHostProvider = { host }
        )

        assertIs<ManagedGpuBackend>(router.resolve())
    }
}
