package sweetie.evaware.test

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.platform.NativeLibrariesBootstrap
import com.mojang.blaze3d.shaders.GpuDebugOptions
import com.mojang.blaze3d.shaders.ShaderSource
import com.mojang.blaze3d.systems.GpuDevice
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vulkan.VulkanBackend
import net.minecraft.SharedConstants
import org.lwjgl.glfw.GLFW
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.backend.blaze3d.Backend
import sweetie.evaware.luma.backend.blaze3d.VulkanRenderTarget
import sweetie.evaware.luma.shader.Shader
import sweetie.evaware.luma.shader.translator.DefaultShaderTranslator
import sweetie.evaware.luma.shader.translator.ShaderTarget

open class VulkanTestApp : BackendTestApp(64, 64, "Luma Vulkan") {
    override val windowVisible: Boolean = false

    private val gpuBackend = VulkanBackend()
    protected lateinit var device: GpuDevice
        private set
    protected lateinit var backend: Backend
        private set
    private lateinit var shader: Shader
    private lateinit var target: VulkanRenderTarget
    private var rendererInitialized = false

    override fun prepareRuntime() {
        NativeLibrariesBootstrap.loadLibraries()
        SharedConstants.tryDetectVersion()
        RenderSystem.initRenderThread()
    }

    override fun configureWindowHints() {
        GLFW.glfwDefaultWindowHints()
        gpuBackend.setWindowHints()
    }

    override fun initialize(window: Long) {
        val shaderSource = ShaderSource { id, type -> error("Unexpected global shader request: $id ($type)") }
        device = gpuBackend.createDevice(window, shaderSource, GpuDebugOptions(0, false, false, false)) {}
        RenderSystem.initRenderer(device)
        rendererInitialized = true

        println("[Info] Blaze3D device: ${device.deviceInfo}")
        backend = Backend()
        Luma.backend = backend
        Luma.shaderTranslator = DefaultShaderTranslator(ShaderTarget.BLAZE3D)
        shader = Shader("shaders/triangle.frag", "shaders/triangle.vert")
        shader.vertices.float2(0)
        shader.load()
        target = backend.createRenderTarget(64, 64, false, RenderTargetFormat.RGBA8) as VulkanRenderTarget
    }

    override fun runScene(window: Long, args: Array<String>) {
        Luma.render {
            backend.beginRenderTarget(target, floatArrayOf(0f, 0f, 1f, 1f))
            shader.attach()
            shader.vertices
                .vec2(-0.75f, -0.75f)
                .vec2(0.75f, -0.75f)
                .vec2(0f, 0.75f)
            shader.draw()
            backend.endRenderTarget()
        }
        verifyPixels()
    }

    protected fun awaitGpu() {
        val encoder = device.createCommandEncoder()
        val fence = encoder.createFence()
        try {
            encoder.submit()
            check(fence.awaitCompletion(Long.MAX_VALUE)) { "Timed out waiting for Vulkan work" }
        } finally {
            fence.close()
        }
    }

    override fun closeBackend() {
        if (::target.isInitialized) target.close()
        if (::shader.isInitialized) shader.close()
        if (::backend.isInitialized) backend.close()
        if (rendererInitialized) RenderSystem.shutdownRenderer()
        else if (::device.isInitialized) device.close()
    }

    private fun verifyPixels() {
        val size = 64
        val readback = device.createBuffer(
            { "Luma Vulkan readback" },
            GpuBuffer.USAGE_MAP_READ or GpuBuffer.USAGE_COPY_DST,
            size.toLong() * size * 4
        )
        try {
            val encoder = device.createCommandEncoder()
            encoder.copyTextureToBuffer(target.gpuTexture, readback, 0, {}, 0)
            val fence = encoder.createFence()
            try {
                encoder.submit()
                check(fence.awaitCompletion(Long.MAX_VALUE)) { "Timed out waiting for Vulkan readback" }
            } finally {
                fence.close()
            }

            readback.map(true, false).use { mapped ->
                val center = colorAt(mapped.data(), size, size / 2, size / 2)
                val corner = colorAt(mapped.data(), size, 2, 2)
                check(isRed(center) && isBlue(corner)) {
                    "Vulkan readback mismatch: center=${center.contentToString()}, corner=${corner.contentToString()}"
                }
                println("[Info] Vulkan offscreen readback: center=${center.contentToString()} corner=${corner.contentToString()} => PASS")
            }
        } finally {
            readback.close()
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) = VulkanTestApp().run(args)
    }
}
