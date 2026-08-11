package sweetie.evaware.benchmark

import sweetie.evaware.test.GlTestApp
import sweetie.evaware.test.VulkanTestApp
import sweetie.evaware.luma.backend.gl.Backend
import sweetie.evaware.luma.backend.gl.GlBackendConfig
import sweetie.evaware.luma.backend.gl.GlStatePolicy

object BenchmarkApp {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1) { "Expected one backend: gl or vulkan" }
        when (args[0].lowercase()) {
            "gl", "opengl" -> GlBenchmarkApp().run(emptyArray())
            "vulkan", "blaze3d" -> VulkanBenchmarkApp().run(emptyArray())
            else -> error("Unknown backend '${args[0]}'; expected gl or vulkan")
        }
    }
}

private class GlBenchmarkApp : GlTestApp() {
    override val windowVisible: Boolean = false

    override fun createBackend(): Backend = Backend(
        GlBackendConfig(statePolicy = GlStatePolicy.OWNED)
    )

    override fun runScene(window: Long, args: Array<String>) {
        RenderBenchmarkSuite("OpenGL", awaitGpu = ::awaitGpu).run()
    }
}

private class VulkanBenchmarkApp : VulkanTestApp() {
    override fun runScene(window: Long, args: Array<String>) {
        RenderBenchmarkSuite(
            "Vulkan",
            beforeSubmit = backend::requestCompletionFence,
            awaitGpu = backend::awaitCompletionFence
        ).run()
    }
}
