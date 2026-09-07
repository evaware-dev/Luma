package sweetie.evaware.luma.backend.gl

internal class GlFramebufferStateCache {
    private var known = false
    private var drawFramebuffer = 0
    private var readFramebuffer = 0
    private var viewportX = 0
    private var viewportY = 0
    private var viewportWidth = 0
    private var viewportHeight = 0

    fun seed(
        drawFramebuffer: Int,
        readFramebuffer: Int,
        viewportX: Int,
        viewportY: Int,
        viewportWidth: Int,
        viewportHeight: Int
    ) {
        this.drawFramebuffer = drawFramebuffer
        this.readFramebuffer = readFramebuffer
        this.viewportX = viewportX
        this.viewportY = viewportY
        this.viewportWidth = viewportWidth
        this.viewportHeight = viewportHeight
        known = true
    }

    fun saveTo(destination: IntArray): Boolean {
        if (!known) return false
        require(destination.size >= STATE_SIZE) { "Framebuffer state destination must contain at least $STATE_SIZE values" }
        destination[0] = drawFramebuffer
        destination[1] = readFramebuffer
        destination[2] = viewportX
        destination[3] = viewportY
        destination[4] = viewportWidth
        destination[5] = viewportHeight
        return true
    }

    fun restoreFrom(source: IntArray) {
        require(source.size >= STATE_SIZE) { "Framebuffer state source must contain at least $STATE_SIZE values" }
        seed(source[0], source[1], source[2], source[3], source[4], source[5])
    }

    fun invalidate() {
        known = false
    }

    private companion object {
        const val STATE_SIZE = 6
    }
}
