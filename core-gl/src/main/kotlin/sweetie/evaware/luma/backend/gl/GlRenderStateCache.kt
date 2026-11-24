package sweetie.evaware.luma.backend.gl

import sweetie.evaware.luma.api.BlendFunction
import sweetie.evaware.luma.api.DepthCompare
import sweetie.evaware.luma.api.StencilCompare
import sweetie.evaware.luma.api.StencilOp

internal class GlRenderStateCache {
    private var blendEnabled: Byte = UNKNOWN
    private var blendFunction: BlendFunction? = null
    private var colorMaskBits: Byte = UNKNOWN
    private var depthEnabled: Byte = UNKNOWN
    private var depthWriteEnabled: Byte = UNKNOWN
    private var depthCompare: DepthCompare? = null
    private var stencilEnabled: Byte = UNKNOWN
    private var stencilCompare: StencilCompare? = null
    private var stencilRef: Int = 0
    private var stencilReadMask: Int = 0xFF
    private var stencilFail: StencilOp? = null
    private var stencilDepthFail: StencilOp? = null
    private var stencilPass: StencilOp? = null
    private var stencilWriteMask: Int = UNKNOWN_INT
    private var cullEnabled: Byte = UNKNOWN

    val isDepthTestEnabled: Boolean
        get() = depthEnabled == TRUE

    val isDepthWriteEnabled: Boolean
        get() = depthWriteEnabled == TRUE

    val currentDepthCompare: DepthCompare
        get() = depthCompare ?: DepthCompare.ALWAYS

    val currentColorMaskBits: Int
        get() = if (colorMaskBits == UNKNOWN) FULL_COLOR_MASK else colorMaskBits.toInt()

    val colorMaskRed: Boolean
        get() = (currentColorMaskBits and COLOR_RED_BIT) != 0

    val colorMaskGreen: Boolean
        get() = (currentColorMaskBits and COLOR_GREEN_BIT) != 0

    val colorMaskBlue: Boolean
        get() = (currentColorMaskBits and COLOR_BLUE_BIT) != 0

    val colorMaskAlpha: Boolean
        get() = (currentColorMaskBits and COLOR_ALPHA_BIT) != 0

    val currentStencilWriteMask: Int
        get() = if (stencilWriteMask == UNKNOWN_INT) 0xFF else stencilWriteMask

    fun resetGui() {
        blendEnabled = TRUE
        blendFunction = BlendFunction.TRANSLUCENT
        colorMaskBits = FULL_COLOR_MASK.toByte()
        depthEnabled = FALSE
        depthWriteEnabled = FALSE
        depthCompare = DepthCompare.ALWAYS
        stencilEnabled = FALSE
        stencilCompare = null
        stencilFail = null
        stencilDepthFail = null
        stencilPass = null
        stencilWriteMask = UNKNOWN_INT
        cullEnabled = FALSE
    }

    fun invalidate() {
        blendEnabled = UNKNOWN
        blendFunction = null
        colorMaskBits = UNKNOWN
        depthEnabled = UNKNOWN
        depthWriteEnabled = UNKNOWN
        depthCompare = null
        stencilEnabled = UNKNOWN
        stencilCompare = null
        stencilFail = null
        stencilDepthFail = null
        stencilPass = null
        stencilWriteMask = UNKNOWN_INT
        cullEnabled = UNKNOWN
    }

    fun blend(enabled: Boolean): Boolean {
        val next = if (enabled) TRUE else FALSE
        if (blendEnabled == next) return false
        blendEnabled = next
        return true
    }

    fun blendFunction(function: BlendFunction): Boolean {
        if (blendFunction == function) return false
        blendFunction = function
        return true
    }

    fun colorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean): Boolean {
        var bits = 0
        if (red) bits = bits or COLOR_RED_BIT
        if (green) bits = bits or COLOR_GREEN_BIT
        if (blue) bits = bits or COLOR_BLUE_BIT
        if (alpha) bits = bits or COLOR_ALPHA_BIT
        val next = bits.toByte()
        if (colorMaskBits == next) return false
        colorMaskBits = next
        return true
    }

    fun depthTest(enabled: Boolean): Boolean {
        val next = if (enabled) TRUE else FALSE
        if (depthEnabled == next) return false
        depthEnabled = next
        return true
    }

    fun depthWrite(enabled: Boolean): Boolean {
        val next = if (enabled) TRUE else FALSE
        if (depthWriteEnabled == next) return false
        depthWriteEnabled = next
        return true
    }

    fun depthCompare(compare: DepthCompare): Boolean {
        if (depthCompare == compare) return false
        depthCompare = compare
        return true
    }

    fun stencilTest(enabled: Boolean): Boolean {
        val next = if (enabled) TRUE else FALSE
        if (stencilEnabled == next) return false
        stencilEnabled = next
        return true
    }

    fun stencilFunc(compare: StencilCompare, ref: Int, readMask: Int): Boolean {
        if (stencilCompare == compare && stencilRef == ref && stencilReadMask == readMask) return false
        stencilCompare = compare
        stencilRef = ref
        stencilReadMask = readMask
        return true
    }

    fun stencilOp(fail: StencilOp, depthFail: StencilOp, pass: StencilOp): Boolean {
        if (stencilFail == fail && stencilDepthFail == depthFail && stencilPass == pass) return false
        stencilFail = fail
        stencilDepthFail = depthFail
        stencilPass = pass
        return true
    }

    fun stencilMask(writeMask: Int): Boolean {
        if (stencilWriteMask == writeMask) return false
        stencilWriteMask = writeMask
        return true
    }

    fun cull(enabled: Boolean): Boolean {
        val next = if (enabled) TRUE else FALSE
        if (cullEnabled == next) return false
        cullEnabled = next
        return true
    }

    companion object {
        private const val UNKNOWN: Byte = -1
        private const val FALSE: Byte = 0
        private const val TRUE: Byte = 1
        private const val UNKNOWN_INT = Int.MIN_VALUE

        const val COLOR_RED_BIT = 1 shl 0
        const val COLOR_GREEN_BIT = 1 shl 1
        const val COLOR_BLUE_BIT = 1 shl 2
        const val COLOR_ALPHA_BIT = 1 shl 3
        const val FULL_COLOR_MASK = COLOR_RED_BIT or COLOR_GREEN_BIT or COLOR_BLUE_BIT or COLOR_ALPHA_BIT
    }
}
