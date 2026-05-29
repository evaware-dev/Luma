package sweetie.evaware.renderutil.renderers

import sweetie.evaware.renderutil.RenderUtil
import sweetie.evaware.renderutil.api.RenderPipeline
import sweetie.evaware.renderutil.helper.ColorUtil

class TextureRenderer internal constructor(
    private val renderer: UberRenderer
) {
    private var pipeline = RenderPipeline.GUI
    private var color = ColorUtil.WHITE

    internal fun reset(): TextureRenderer {
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
        RenderUtil.useUberBatch(pipeline)
        renderer.texture(pipeline, id, x, y, width, height, color)
    }
}
