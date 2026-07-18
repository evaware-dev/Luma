package sweetie.evaware.renderutil

import sweetie.evaware.luma.LumaAssets
import sweetie.evaware.renderutil.api.RenderPipeline
import sweetie.evaware.renderutil.helper.ColorUtil

object RenderTest {
    private val accentColor = ColorUtil.rgba(184, 154, 243, 255)
    private val pipeline = RenderPipeline.GUI

    fun renderGui() {
        val startX = 8f
        val y = 8f
        val width = 40f
        val height = 40f
        val gap = 10f
        val radius = 6f

        for (i in 0 until 5) {
            val currentX = startX + i * (width + gap)

            RenderUtil.ROUNDED_RECT
                .priority(pipeline)
                .color(accentColor)
                .radius(radius)
                .draw(currentX, y, width, height)
        }

        val textureX = startX + 5 * (width + gap)
        RenderUtil.TEXTURE
            .priority(pipeline)
            .draw(LumaAssets.DEMO_CHECKER_ID, textureX, y, width, height)

        RenderUtil.TEXTURE
            .priority(pipeline)
            .radius(radius)
            .draw(LumaAssets.DEMO_ICON_ID, textureX + width + gap, y, width, height)
    }
}
