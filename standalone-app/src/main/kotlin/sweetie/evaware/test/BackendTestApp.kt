package sweetie.evaware.test

import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

abstract class BackendTestApp(
    private val width: Int,
    private val height: Int,
    private val title: String
) {
    protected open val windowVisible: Boolean = true

    fun run(args: Array<String>) {
        var window = MemoryUtil.NULL
        var glfwInitialized = false
        GLFWErrorCallback.createPrint(System.err).set()
        try {
            prepareRuntime()
            check(GLFW.glfwInit()) { "Failed to initialize GLFW" }
            glfwInitialized = true
            configureWindowHints()
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)
            window = GLFW.glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL)
            check(window != MemoryUtil.NULL) { "Failed to create '$title' window" }
            initialize(window)
            if (windowVisible) GLFW.glfwShowWindow(window)
            runScene(window, args)
        } finally {
            try {
                closeBackend()
            } finally {
                try {
                    if (window != MemoryUtil.NULL) GLFW.glfwDestroyWindow(window)
                } finally {
                    if (glfwInitialized) GLFW.glfwTerminate()
                    GLFW.glfwSetErrorCallback(null)?.free()
                }
            }
        }
    }

    protected open fun prepareRuntime() = Unit

    protected abstract fun configureWindowHints()

    protected abstract fun initialize(window: Long)

    protected abstract fun runScene(window: Long, args: Array<String>)

    protected abstract fun closeBackend()
}

internal fun colorAt(buffer: ByteBuffer, width: Int, x: Int, y: Int): IntArray {
    val offset = (y * width + x) * 4
    return IntArray(4) { channel -> buffer.get(offset + channel).toInt() and 0xFF }
}

internal fun isRed(color: IntArray): Boolean = color[0] > 200 && color[1] < 60 && color[2] < 60

internal fun isBlue(color: IntArray): Boolean = color[0] < 60 && color[1] < 60 && color[2] > 200
