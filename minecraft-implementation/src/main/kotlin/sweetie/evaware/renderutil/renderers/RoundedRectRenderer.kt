package sweetie.evaware.renderutil.renderers

import sweetie.evaware.renderutil.RenderUtil
import sweetie.evaware.renderutil.api.BatchRenderer
import sweetie.evaware.renderutil.api.RenderPipeline
import sweetie.evaware.renderutil.helper.ColorUtil

class RoundedRectRenderer : BatchRenderer, AutoCloseable {
    private val renderers = Array(RenderPipeline.entries.size) { RectQuadsRenderer() }
    private var pendingMask = 0

    private var pipeline = RenderPipeline.GUI
    private var color = ColorUtil.WHITE
    private var radius = 0f

    internal fun reset(): RoundedRectRenderer {
        pipeline = RenderPipeline.GUI
        color = ColorUtil.WHITE
        radius = 0f
        return this
    }

    fun priority(pipeline: RenderPipeline) = apply {
        this.pipeline = pipeline
    }

    fun color(color: Int) = apply {
        this.color = color
    }

    fun radius(radius: Float) = apply {
        this.radius = radius
    }

    fun draw(x: Float, y: Float, width: Float, height: Float) {
        RenderUtil.useRoundedBatch(pipeline)
        renderers[pipeline.ordinal].rect(x, y, width, height, color, radius)
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
