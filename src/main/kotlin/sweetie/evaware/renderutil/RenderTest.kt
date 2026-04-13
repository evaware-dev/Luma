package sweetie.evaware.renderutil

import sweetie.evaware.luma.wrapper.LumaMetadata
import sweetie.evaware.renderutil.api.RenderPipeline
import sweetie.evaware.renderutil.font.Fonts
import sweetie.evaware.renderutil.font.priority
import sweetie.evaware.renderutil.helper.ColorUtil

object RenderTest {
    private val surfaceColor = ColorUtil.rgba(20, 18, 24, 204)
    private val accentColor = ColorUtil.rgba(184, 154, 243, 255)
    private val textColor = ColorUtil.WHITE
    private val subTextColor = ColorUtil.rgba(212, 212, 220, 255)

    fun renderGui() {
        RenderUtil.RECT
            .priority(RenderPipeline.GUI)
            .color(surfaceColor)
            .draw(8f, 8f, 232f, 104f)
        RenderUtil.RECT
            .priority(RenderPipeline.GUI)
            .color(accentColor)
            .draw(10f, 10f, 228f, 4f)
        RenderUtil.TEXTURE
            .priority(RenderPipeline.GUI)
            .draw("demo_icon", 18f, 52f, 18f, 18f)
        RenderUtil.TEXTURE
            .priority(RenderPipeline.GUI)
            .draw("demo_checker", 44f, 52f, 18f, 18f)
        Fonts.GOOGLE_SANS_MEDIUM
            .priority(RenderPipeline.GUI)
            .color(textColor)
            .size(18f)
            .draw(LumaMetadata.displayName, 18f, 20f)
        Fonts.GOOGLE_SANS
            .priority(RenderPipeline.GUI)
            .color(subTextColor)
            .size(14f)
            .draw("dc ${RenderStats.lastDrawCalls} | bt ${RenderStats.lastBatches}", 18f, 84f)
        RenderUtil.flush(RenderPipeline.GUI)
    }
}
