package sweetie.evaware.renderutil.renderers

import sweetie.evaware.renderutil.RenderUtil
import sweetie.evaware.renderutil.api.RenderPipeline
import sweetie.evaware.renderutil.helper.ColorUtil

class TextureRectRenderer(
    private val renderer: RoundedRectRenderer
) {
    private var pipeline = RenderPipeline.GUI
    private var color = ColorUtil.WHITE
    private var radius = 0f

    internal fun reset(): TextureRectRenderer {
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

    fun draw(id: String, x: Float, y: Float, width: Float, height: Float) {
        RenderUtil.useTextureBatch(pipeline)
        renderer.texture(pipeline, id, x, y, width, height, color, radius)
    }
}
