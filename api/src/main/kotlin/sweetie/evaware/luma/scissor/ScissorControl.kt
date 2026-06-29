package sweetie.evaware.luma.scissor

import sweetie.evaware.luma.Luma

object ScissorControl {
    private const val INITIAL_DEPTH = 16

    private var size = 0
    private var stack = FloatArray(INITIAL_DEPTH * 4)
    private var scale = 1f
    private var windowHeight = 0f

    var version = 0; private set
    var minX = 0f; private set
    var minY = 0f; private set
    var maxX = 0f; private set
    var maxY = 0f; private set
    var hasActive = false; private set

    fun beginGuiFrame() {
        scale = Luma.platform.getGuiScale()
        windowHeight = Luma.platform.getWindowHeight()
        clear()
    }

    fun clear() {
        size = 0
        minX = 0f
        minY = 0f
        maxX = 0f
        maxY = 0f
        hasActive = false
        version++
    }

    fun push(x: Float, y: Float, width: Float, height: Float) {
        val nextMinX = x * scale
        val nextMinY = windowHeight - (y + height) * scale
        val nextMaxX = (x + width) * scale
        val nextMaxY = windowHeight - y * scale

        ensureCapacity(size + 1)
        val index = size * 4

        if (size > 0) {
            val prevMinX = stack[index - 4]
            val prevMinY = stack[index - 3]
            val prevMaxX = stack[index - 2]
            val prevMaxY = stack[index - 1]

            minX = if (nextMinX > prevMinX) nextMinX else prevMinX
            minY = if (nextMinY > prevMinY) nextMinY else prevMinY
            maxX = if (nextMaxX < prevMaxX) nextMaxX else prevMaxX
            maxY = if (nextMaxY < prevMaxY) nextMaxY else prevMaxY
        } else {
            minX = nextMinX
            minY = nextMinY
            maxX = nextMaxX
            maxY = nextMaxY
        }

        stack[index] = minX
        stack[index + 1] = minY
        stack[index + 2] = maxX
        stack[index + 3] = maxY
        size++
        hasActive = true
        version++
    }

    fun pop() {
        require(size > 0) { "Scissor stack underflow" }
        size--
        if (size == 0) {
            minX = 0f
            minY = 0f
            maxX = 0f
            maxY = 0f
            hasActive = false
            version++
            return
        }
        val index = (size - 1) * 4
        minX = stack[index]
        minY = stack[index + 1]
        maxX = stack[index + 2]
        maxY = stack[index + 3]
        hasActive = true
        version++
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