package sweetie.evaware.luma.backend.gl

import sweetie.evaware.luma.api.BlendFunction
import sweetie.evaware.luma.api.DepthCompare

internal class GlRenderStateCache {
    private var blendEnabled: Boolean? = null
    private var blendFunction: BlendFunction? = null
    private var depthEnabled: Boolean? = null
    private var depthWriteEnabled: Boolean? = null
    private var depthCompare: DepthCompare? = null
    private var cullEnabled: Boolean? = null

    fun resetGui() {
        blendEnabled = true
        blendFunction = BlendFunction.TRANSLUCENT
        depthEnabled = false
        depthWriteEnabled = false
        depthCompare = DepthCompare.ALWAYS
        cullEnabled = false
    }

    fun invalidate() {
        blendEnabled = null
        blendFunction = null
        depthEnabled = null
        depthWriteEnabled = null
        depthCompare = null
        cullEnabled = null
    }

    fun blend(enabled: Boolean): Boolean {
        if (blendEnabled == enabled) return false
        blendEnabled = enabled
        return true
    }

    fun blendFunction(function: BlendFunction): Boolean {
        if (blendFunction == function) return false
        blendFunction = function
        return true
    }

    fun depthTest(enabled: Boolean): Boolean {
        if (depthEnabled == enabled) return false
        depthEnabled = enabled
        return true
    }

    fun depthWrite(enabled: Boolean): Boolean {
        if (depthWriteEnabled == enabled) return false
        depthWriteEnabled = enabled
        return true
    }

    fun depthCompare(compare: DepthCompare): Boolean {
        if (depthCompare == compare) return false
        depthCompare = compare
        return true
    }

    fun cull(enabled: Boolean): Boolean {
        if (cullEnabled == enabled) return false
        cullEnabled = enabled
        return true
    }
}
