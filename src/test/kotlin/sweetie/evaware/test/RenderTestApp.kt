package sweetie.evaware.test

import org.joml.Matrix4f
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.shader.Shader
import sweetie.evaware.luma.uniform.Mat4Uniform
import sweetie.evaware.renderutil.helper.ColorUtil

object RenderTestApp {
    private const val initialWidth = 960
    private const val initialHeight = 540
    private const val windowTitle = "Luma Render Test"

    private val shader = Shader(
        "assets/luma-renderer/shaders/core/rect_triangle.frag",
        "assets/luma-renderer/shaders/core/rect_triangle.vert"
    )

    private val projectionMatrix = Matrix4f()
    private val widthBuffer = BufferUtils.createIntBuffer(1)
    private val heightBuffer = BufferUtils.createIntBuffer(1)
    private var uMatrix: Mat4Uniform

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
        check(GLFW.glfwInit()) { "Unable to initialize GLFW" }

        GLFW.glfwDefaultWindowHints()
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3)
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3)
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE)
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE)

        val window = GLFW.glfwCreateWindow(initialWidth, initialHeight, windowTitle, MemoryUtil.NULL, MemoryUtil.NULL)
        check(window != MemoryUtil.NULL) { "Unable to create GLFW window" }

        try {
            GLFW.glfwMakeContextCurrent(window)
            GLFW.glfwSwapInterval(1)
            GLFW.glfwShowWindow(window)
            GL.createCapabilities()
            shader.load()

            while (!GLFW.glfwWindowShouldClose(window)) {
                GLFW.glfwPollEvents()
                renderFrame(window)
                GLFW.glfwSwapBuffers(window)
            }
        } finally {
            shader.close()
            GLFW.glfwDestroyWindow(window)
            GLFW.glfwTerminate()
            GLFW.glfwSetErrorCallback(null)?.free()
            GL.setCapabilities(null)
        }
    }

    private fun renderFrame(window: Long) {
        updateFramebufferSize(window)
        val width = widthBuffer.get(0)
        val height = heightBuffer.get(0)

        GL11.glViewport(0, 0, width, height)
        GL11.glClearColor(0f, 0f, 0f, 1f)
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
        projectionMatrix.identity().ortho(0f, width.toFloat(), height.toFloat(), 0f, -1f, 1f)

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
        GLFW.glfwGetFramebufferSize(window, widthBuffer, heightBuffer)
    }

    private fun drawRect(x: Float, y: Float, width: Float, height: Float, color: Int) {
        val minX = x
        val minY = y
        val maxX = x + width
        val maxY = y + height
        val red = ColorUtil.redf(color)
        val green = ColorUtil.greenf(color)
        val blue = ColorUtil.bluef(color)
        val alpha = ColorUtil.alphaf(color)

        putVertex(minX, minY, red, green, blue, alpha)
        putVertex(minX, maxY, red, green, blue, alpha)
        putVertex(maxX, maxY, red, green, blue, alpha)
        putVertex(minX, minY, red, green, blue, alpha)
        putVertex(maxX, maxY, red, green, blue, alpha)
        putVertex(maxX, minY, red, green, blue, alpha)
    }

    private fun putVertex(x: Float, y: Float, red: Float, green: Float, blue: Float, alpha: Float) {
        shader.vertices
            .vec2(x, y)
            .vec4(red, green, blue, alpha)
    }
}
