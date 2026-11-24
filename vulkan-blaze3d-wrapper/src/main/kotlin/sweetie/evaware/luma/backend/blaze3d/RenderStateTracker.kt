package sweetie.evaware.luma.backend.blaze3d

import sweetie.evaware.luma.api.BlendFunction
import sweetie.evaware.luma.api.DepthCompare

internal class RenderStateTracker {
    var blendEnabled = true
        private set
    var blendFunction = BlendFunction.TRANSLUCENT
        private set
    var colorWriteMask = PipelineStateKey.DEFAULT_COLOR_WRITE_MASK
        private set
    var depthEnabled = false
        private set
    var depthWrite = false
        private set
    var depthFunc = DepthCompare.ALWAYS
        private set
    var cullEnabled = false
        private set
    var scissorEnabled = false
        private set
    var scissorX = 0
        private set
    var scissorY = 0
        private set
    var scissorWidth = 0
        private set
    var scissorHeight = 0
        private set

    val colorMaskRed: Boolean get() = (colorWriteMask and PipelineStateKey.COLOR_WRITE_RED) != 0
    val colorMaskGreen: Boolean get() = (colorWriteMask and PipelineStateKey.COLOR_WRITE_GREEN) != 0
    val colorMaskBlue: Boolean get() = (colorWriteMask and PipelineStateKey.COLOR_WRITE_BLUE) != 0
    val colorMaskAlpha: Boolean get() = (colorWriteMask and PipelineStateKey.COLOR_WRITE_ALPHA) != 0

    private var cachedStatePipelineBits: Long = PipelineStateKey.DEFAULT_STATE_BITS
    private var pipelineBitsDirty = false
    val statePipelineBits: Long
        get() {
            if (pipelineBitsDirty) {
                cachedStatePipelineBits = PipelineStateKey.packState(
                    blendEnabled,
                    blendFunction,
                    depthEnabled,
                    depthWrite,
                    depthFunc,
                    cullEnabled,
                    colorWriteMask
                )
                pipelineBitsDirty = false
            }
            return cachedStatePipelineBits
        }
    var scissorXY: Long = 0L
        private set
    var scissorWH: Long = 0L
        private set

    fun beginFrame() {
        blendEnabled = true
        blendFunction = BlendFunction.TRANSLUCENT
        colorWriteMask = PipelineStateKey.DEFAULT_COLOR_WRITE_MASK
        depthEnabled = false
        depthWrite = false
        depthFunc = DepthCompare.ALWAYS
        cullEnabled = false
        scissorEnabled = false
        cachedStatePipelineBits = PipelineStateKey.DEFAULT_STATE_BITS
        pipelineBitsDirty = false
        scissorXY = 0L
        scissorWH = 0L
    }

    fun blend(enabled: Boolean) {
        if (blendEnabled == enabled) return
        blendEnabled = enabled
        pipelineBitsDirty = true
    }

    fun blendFunction(function: BlendFunction) {
        if (blendFunction === function || blendFunction == function) return
        blendFunction = function
        pipelineBitsDirty = true
    }

    fun colorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) {
        var mask = 0
        if (red) mask = mask or PipelineStateKey.COLOR_WRITE_RED
        if (green) mask = mask or PipelineStateKey.COLOR_WRITE_GREEN
        if (blue) mask = mask or PipelineStateKey.COLOR_WRITE_BLUE
        if (alpha) mask = mask or PipelineStateKey.COLOR_WRITE_ALPHA
        if (colorWriteMask == mask) return
        colorWriteMask = mask
        pipelineBitsDirty = true
    }

    fun depthTest(enabled: Boolean) {
        if (depthEnabled == enabled) return
        depthEnabled = enabled
        pipelineBitsDirty = true
    }

    fun depthWrite(enabled: Boolean) {
        if (depthWrite == enabled) return
        depthWrite = enabled
        if (depthEnabled) pipelineBitsDirty = true
    }

    fun depthCompare(compare: DepthCompare) {
        if (depthFunc == compare) return
        depthFunc = compare
        if (depthEnabled) pipelineBitsDirty = true
    }

    fun cull(enabled: Boolean) {
        if (cullEnabled == enabled) return
        cullEnabled = enabled
        pipelineBitsDirty = true
    }

    fun scissor(x: Int, y: Int, width: Int, height: Int) {
        require(x >= 0 && y >= 0 && width > 0 && height > 0)
        scissorEnabled = true
        scissorX = x
        scissorY = y
        scissorWidth = width
        scissorHeight = height
        scissorXY = PipelineStateKey.packScissorXY(x, y)
        scissorWH = PipelineStateKey.packScissorWH(width, height)
    }

    fun disableScissor() {
        if (!scissorEnabled) return
        scissorEnabled = false
        scissorXY = 0L
        scissorWH = 0L
    }
}
