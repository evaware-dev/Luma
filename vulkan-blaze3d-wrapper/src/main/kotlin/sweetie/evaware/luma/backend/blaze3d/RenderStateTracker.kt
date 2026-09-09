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

    fun beginFrame() {
        blendEnabled = true
        blendFunction = BlendFunction.TRANSLUCENT
        depthEnabled = false
        depthWrite = false
        depthFunc = CompareOp.ALWAYS_PASS
        cullEnabled = false
        scissorEnabled = false
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

    fun scissor(x: Int, y: Int, width: Int, height: Int) {
        require(x >= 0 && y >= 0 && width > 0 && height > 0)
        scissorEnabled = true
        scissorX = x
        scissorY = y
        scissorWidth = width
        scissorHeight = height
    }

    fun disableScissor() {
        scissorEnabled = false
    }
}
