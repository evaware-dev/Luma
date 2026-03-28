package sweetie.evaware.renderutil.font

import sweetie.evaware.msdf.MsdfFont
import sweetie.evaware.renderutil.RenderUtil
import sweetie.evaware.renderutil.api.RenderPipeline
import sweetie.evaware.renderutil.helper.ColorUtil
import kotlin.math.max

fun MsdfFont.priority(pipeline: RenderPipeline = RenderPipeline.GUI) = RenderUtil.text(this, pipeline)

fun MsdfFont.draw(
    text: String,
    x: Float,
    y: Float,
    size: Float,
    color: Int = ColorUtil.WHITE,
    pipeline: RenderPipeline = RenderPipeline.GUI
) {
    priority(pipeline)
        .color(color)
        .size(size)
        .draw(text, x, y)
}

fun MsdfFont.width(text: String, size: Float): Float {
    var lineWidth = 0f
    var maxWidth = 0f
    var index = 0

    while (index < text.length) {
        val code = text.codePointAt(index)
        index += Character.charCount(code)

        if (code == '\n'.code) {
            maxWidth = max(maxWidth, lineWidth)
            lineWidth = 0f
            continue
        }

        val glyph = glyph(code) ?: continue
        lineWidth += glyph.advance * size
    }

    return max(maxWidth, lineWidth)
}
