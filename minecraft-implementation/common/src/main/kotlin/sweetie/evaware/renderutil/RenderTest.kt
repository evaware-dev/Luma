package sweetie.evaware.renderutil

import sweetie.evaware.luma.LumaAssets
import sweetie.evaware.renderutil.api.RenderPipeline
import sweetie.evaware.renderutil.helper.ColorUtil

object RenderTest {
    private val accentColor = ColorUtil.rgba(184, 154, 243, 255)
    private val pipeline = RenderPipeline.GUI

    private var texturesRegistered = false

    fun renderGui() {
        if (!texturesRegistered) {
            RenderUtil.registerTexture(LumaAssets.DEMO_ICON_ID, LumaAssets.DEMO_ICON)
            RenderUtil.registerTexture(LumaAssets.DEMO_CHECKER_ID) {
                val image = java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                for (x in 0 until 16) {
                    for (y in 0 until 16) {
                        val checker = ((x / 4) + (y / 4)) and 1
                        image.setRGB(x, y, if (checker == 0) -0x1 else -0x454546)
                    }
                }
                image
            }
            texturesRegistered = true
        }
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
