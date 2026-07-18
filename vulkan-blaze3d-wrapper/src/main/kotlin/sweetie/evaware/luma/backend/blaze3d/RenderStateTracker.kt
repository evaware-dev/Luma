package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.platform.CompareOp

internal object RenderStateTracker {
    var depthEnabled = false
        private set
    var depthWrite = false
        private set
    var depthFunc = CompareOp.ALWAYS_PASS
        private set
    var cullEnabled = false
        private set

    fun beginFrame() {
        depthEnabled = false
        depthWrite = false
        depthFunc = CompareOp.ALWAYS_PASS
        cullEnabled = false
    }

    fun depthTest(enabled: Boolean) {
        depthEnabled = enabled
    }

    fun cull(enabled: Boolean) {
        cullEnabled = enabled
    }
}
