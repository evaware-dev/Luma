package sweetie.evaware.renderutil.renderers

import sweetie.evaware.msdf.MsdfFont
import sweetie.evaware.renderutil.RenderUtil
import sweetie.evaware.renderutil.api.RenderPipeline
import sweetie.evaware.renderutil.helper.ColorUtil

class TextRenderer internal constructor(
    private val renderer: UberRenderer
) {
    private lateinit var font: MsdfFont
    private var pipeline = RenderPipeline.GUI
    private var color = ColorUtil.WHITE
    private var size = 12f

    internal fun reset(font: MsdfFont, pipeline: RenderPipeline): TextRenderer {
        this.font = font
        this.pipeline = pipeline
        color = ColorUtil.WHITE
        size = 12f
        return this
    }

    fun priority(pipeline: RenderPipeline) = apply {
        this.pipeline = pipeline
    }

    fun color(color: Int) = apply {
        this.color = color
    }

    fun size(size: Float) = apply {
        this.size = size
    }

    fun draw(text: String, x: Float, y: Float) {
        RenderUtil.useUberBatch(pipeline)
        renderer.text(pipeline, font, text, x, y, size, color)
    }
}
