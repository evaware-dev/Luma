package sweetie.evaware.test

import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.backend.gl.Backend
import sweetie.evaware.luma.backend.gl.GlRenderTarget
import sweetie.evaware.luma.shader.Shader
import sweetie.evaware.luma.shader.translator.DefaultShaderTranslator
import sweetie.evaware.luma.shader.translator.ShaderTarget

open class GlTestApp : BackendTestApp(960, 540, "Luma OpenGL") {
    private lateinit var backend: Backend
    private lateinit var shader: Shader
    private lateinit var directGeometry: DirectGeometryScene
    private val framebufferWidth = BufferUtils.createIntBuffer(1)
    private val framebufferHeight = BufferUtils.createIntBuffer(1)

    override fun configureWindowHints() {
        GLFW.glfwDefaultWindowHints()
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3)
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3)
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE)
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE)
    }

    override fun initialize(window: Long) {
        GLFW.glfwMakeContextCurrent(window)
        GLFW.glfwSwapInterval(1)
        GL.createCapabilities()

        backend = createBackend()
        Luma.backend = backend
        Luma.shaderTranslator = DefaultShaderTranslator(ShaderTarget.OPENGL)
        shader = Shader("shaders/triangle.frag", "shaders/triangle.vert")
        shader.vertices.float2(0)
        shader.load()
        directGeometry = DirectGeometryScene(backend)
    }

    override fun runScene(window: Long, args: Array<String>) {
        verifyOffscreenRendering()
        if (args.contains("--verify-only")) return
        while (!GLFW.glfwWindowShouldClose(window)) {
            GLFW.glfwPollEvents()
            renderFrame(window)
            GLFW.glfwSwapBuffers(window)
        }
    }

    protected fun awaitGpu() {
        GL11.glFinish()
    }

    protected open fun createBackend(): Backend = Backend()

    override fun closeBackend() {
        if (::directGeometry.isInitialized) directGeometry.close()
        if (::shader.isInitialized) shader.close()
        if (::backend.isInitialized) {
            Luma.render {}
            backend.close()
        }
        GL.setCapabilities(null)
    }

    private fun verifyOffscreenRendering() {
        val size = 64
        val target = backend.createRenderTarget(size, size, false, RenderTargetFormat.RGBA8)
        try {
            Luma.render {
                backend.beginRenderTarget(target, floatArrayOf(0f, 0f, 1f, 1f))
                directGeometry.draw()
                backend.endRenderTarget()
            }

            val pixels = BufferUtils.createByteBuffer(size * size * 4)
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, (target as GlRenderTarget).fbo)
            GL11.glReadPixels(0, 0, size, size, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels)
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0)

            val left = colorAt(pixels, size, size / 4, size / 2)
            val right = colorAt(pixels, size, size * 3 / 4, size / 2)
            val corner = colorAt(pixels, size, 2, 2)
            check(isRed(left) && isRed(right) && isBlue(corner)) {
                "OpenGL readback mismatch: left=${left.contentToString()}, right=${right.contentToString()}, corner=${corner.contentToString()}"
            }
            println("[Info] OpenGL offscreen readback: left=${left.contentToString()} right=${right.contentToString()} corner=${corner.contentToString()} => PASS")
        } finally {
            target.close()
        }
    }

    private fun renderFrame(window: Long) {
        framebufferWidth.clear()
        framebufferHeight.clear()
        GLFW.glfwGetFramebufferSize(window, framebufferWidth, framebufferHeight)
        GL11.glViewport(0, 0, framebufferWidth[0], framebufferHeight[0])
        GL11.glClearColor(0f, 0f, 1f, 1f)
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
        Luma.render { drawTriangle() }
    }

    private fun drawTriangle() {
        shader.attach()
        shader.vertices
            .vec2(-0.75f, -0.75f)
            .vec2(0.75f, -0.75f)
            .vec2(0f, 0.75f)
        shader.draw()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) = GlTestApp().run(args)
    }
}
