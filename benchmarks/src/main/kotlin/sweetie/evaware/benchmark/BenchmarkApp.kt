package sweetie.evaware.benchmark

import sweetie.evaware.benchmark.workload.RenderBenchmarkSuite
import sweetie.evaware.luma.backend.gl.Backend
import sweetie.evaware.luma.backend.gl.GlBackendConfig
import sweetie.evaware.luma.backend.gl.GlStatePolicy
import sweetie.evaware.test.GlTestApp
import sweetie.evaware.test.VulkanTestApp

object BenchmarkApp {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1) { "Expected one backend: gl, gl-preserve, or vulkan" }
        when (args[0].lowercase()) {
            "gl", "opengl" -> GlBenchmarkApp(GlStatePolicy.OWNED).run(emptyArray())
            "gl-preserve", "opengl-preserve" -> GlBenchmarkApp(GlStatePolicy.PRESERVE).run(emptyArray())
            "vulkan", "blaze3d" -> VulkanBenchmarkApp().run(emptyArray())
            else -> error("Unknown backend '${args[0]}'; expected gl, gl-preserve, or vulkan")
        }
    }
}

private class GlBenchmarkApp(
    private val statePolicy: GlStatePolicy
) : GlTestApp() {
    override val windowVisible: Boolean = false

    override fun createBackend(): Backend = Backend(
        GlBackendConfig(statePolicy = statePolicy)
    )

    override fun runScene(window: Long, args: Array<String>) {
        RenderBenchmarkSuite("OpenGL/$statePolicy", awaitGpu = ::awaitGpu).run()
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
