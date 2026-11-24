package sweetie.evaware.luma.backend.gl

import sweetie.evaware.luma.api.BlendFunction
import sweetie.evaware.luma.api.DepthCompare

internal class GlRenderStateCache {
    private var blendEnabled: Byte = UNKNOWN
    private var blendFunction: BlendFunction? = null
    private var depthEnabled: Byte = UNKNOWN
    private var depthWriteEnabled: Byte = UNKNOWN
    private var depthCompare: DepthCompare? = null
    private var cullEnabled: Byte = UNKNOWN

    fun resetGui() {
        blendEnabled = TRUE
        blendFunction = BlendFunction.TRANSLUCENT
        depthEnabled = FALSE
        depthWriteEnabled = FALSE
        depthCompare = DepthCompare.ALWAYS
        cullEnabled = FALSE
    }

    fun invalidate() {
        blendEnabled = UNKNOWN
        blendFunction = null
        depthEnabled = UNKNOWN
        depthWriteEnabled = UNKNOWN
        depthCompare = null
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

    fun cull(enabled: Boolean): Boolean {
        val next = if (enabled) TRUE else FALSE
        if (cullEnabled == next) return false
        cullEnabled = next
        return true
    }

    private companion object {
        const val UNKNOWN: Byte = -1
        const val FALSE: Byte = 0
        const val TRUE: Byte = 1
    }
}
