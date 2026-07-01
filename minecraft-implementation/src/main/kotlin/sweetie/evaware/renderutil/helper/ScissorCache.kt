package sweetie.evaware.renderutil.helper

import sweetie.evaware.luma.scissor.ScissorControl
import sweetie.evaware.renderutil.api.BatchRenderer

class ScissorCache {
    var minX = 0f
        private set
    var minY = 0f
        private set
    var maxX = 0f
        private set
    var maxY = 0f
        private set

    private var version = Int.MIN_VALUE

    fun update(batch: BatchRenderer) {
        val current = ScissorControl.version
        if (current == version) return

        if (batch.hasPending()) batch.flush()

        version = current
        minX = ScissorControl.minX
        minY = ScissorControl.minY
        maxX = ScissorControl.maxX
        maxY = ScissorControl.maxY
    }
}
