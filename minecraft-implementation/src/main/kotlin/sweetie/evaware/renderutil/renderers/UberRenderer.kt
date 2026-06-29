package sweetie.evaware.renderutil.renderers

import sweetie.evaware.renderutil.api.BatchRenderer
import sweetie.evaware.renderutil.api.RenderPipeline

class UberRenderer : BatchRenderer, AutoCloseable {
    private val renderers = Array(RenderPipeline.entries.size) { UberBatch() }
    private var pendingMask = 0

    override fun load() {
        for (i in renderers.indices) {
            renderers[i].load()
        }
    }

    override fun hasPending() = pendingMask != 0

    fun hasPending(pipeline: RenderPipeline): Boolean {
        val bit = 1 shl pipeline.ordinal
        return (pendingMask and bit) != 0 && renderers[pipeline.ordinal].hasPending()
    }

    override fun flush() {
        var mask = pendingMask
        while (mask != 0) {
            val index = Integer.numberOfTrailingZeros(mask)
            val renderer = renderers[index]
            renderer.flush()

            if (!renderer.hasPending()) {
                pendingMask = pendingMask and (1 shl index).inv()
            }
            mask = mask and (mask - 1)
        }
    }

    fun flush(pipeline: RenderPipeline) {
        val ordinal = pipeline.ordinal
        val bit = 1 shl ordinal
        if ((pendingMask and bit) == 0) return

        renderers[ordinal].flush()
        if (!renderers[ordinal].hasPending()) {
            pendingMask = pendingMask and bit.inv()
        }
    }

    fun rect(pipeline: RenderPipeline, x: Float, y: Float, width: Float, height: Float, color: Int) {
        val ordinal = pipeline.ordinal
        renderers[ordinal].rect(x, y, width, height, color)
        pendingMask = pendingMask or (1 shl ordinal)
    }

    fun texture(pipeline: RenderPipeline, id: String, x: Float, y: Float, width: Float, height: Float, color: Int) {
        val ordinal = pipeline.ordinal
        renderers[ordinal].texture(id, x, y, width, height, color)
        pendingMask = pendingMask or (1 shl ordinal)
    }

    override fun close() {
        for (i in renderers.indices) {
            renderers[i].close()
        }
        pendingMask = 0
    }
}