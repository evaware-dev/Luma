package sweetie.evaware.renderutil.renderers

import sweetie.evaware.renderutil.RenderUtil
import sweetie.evaware.renderutil.api.RenderPipeline
import sweetie.evaware.renderutil.helper.ColorUtil

class RectRenderer internal constructor(
    private val renderer: UberRenderer
) {
    private var pipeline = RenderPipeline.GUI
    private var color = ColorUtil.WHITE

    internal fun reset(): RectRenderer {
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

    fun draw(x: Float, y: Float, width: Float, height: Float) {
        RenderUtil.useUberBatch(pipeline)
        renderer.rect(pipeline, x, y, width, height, color)
    }
}
