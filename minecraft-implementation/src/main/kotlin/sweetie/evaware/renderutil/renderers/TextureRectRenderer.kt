package sweetie.evaware.renderutil.renderers

import sweetie.evaware.renderutil.RenderUtil
import sweetie.evaware.renderutil.api.BatchRenderer
import sweetie.evaware.renderutil.api.RenderPipeline
import sweetie.evaware.renderutil.helper.ColorUtil

class TextureRectRenderer : BatchRenderer, AutoCloseable {
    private val renderers = Array(RenderPipeline.entries.size) { TextureRectBatch() }
    private var pendingMask = 0

    private var pipeline = RenderPipeline.GUI
    private var color = ColorUtil.WHITE

    internal fun reset(): TextureRectRenderer {
        pipeline = RenderPipeline.GUI
        color = ColorUtil.WHITE
        return this
    }

    fun priority(pipeline: RenderPipeline) = apply {
        this.pipeline = pipeline
    }

    fun color(color: Int) = apply {
        this.color = color
    }

    fun draw(id: String, x: Float, y: Float, width: Float, height: Float) {
        RenderUtil.useTextureBatch(pipeline)
        renderers[pipeline.ordinal].texture(id, x, y, width, height, color)
        pendingMask = pendingMask or (1 shl pipeline.ordinal)
    }

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

    override fun close() {
        for (renderer in renderers) {
            renderer.close()
        }
        pendingMask = 0
    }
}
