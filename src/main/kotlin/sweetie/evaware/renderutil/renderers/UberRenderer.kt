package sweetie.evaware.renderutil.renderers

import sweetie.evaware.msdf.MsdfFont
import sweetie.evaware.renderutil.api.BatchRenderer
import sweetie.evaware.renderutil.api.RenderPipeline

class UberRenderer : BatchRenderer, AutoCloseable {
    private val renderers = Array(RenderPipeline.entries.size) { UberBatch() }
    private var pendingMask = 0

    override fun load() {
        for (renderer in renderers) {
            renderer.load()
        }
    }

    override fun hasPending() = pendingMask != 0

    fun hasPending(pipeline: RenderPipeline): Boolean {
        val bit = 1 shl pipeline.ordinal
        return pendingMask and bit != 0 && renderers[pipeline.ordinal].hasPending()
    }

    override fun flush() {
        var mask = pendingMask
        while (mask != 0) {
            val index = Integer.numberOfTrailingZeros(mask)
            flush(RenderPipeline.entries[index])
            mask = mask and (mask - 1)
        }
    }

    fun flush(pipeline: RenderPipeline) {
        val bit = 1 shl pipeline.ordinal
        if (pendingMask and bit == 0) return
        renderers[pipeline.ordinal].flush()
        if (!renderers[pipeline.ordinal].hasPending()) {
            pendingMask = pendingMask and bit.inv()
        }
    }

    fun rect(pipeline: RenderPipeline, x: Float, y: Float, width: Float, height: Float, color: Int) {
        renderers[pipeline.ordinal].rect(x, y, width, height, color)
        pendingMask = pendingMask or (1 shl pipeline.ordinal)
    }

    fun texture(pipeline: RenderPipeline, id: String, x: Float, y: Float, width: Float, height: Float, color: Int) {
        renderers[pipeline.ordinal].texture(id, x, y, width, height, color)
        pendingMask = pendingMask or (1 shl pipeline.ordinal)
    }

    fun text(pipeline: RenderPipeline, font: MsdfFont, text: String, x: Float, y: Float, size: Float, color: Int) {
        renderers[pipeline.ordinal].text(font, text, x, y, size, color)
        pendingMask = pendingMask or (1 shl pipeline.ordinal)
    }

    override fun close() {
        for (renderer in renderers) {
            renderer.close()
        }
        pendingMask = 0
    }
}
