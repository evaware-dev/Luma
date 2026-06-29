package sweetie.evaware.luma.scissor

import sweetie.evaware.luma.Luma

object ScissorControl {
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
        pop()
    }

    fun pop() {
        minX = 0f;
        minY = 0f;
        maxX = 0f;
        maxY = 0f
        hasActive = false
        version++
    }

    fun push(x: Float, y: Float, width: Float, height: Float) {
        minX = x * scale
        minY = windowHeight - (y + height) * scale
        maxX = (x + width) * scale
        maxY = windowHeight - y * scale
        hasActive = true
        version++
    }
}