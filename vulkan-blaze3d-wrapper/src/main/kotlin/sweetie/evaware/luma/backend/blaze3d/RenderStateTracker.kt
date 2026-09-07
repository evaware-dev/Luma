package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.platform.CompareOp
import sweetie.evaware.luma.api.BlendFunction

internal class RenderStateTracker {
    var blendEnabled = true
        private set
    var blendFunction = BlendFunction.TRANSLUCENT
        private set
    var depthEnabled = false
        private set
    var depthWrite = false
        private set
    var depthFunc = CompareOp.ALWAYS_PASS
        private set
    var cullEnabled = false
        private set

    fun beginFrame() {
        blendEnabled = true
        blendFunction = BlendFunction.TRANSLUCENT
        depthEnabled = false
        depthWrite = false
        depthFunc = CompareOp.ALWAYS_PASS
        cullEnabled = false
    }

    fun blend(enabled: Boolean) {
        blendEnabled = enabled
    }

    fun blendFunction(function: BlendFunction) {
        blendFunction = function
    }

    fun depthTest(enabled: Boolean) {
        depthEnabled = enabled
    }

    fun depthWrite(enabled: Boolean) {
        depthWrite = enabled
    }

    fun depthCompare(compare: CompareOp) {
        depthFunc = compare
    }

    fun cull(enabled: Boolean) {
        cullEnabled = enabled
    }
}
