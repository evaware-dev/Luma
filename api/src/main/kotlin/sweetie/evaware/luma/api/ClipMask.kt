package sweetie.evaware.luma.api

import sweetie.evaware.luma.Luma

enum class ClipMode {
    INSIDE,
    OUTSIDE
}

/**
 * Cross-backend hardware Early-Z clip mask using the depth buffer.
 *
 * Works on both OpenGL and Vulkan with arbitrary float geometry and any user shader
 * (including fragment `discard` SDF masks), rejecting clipped fragments via hardware
 * Early-Z before fragment shading. Requires a render target with a depth attachment.
 */
object ClipMask {
    private const val COLOR_RED_BIT = 1 shl 0
    private const val COLOR_GREEN_BIT = 1 shl 1
    private const val COLOR_BLUE_BIT = 1 shl 2
    private const val COLOR_ALPHA_BIT = 1 shl 3
    private const val DEPTH_TEST_BIT = 1 shl 4
    private const val DEPTH_WRITE_BIT = 1 shl 5
    private const val DEPTH_COMPARE_SHIFT = 6
    private const val DEPTH_COMPARE_MASK = 0x7

    private val DEPTH_COMPARES = DepthCompare.entries.toTypedArray()

    private var savedSessionState = 0
    private var inSession = false

    @PublishedApi
    internal fun captureState(backend: StateBackend): Int {
        var bits = 0
        if (backend.colorMaskRed) bits = bits or COLOR_RED_BIT
        if (backend.colorMaskGreen) bits = bits or COLOR_GREEN_BIT
        if (backend.colorMaskBlue) bits = bits or COLOR_BLUE_BIT
        if (backend.colorMaskAlpha) bits = bits or COLOR_ALPHA_BIT
        if (backend.isDepthTestEnabled) bits = bits or DEPTH_TEST_BIT
        if (backend.isDepthWriteEnabled) bits = bits or DEPTH_WRITE_BIT
        bits = bits or (backend.currentDepthCompare.ordinal shl DEPTH_COMPARE_SHIFT)
        return bits
    }

    @PublishedApi
    internal fun restoreState(backend: StateBackend, state: Int) {
        backend.colorMask(
            (state and COLOR_RED_BIT) != 0,
            (state and COLOR_GREEN_BIT) != 0,
            (state and COLOR_BLUE_BIT) != 0,
            (state and COLOR_ALPHA_BIT) != 0
        )
        backend.depthTest((state and DEPTH_TEST_BIT) != 0)
        backend.depthWrite((state and DEPTH_WRITE_BIT) != 0)
        backend.depthCompare(DEPTH_COMPARES[(state ushr DEPTH_COMPARE_SHIFT) and DEPTH_COMPARE_MASK])
    }

    @PublishedApi
    internal fun applyWriteState(backend: StateBackend, clear: Boolean) {
        if (clear) backend.clearDepth(1.0)
        backend.colorMask(false, false, false, false)
        backend.depthTest(true)
        backend.depthWrite(true)
        backend.depthCompare(DepthCompare.ALWAYS)
    }

    @PublishedApi
    internal fun applyTestState(backend: StateBackend, mode: ClipMode) {
        backend.colorMask(true, true, true, true)
        backend.depthTest(true)
        backend.depthWrite(false)
        backend.depthCompare(
            when (mode) {
                ClipMode.INSIDE -> DepthCompare.EQUAL
                ClipMode.OUTSIDE -> DepthCompare.LESS
            }
        )
    }

    inline fun write(
        clear: Boolean = true,
        backend: StateBackend = Luma.backend,
        action: () -> Unit
    ) {
        val saved = captureState(backend)
        try {
            applyWriteState(backend, clear)
            action()
        } finally {
            restoreState(backend, saved)
        }
    }

    inline fun inside(
        backend: StateBackend = Luma.backend,
        action: () -> Unit
    ) {
        val saved = captureState(backend)
        try {
            applyTestState(backend, ClipMode.INSIDE)
            action()
        } finally {
            restoreState(backend, saved)
        }
    }

    inline fun outside(
        backend: StateBackend = Luma.backend,
        action: () -> Unit
    ) {
        val saved = captureState(backend)
        try {
            applyTestState(backend, ClipMode.OUTSIDE)
            action()
        } finally {
            restoreState(backend, saved)
        }
    }

    inline fun clip(
        mode: ClipMode = ClipMode.INSIDE,
        clear: Boolean = true,
        backend: StateBackend = Luma.backend,
        mask: () -> Unit,
        content: () -> Unit
    ) {
        val saved = captureState(backend)
        try {
            applyWriteState(backend, clear)
            mask()
            applyTestState(backend, mode)
            content()
        } finally {
            restoreState(backend, saved)
        }
    }

    fun beginWrite(clear: Boolean = true, backend: StateBackend = Luma.backend) {
        val saved = if (!inSession) captureState(backend) else savedSessionState
        applyWriteState(backend, clear)
        if (!inSession) {
            savedSessionState = saved
            inSession = true
        }
    }

    fun beginTest(mode: ClipMode = ClipMode.INSIDE, backend: StateBackend = Luma.backend) {
        val saved = if (!inSession) captureState(backend) else savedSessionState
        applyTestState(backend, mode)
        if (!inSession) {
            savedSessionState = saved
            inSession = true
        }
    }

    fun end(backend: StateBackend = Luma.backend) {
        check(inSession) { "ClipMask.end() called without matching beginWrite/beginTest" }
        inSession = false
        restoreState(backend, savedSessionState)
    }
}
