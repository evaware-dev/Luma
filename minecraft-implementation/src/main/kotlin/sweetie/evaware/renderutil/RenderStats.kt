package sweetie.evaware.renderutil

object RenderStats {
    var lastDrawCalls = 0
        private set
    var lastBatches = 0
        private set

    private var drawCalls = 0
    private var batches = 0

    fun beginFrame() {
        lastDrawCalls = drawCalls
        lastBatches = batches
        drawCalls = 0
        batches = 0
    }

    fun markBatch() {
        batches++
    }

    fun markDrawCall() {
        drawCalls++
    }
}
