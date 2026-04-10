package sweetie.evaware.luma.wrapper.scissor

import sweetie.evaware.luma.wrapper.api.Clearable
import sweetie.evaware.luma.wrapper.frame.FrameState
import kotlin.math.max
import kotlin.math.min

object ScissorControl : Clearable {
    private const val INITIAL_DEPTH = 16

    private var size = 0
    private var stack = FloatArray(INITIAL_DEPTH * 4)
    private var scale = 1f
    private var windowHeight = 0f

    private var currentMinX = 0f
    private var currentMinY = 0f
    private var currentMaxX = 0f
    private var currentMaxY = 0f

    fun beginGuiFrame() {
        val frameInfo = FrameState.current()
        scale = frameInfo.guiScale
        windowHeight = frameInfo.windowHeight
        clear()
    }

    override fun clear() {
        size = 0
        currentMinX = 0f
        currentMinY = 0f
        currentMaxX = 0f
        currentMaxY = 0f
    }

    fun push(x: Float, y: Float, width: Float, height: Float) {
        val minX = x * scale
        val minY = windowHeight - (y + height) * scale
        val maxX = (x + width) * scale
        val maxY = windowHeight - y * scale

        val nextMinX = if (size == 0) minX else max(minX, currentMinX)
        val nextMinY = if (size == 0) minY else max(minY, currentMinY)
        val nextMaxX = if (size == 0) maxX else min(maxX, currentMaxX)
        val nextMaxY = if (size == 0) maxY else min(maxY, currentMaxY)

        ensureCapacity(size + 1)
        val index = size * 4
        stack[index] = nextMinX
        stack[index + 1] = nextMinY
        stack[index + 2] = max(nextMinX, nextMaxX)
        stack[index + 3] = max(nextMinY, nextMaxY)
        size++
        restoreCurrent(index)
    }

    fun pop() {
        require(size > 0) { "Scissor stack underflow" }
        size--
        if (size == 0) {
            clear()
            return
        }
        restoreCurrent((size - 1) * 4)
    }

    fun copyCurrent(out: FloatArray, offset: Int = 0) {
        out[offset] = currentMinX
        out[offset + 1] = currentMinY
        out[offset + 2] = currentMaxX
        out[offset + 3] = currentMaxY
    }

    internal val minX
        get() = currentMinX

    internal val minY
        get() = currentMinY

    internal val maxX
        get() = currentMaxX

    internal val maxY
        get() = currentMaxY

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
