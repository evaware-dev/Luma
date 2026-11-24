package sweetie.evaware.luma.backend.gl

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.TextureFilter

internal class GlFramebufferStack(
    private val config: GlBackendConfig,
    private val stateGuard: GlStateGuard
) {
    private val targetBindings = ArrayList<IntArray>()
    var targetDepth = 0
        private set

    // Logical vs applied framebuffer & viewport state (lazy binding)
    private var logicalDrawFramebuffer = HOST_FBO_SENTINEL
    private var logicalReadFramebuffer = HOST_FBO_SENTINEL
    private var viewportX = 0
    private var viewportY = 0
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var hostViewportKnown = false
    var hostFramebufferCaptured = false
        private set
    var framebufferDirty = false
        private set
    private var appliedFramebufferKnown = false
    private var appliedDrawFramebuffer = -1
    private var appliedReadFramebuffer = -1
    private var appliedViewportKnown = false
    private var appliedViewportX = 0
    private var appliedViewportY = 0
    private var appliedViewportWidth = 0
    private var appliedViewportHeight = 0

    // Scissor state
    var scissorEnabled = false
        private set
    private var scissorX = 0
    private var scissorY = 0
    private var scissorWidth = 0
    private var scissorHeight = 0
    var scissorBoxDirty = false
        private set

    fun beginFrame() {
        targetDepth = 0
        logicalDrawFramebuffer = HOST_FBO_SENTINEL
        logicalReadFramebuffer = HOST_FBO_SENTINEL
        hostViewportKnown = false
        hostFramebufferCaptured = false
        framebufferDirty = false
        appliedFramebufferKnown = false
        appliedViewportKnown = false
        scissorEnabled = false
        scissorBoxDirty = false
    }

    fun endOwnedFrame() {
        if (scissorEnabled) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST)
            scissorEnabled = false
        }
        if (hostFramebufferCaptured) {
            logicalDrawFramebuffer = HOST_FBO_SENTINEL
            logicalReadFramebuffer = HOST_FBO_SENTINEL
            framebufferDirty = true
            flushFramebuffer()
        }
    }

    fun resetAfterFrame() {
        targetDepth = 0
        hostFramebufferCaptured = false
        hostViewportKnown = false
        framebufferDirty = false
        scissorBoxDirty = false
        appliedFramebufferKnown = false
        appliedViewportKnown = false
    }

    fun invalidateApplied() {
        appliedFramebufferKnown = false
        appliedViewportKnown = false
    }

    fun markDirtyAfterExternal(frameActive: Boolean) {
        if (frameActive && (targetDepth > 0 || hostFramebufferCaptured)) {
            framebufferDirty = true
        }
        if (scissorEnabled) scissorBoxDirty = true
    }

    fun markHostFramebufferCaptured() {
        hostFramebufferCaptured = true
    }

    fun pushTarget(fbo: Int, width: Int, height: Int) {
        if (targetDepth == targetBindings.size) targetBindings.add(IntArray(6))
        val previous = targetBindings[targetDepth]
        previous[0] = logicalDrawFramebuffer
        previous[1] = logicalReadFramebuffer
        previous[2] = viewportX
        previous[3] = viewportY
        previous[4] = viewportWidth
        previous[5] = viewportHeight
        targetDepth++

        logicalDrawFramebuffer = fbo
        logicalReadFramebuffer = fbo
        viewportX = 0
        viewportY = 0
        viewportWidth = width
        viewportHeight = height
        framebufferDirty = true
        if (scissorEnabled) scissorBoxDirty = true
    }

    fun popTarget() {
        check(targetDepth > 0) { "No render target to end" }
        val previous = targetBindings[--targetDepth]
        logicalDrawFramebuffer = previous[0]
        logicalReadFramebuffer = previous[1]
        viewportX = previous[2]
        viewportY = previous[3]
        viewportWidth = previous[4]
        viewportHeight = previous[5]
        framebufferDirty = !(logicalDrawFramebuffer == HOST_FBO_SENTINEL && !hostFramebufferCaptured)
        if (scissorEnabled) scissorBoxDirty = true
    }

    fun ensureHostViewportKnown() {
        if (hostViewportKnown) return
        val viewport = stateGuard.viewportBuffer
        if (stateGuard.preserveState) {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport)
        } else {
            if (!Luma.platform.getViewport(viewport)) GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport)
        }
        val snapshot = stateGuard.snapshot
        snapshot.viewportX = viewport[0]
        snapshot.viewportY = viewport[1]
        snapshot.viewportWidth = viewport[2]
        snapshot.viewportHeight = viewport[3]
        hostViewportKnown = true
    }

    fun captureHostFramebuffer() {
        if (hostFramebufferCaptured) return
        ensureHostViewportKnown()
        val snapshot = stateGuard.snapshot
        if (stateGuard.preserveState) {
            snapshot.drawFramebuffer = stateGuard.getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
            snapshot.readFramebuffer = stateGuard.getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
            stateGuard.markDirty(GlStateGuard.DIRTY_FRAMEBUFFER)
        } else {
            snapshot.drawFramebuffer = config.ownedDrawFramebuffer
            snapshot.readFramebuffer = config.ownedReadFramebuffer
        }
        appliedDrawFramebuffer = snapshot.drawFramebuffer
        appliedReadFramebuffer = snapshot.readFramebuffer
        appliedFramebufferKnown = true
        appliedViewportX = snapshot.viewportX
        appliedViewportY = snapshot.viewportY
        appliedViewportWidth = snapshot.viewportWidth
        appliedViewportHeight = snapshot.viewportHeight
        appliedViewportKnown = true
        hostFramebufferCaptured = true
    }

    fun flushFramebuffer() {
        if (!framebufferDirty) return
        if (!hostFramebufferCaptured) {
            captureHostFramebuffer()
        }
        val snapshot = stateGuard.snapshot
        val drawFbo: Int
        val readFbo: Int
        val vx: Int
        val vy: Int
        val vw: Int
        val vh: Int
        if (logicalDrawFramebuffer == HOST_FBO_SENTINEL) {
            drawFbo = snapshot.drawFramebuffer
            readFbo = snapshot.readFramebuffer
            vx = snapshot.viewportX
            vy = snapshot.viewportY
            vw = snapshot.viewportWidth
            vh = snapshot.viewportHeight
        } else {
            drawFbo = logicalDrawFramebuffer
            readFbo = logicalReadFramebuffer
            vx = viewportX
            vy = viewportY
            vw = viewportWidth
            vh = viewportHeight
        }
        if (!appliedFramebufferKnown || appliedDrawFramebuffer != drawFbo || appliedReadFramebuffer != readFbo) {
            if (drawFbo == readFbo) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, drawFbo)
            } else {
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo)
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo)
            }
            appliedDrawFramebuffer = drawFbo
            appliedReadFramebuffer = readFbo
            appliedFramebufferKnown = true
        }
        if (
            !appliedViewportKnown ||
            appliedViewportX != vx ||
            appliedViewportY != vy ||
            appliedViewportWidth != vw ||
            appliedViewportHeight != vh
        ) {
            GL11.glViewport(vx, vy, vw, vh)
            appliedViewportX = vx
            appliedViewportY = vy
            appliedViewportWidth = vw
            appliedViewportHeight = vh
            appliedViewportKnown = true
        }
        framebufferDirty = false
    }

    fun markBlitBoundFramebuffers(readFbo: Int, drawFbo: Int) {
        appliedReadFramebuffer = readFbo
        appliedDrawFramebuffer = drawFbo
        appliedFramebufferKnown = true
        framebufferDirty = true
    }

    inline fun setScissor(
        frameActive: Boolean,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        flushPending: () -> Unit
    ) {
        if (!validateAndCheckScissorChanged(x, y, width, height)) return
        flushPending()
        applyScissor(frameActive, x, y, width, height)
    }

    @PublishedApi
    internal fun validateAndCheckScissorChanged(x: Int, y: Int, width: Int, height: Int): Boolean {
        val activeViewportWidth: Int
        val activeViewportHeight: Int
        if (logicalDrawFramebuffer == HOST_FBO_SENTINEL) {
            ensureHostViewportKnown()
            activeViewportWidth = stateGuard.snapshot.viewportWidth
            activeViewportHeight = stateGuard.snapshot.viewportHeight
        } else {
            activeViewportWidth = viewportWidth
            activeViewportHeight = viewportHeight
        }
        require(x >= 0 && y >= 0 && width > 0 && height > 0)
        require(x + width <= activeViewportWidth && y + height <= activeViewportHeight) {
            "Scissor [$x, $y, $width, $height] exceeds viewport [$activeViewportWidth, $activeViewportHeight]"
        }
        return !(scissorEnabled && scissorX == x && scissorY == y && scissorWidth == width && scissorHeight == height)
    }

    @PublishedApi
    internal fun applyScissor(frameActive: Boolean, x: Int, y: Int, width: Int, height: Int) {
        scissorX = x
        scissorY = y
        scissorWidth = width
        scissorHeight = height
        if (!scissorEnabled) {
            val savedNow = stateGuard.saveScissorEnableIfNeeded(frameActive)
            if (!savedNow || !stateGuard.snapshot.scissorEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST)
            }
            scissorEnabled = true
        }
        stateGuard.saveScissorBoxIfNeeded(frameActive)
        scissorBoxDirty = true
        flushScissor()
    }

    inline fun disableScissor(frameActive: Boolean, flushPending: () -> Unit) {
        if (!scissorEnabled) {
            ensureHostScissorDisabledIfInactive(frameActive)
            return
        }
        flushPending()
        applyDisableScissor(frameActive)
    }

    @PublishedApi
    internal fun ensureHostScissorDisabledIfInactive(frameActive: Boolean) {
        stateGuard.ensureHostScissorDisabled(frameActive, false)
    }

    @PublishedApi
    internal fun applyDisableScissor(frameActive: Boolean) {
        stateGuard.saveScissorEnableIfNeeded(frameActive)
        GL11.glDisable(GL11.GL_SCISSOR_TEST)
        scissorEnabled = false
        scissorBoxDirty = false
    }

    fun flushScissor() {
        if (!scissorBoxDirty) return
        scissorBoxDirty = false
        if (!scissorEnabled) return
        val vx: Int
        val vy: Int
        val vh: Int
        if (logicalDrawFramebuffer == HOST_FBO_SENTINEL) {
            ensureHostViewportKnown()
            vx = stateGuard.snapshot.viewportX
            vy = stateGuard.snapshot.viewportY
            vh = stateGuard.snapshot.viewportHeight
        } else {
            vx = viewportX
            vy = viewportY
            vh = viewportHeight
        }
        GL11.glScissor(
            vx + scissorX,
            vy + vh - scissorY - scissorHeight,
            scissorWidth,
            scissorHeight
        )
    }

    inline fun withoutScissor(action: () -> Unit) {
        if (scissorEnabled) GL11.glDisable(GL11.GL_SCISSOR_TEST)
        try {
            action()
        } finally {
            if (scissorEnabled) GL11.glEnable(GL11.GL_SCISSOR_TEST)
        }
    }

    fun blit(
        frameActive: Boolean,
        source: GlRenderTarget,
        destination: GlRenderTarget?,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int,
        dstX: Int,
        dstY: Int,
        dstWidth: Int,
        dstHeight: Int,
        filter: TextureFilter,
        copyColor: Boolean,
        copyDepth: Boolean
    ) {
        require(copyColor || copyDepth) { "blitRenderTarget must copy at least color or depth" }
        require(srcWidth > 0 && srcHeight > 0 && dstWidth > 0 && dstHeight > 0) {
            "Blit dimensions must be positive"
        }
        require(srcX >= 0 && srcY >= 0 && srcX + srcWidth <= source.width && srcY + srcHeight <= source.height) {
            "Source blit region [$srcX, $srcY, ${srcWidth}x${srcHeight}] exceeds source [${source.width}x${source.height}]"
        }
        require(dstX >= 0 && dstY >= 0) { "Destination blit origin must be non-negative: [$dstX, $dstY]" }
        if (destination != null) {
            require(dstX + dstWidth <= destination.width && dstY + dstHeight <= destination.height) {
                "Destination blit region [$dstX, $dstY, ${dstWidth}x${dstHeight}] exceeds destination [${destination.width}x${destination.height}]"
            }
        }
        if (copyDepth) {
            require(source.depth != null) { "Source render target has no depth attachment" }
            if (destination != null) {
                require(destination.depth != null) { "Destination render target has no depth attachment" }
            }
        }

        val glSrcY0 = source.height - srcY - srcHeight
        val glSrcY1 = source.height - srcY
        val drawFbo: Int
        val glDstX0: Int
        val glDstY0: Int
        val glDstX1: Int
        val glDstY1: Int
        var prevDrawFbo = 0
        var prevReadFbo = 0
        var prevScissorOutside = false

        if (frameActive) {
            captureHostFramebuffer()
            if (destination != null) {
                drawFbo = destination.fbo
                glDstX0 = dstX
                glDstX1 = dstX + dstWidth
                glDstY0 = destination.height - dstY - dstHeight
                glDstY1 = destination.height - dstY
            } else {
                val snap = stateGuard.snapshot
                drawFbo = snap.drawFramebuffer
                glDstX0 = snap.viewportX + dstX
                glDstX1 = glDstX0 + dstWidth
                glDstY0 = snap.viewportY + snap.viewportHeight - dstY - dstHeight
                glDstY1 = glDstY0 + dstHeight
            }
            if (stateGuard.preserveState) {
                stateGuard.ensureHostScissorDisabled(frameActive, scissorEnabled)
            }
        } else {
            prevDrawFbo = stateGuard.getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
            prevReadFbo = stateGuard.getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
            prevScissorOutside = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
            if (destination != null) {
                drawFbo = destination.fbo
                glDstX0 = dstX
                glDstX1 = dstX + dstWidth
                glDstY0 = destination.height - dstY - dstHeight
                glDstY1 = destination.height - dstY
            } else {
                GL11.glGetIntegerv(GL11.GL_VIEWPORT, stateGuard.viewportBuffer)
                drawFbo = prevDrawFbo
                glDstX0 = stateGuard.viewportBuffer[0] + dstX
                glDstX1 = glDstX0 + dstWidth
                glDstY0 = stateGuard.viewportBuffer[1] + stateGuard.viewportBuffer[3] - dstY - dstHeight
                glDstY1 = glDstY0 + dstHeight
            }
        }

        val glFilter = if (filter == TextureFilter.NEAREST) GL11.GL_NEAREST else GL11.GL_LINEAR
        val srcX1 = srcX + srcWidth
        if (frameActive) {
            withoutScissor {
                executeBlitFramebuffer(
                    source.fbo, drawFbo, srcX, glSrcY0, srcX1, glSrcY1, glDstX0, glDstY0, glDstX1, glDstY1,
                    copyColor, copyDepth, glFilter
                )
            }
            markBlitBoundFramebuffers(source.fbo, drawFbo)
        } else {
            if (prevScissorOutside) GL11.glDisable(GL11.GL_SCISSOR_TEST)
            try {
                executeBlitFramebuffer(
                    source.fbo, drawFbo, srcX, glSrcY0, srcX1, glSrcY1, glDstX0, glDstY0, glDstX1, glDstY1,
                    copyColor, copyDepth, glFilter
                )
            } finally {
                if (prevScissorOutside) GL11.glEnable(GL11.GL_SCISSOR_TEST)
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFbo)
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo)
            }
        }
    }

    private fun executeBlitFramebuffer(
        readFbo: Int,
        drawFbo: Int,
        srcX0: Int,
        srcY0: Int,
        srcX1: Int,
        srcY1: Int,
        dstX0: Int,
        dstY0: Int,
        dstX1: Int,
        dstY1: Int,
        copyColor: Boolean,
        copyDepth: Boolean,
        glFilter: Int
    ) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo)
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo)
        if (copyColor && copyDepth && glFilter == GL11.GL_LINEAR) {
            GL30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR)
            GL30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST)
        } else {
            var mask = 0
            if (copyColor) mask = mask or GL11.GL_COLOR_BUFFER_BIT
            if (copyDepth) mask = mask or GL11.GL_DEPTH_BUFFER_BIT
            val effectiveFilter = if (copyDepth) GL11.GL_NEAREST else glFilter
            GL30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, effectiveFilter)
        }
    }

    private companion object {
        const val HOST_FBO_SENTINEL = -2
    }
}
