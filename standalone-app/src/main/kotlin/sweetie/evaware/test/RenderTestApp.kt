package sweetie.evaware.test

import org.joml.Matrix4f
import org.joml.Matrix4fStack
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.*
import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.shader.Shader
import sweetie.evaware.luma.uniform.Mat4Uniform
import sweetie.evaware.test.ColorUtil
import sweetie.evaware.luma.matrix.MatrixControl
import sweetie.evaware.luma.texture.TextureUploader
import java.awt.image.BufferedImage

object RenderTestApp {
    private const val WIDTH = 960
    private const val HEIGHT = 540
    private const val TITLE = "Luma Render Test"

    private val shader = Shader(
        "assets/luma-renderer/shaders/core/rect_triangle.frag",
        "assets/luma-renderer/shaders/core/rect_triangle.vert"
    )

    private val projectionMatrix = Matrix4f()
    private val widthBuffer = BufferUtils.createIntBuffer(1)
    private val heightBuffer = BufferUtils.createIntBuffer(1)
    private val uMatrix: Mat4Uniform

    enum class LogPrefix(val value: String) {
        BENCHMARK("[Benchmark]"),
        INFO("[Info]"),
        ERROR("[Error]")
    }

    private fun log(prefix: LogPrefix, message: String) {
        println("${prefix.value} $message")
    }

    init {
        with(shader) {
            vertices.float(2, 0)
            vertices.float(4, 1)
            uMatrix = uniforms.mat4("uMatrix")
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        GLFWErrorCallback.createPrint(System.err).set()

        check(glfwInit()) { "Failed to initialize GLFW" }

        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)

        val window = glfwCreateWindow(WIDTH, HEIGHT, TITLE, MemoryUtil.NULL, MemoryUtil.NULL)
        check(window != MemoryUtil.NULL) { "Failed to create GLFW window" }

        try {
            glfwMakeContextCurrent(window)
            glfwSwapInterval(1)
            glfwShowWindow(window)
            GL.createCapabilities()
            shader.load()

            runBenchmark()

            if (args.contains("--benchmark-only")) return

            while (!glfwWindowShouldClose(window)) {
                glfwPollEvents()
                renderFrame(window)
                glfwSwapBuffers(window)
            }
        } finally {
            shader.close()
            glfwDestroyWindow(window)
            glfwTerminate()
            glfwSetErrorCallback(null)?.free()
            GL.setCapabilities(null)
        }
    }

    private fun captureStateLegacy(snapshot: Luma.GlStateSnapshot): Luma.GlStateSnapshot {
        val viewport = BufferUtils.createIntBuffer(4)
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport)

        snapshot.viewportX = viewport.get(0)
        snapshot.viewportY = viewport.get(1)
        snapshot.viewportWidth = viewport.get(2)
        snapshot.viewportHeight = viewport.get(3)

        snapshot.drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        snapshot.readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
        snapshot.arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
        snapshot.blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB)
        snapshot.blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA)
        snapshot.program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        snapshot.vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
        snapshot.blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND)
        snapshot.depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        snapshot.cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE)
        snapshot.activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
        snapshot.boundTexture2d = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        return snapshot
    }

    private fun uploadCoverageLegacy(image: BufferedImage): Int {
        val tex = GL11.glGenTextures()
        val prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        val buffer = BufferUtils.createByteBuffer(image.width * image.height * 4)

        val pixels = IntArray(image.width * image.height)
        image.getRGB(0, 0, image.width, image.height, pixels, 0, image.width)

        for (y in image.height - 1 downTo 0) {
            val offset = y * image.width
            for (x in 0 until image.width) {
                val alpha = ((pixels[offset + x] ushr 24) and 0xFF).toByte()
                buffer.put(alpha).put(alpha).put(alpha).put(alpha)
            }
        }
        buffer.flip()

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)

        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, image.width, image.height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer)
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex)
        return tex
    }

    private fun runBenchmark() {
        log(LogPrefix.INFO, "Running internal benchmarks...")
        Luma.mockRenderTargetWidth = 960
        Luma.mockRenderTargetHeight = 540

        val warmUp = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val tempSnapshot = Luma.GlStateSnapshot()
        repeat(5000) { i ->
            Luma.captureManagedMainFramebufferState(tempSnapshot)
            MatrixControl.current()
            if (i < 200) TextureUploader.uploadCoverage(warmUp).close()
        }

        // 1. State Capture
        val snapshot = Luma.GlStateSnapshot()
        val t0 = System.nanoTime()
        repeat(20_000) { captureStateLegacy(snapshot) }
        val d0 = (System.nanoTime() - t0) / 1_000_000.0

        val t1 = System.nanoTime()
        repeat(20_000) { Luma.captureManagedMainFramebufferState(snapshot) }
        val d1 = (System.nanoTime() - t1) / 1_000_000.0

        // 2. Matrix Multiplication
        val proj = Matrix4f()
        val stack = Matrix4fStack(32)
        val combined = Matrix4f()
        val t2 = System.nanoTime()
        repeat(500_000) { combined.set(proj).mul(stack) }
        val d2 = (System.nanoTime() - t2) / 1_000_000.0

        val t3 = System.nanoTime()
        repeat(500_000) { MatrixControl.current() }
        val d3 = (System.nanoTime() - t3) / 1_000_000.0

        // 3. Texture Upload
        val img = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB).apply {
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    setRGB(x, y, ((x + y) * 3 and 0xFF) shl 24)
                }
            }
        }

        val t4 = System.nanoTime()
        repeat(300) { GL11.glDeleteTextures(uploadCoverageLegacy(img)) }
        val d4 = (System.nanoTime() - t4) / 1_000_000.0

        val t5 = System.nanoTime()
        repeat(300) { TextureUploader.uploadCoverage(img).close() }
        val d5 = (System.nanoTime() - t5) / 1_000_000.0

        // Output results
        log(LogPrefix.BENCHMARK, "State Capture (20k): Legacy=%.2fms, Cached=%.2fms (Speedup: %.1f%%)".format(d0, d1, (d0 - d1) / d0 * 100))
        log(LogPrefix.BENCHMARK, "Matrix Mult (500k): Legacy=%.2fms, Cached=%.2fms (Speedup: %.1f%%)".format(d2, d3, (d2 - d3) / d2 * 100))
        log(LogPrefix.BENCHMARK, "Tex Upload (300): Legacy=%.2fms, Optimized=%.2fms (Speedup: %.1f%%)".format(d4, d5, (d4 - d5) / d4 * 100))

        val totalLegacy = d0 + d2 + d4
        val totalOpt = d1 + d3 + d5
        log(LogPrefix.BENCHMARK, "Total Benchmark: Legacy=%.2fms, Optimized=%.2fms (Overall Speedup: %.1f%%)".format(totalLegacy, totalOpt, (totalLegacy - totalOpt) / totalLegacy * 100))
    }

    private fun renderFrame(window: Long) {
        updateFramebufferSize(window)
        val w = widthBuffer.get(0)
        val h = heightBuffer.get(0)

        GL11.glViewport(0, 0, w, h)
        GL11.glClearColor(0f, 0f, 0f, 1f)
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
        projectionMatrix.identity().ortho(0f, w.toFloat(), h.toFloat(), 0f, -1f, 1f)

        Luma.render {
            shader.attach()
            shader.uniforms.mat4(uMatrix, projectionMatrix)
            drawRect(48f, 48f, 128f, 128f, ColorUtil.WHITE)
            Luma.drawShader(shader)
            shader.detach()
        }
    }

    private fun updateFramebufferSize(window: Long) {
        widthBuffer.clear()
        heightBuffer.clear()
        glfwGetFramebufferSize(window, widthBuffer, heightBuffer)
    }

    private fun drawRect(x: Float, y: Float, width: Float, height: Float, color: Int) {
        val x2 = x + width
        val y2 = y + height
        val r = ColorUtil.redf(color)
        val g = ColorUtil.greenf(color)
        val b = ColorUtil.bluef(color)
        val a = ColorUtil.alphaf(color)

        putVertex(x, y, r, g, b, a)
        putVertex(x, y2, r, g, b, a)
        putVertex(x2, y2, r, g, b, a)
        putVertex(x, y, r, g, b, a)
        putVertex(x2, y2, r, g, b, a)
        putVertex(x2, y, r, g, b, a)
    }

    private fun putVertex(x: Float, y: Float, r: Float, g: Float, b: Float, a: Float) {
        shader.vertices.vec2(x, y).vec4(r, g, b, a)
    }
}