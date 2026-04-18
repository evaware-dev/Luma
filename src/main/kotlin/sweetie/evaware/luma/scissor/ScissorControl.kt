package sweetie.evaware.luma.scissor

import kotlin.math.ceil
import kotlin.math.floor
import net.minecraft.client.Minecraft
import org.lwjgl.opengl.GL11
import sweetie.evaware.luma.api.Clearable

object ScissorControl : Clearable {
    private const val INITIAL_DEPTH = 16

    private var size = 0
    private var stack = FloatArray(INITIAL_DEPTH * 4)
    private var scale = 1f
    private var windowHeight = 0f
    private var stateVersion = 0

    private var currentMinX = 0f
    private var currentMinY = 0f
    private var currentMaxX = 0f
    private var currentMaxY = 0f

    fun beginGuiFrame() {
        val window = Minecraft.getInstance().window
        scale = window.guiScale.toFloat()
        windowHeight = window.height.toFloat()
        clear()
    }

    override fun clear() {
        size = 0
        currentMinX = 0f
        currentMinY = 0f
        currentMaxX = 0f
        currentMaxY = 0f
        stateVersion++
    }

    fun version() = stateVersion

    fun minX() = currentMinX

    fun minY() = currentMinY

    fun maxX() = currentMaxX

    fun maxY() = currentMaxY

    fun hasActive() = size > 0

    fun push(x: Float, y: Float, width: Float, height: Float) {
        val minX = x * scale
        val minY = windowHeight - (y + height) * scale
        val maxX = (x + width) * scale
        val maxY = windowHeight - y * scale

        val nextMinX: Float
        val nextMinY: Float
        val nextMaxX: Float
        val nextMaxY: Float

        if (size == 0) {
            nextMinX = minX
            nextMinY = minY
            nextMaxX = maxX
            nextMaxY = maxY
        } else {
            nextMinX = if (minX > currentMinX) minX else currentMinX
            nextMinY = if (minY > currentMinY) minY else currentMinY
            nextMaxX = if (maxX < currentMaxX) maxX else currentMaxX
            nextMaxY = if (maxY < currentMaxY) maxY else currentMaxY
        }

        ensureCapacity(size + 1)
        val index = size * 4
        currentMinX = nextMinX
        currentMinY = nextMinY
        currentMaxX = if (nextMinX > nextMaxX) nextMinX else nextMaxX
        currentMaxY = if (nextMinY > nextMaxY) nextMinY else nextMaxY
        stack[index] = currentMinX
        stack[index + 1] = currentMinY
        stack[index + 2] = currentMaxX
        stack[index + 3] = currentMaxY
        size++
        stateVersion++
    }

    fun pop() {
        require(size > 0) { "Scissor stack underflow" }
        size--
        if (size == 0) {
            currentMinX = 0f
            currentMinY = 0f
            currentMaxX = 0f
            currentMaxY = 0f
            stateVersion++
            return
        }
        restoreCurrent((size - 1) * 4)
        stateVersion++
    }

    fun applyGlScissor() {
        if (size == 0) {
            disableGlScissor()
            return
        }

        val x = floor(currentMinX).toInt()
        val y = floor(currentMinY).toInt()
        val width = (ceil(currentMaxX) - floor(currentMinX)).toInt().coerceAtLeast(0)
        val height = (ceil(currentMaxY) - floor(currentMinY)).toInt().coerceAtLeast(0)

        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        GL11.glScissor(x, y, width, height)
    }

    fun disableGlScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST)
    }

    private fun restoreCurrent(index: Int) {
        currentMinX = stack[index]
        currentMinY = stack[index + 1]
        currentMaxX = stack[index + 2]
        currentMaxY = stack[index + 3]
    }

    private fun ensureCapacity(required: Int) {
        if (required * 4 <= stack.size) return

        var capacity = stack.size.coerceAtLeast(4)
        while (required * 4 > capacity) {
            capacity = capacity shl 1
        }
        stack = stack.copyOf(capacity)
    }
}
