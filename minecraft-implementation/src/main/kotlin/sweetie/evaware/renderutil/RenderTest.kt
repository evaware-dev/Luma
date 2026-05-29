package sweetie.evaware.renderutil

import sweetie.evaware.renderutil.api.RenderPipeline
import sweetie.evaware.renderutil.helper.ColorUtil

object RenderTest {
    private val surfaceColor = ColorUtil.rgba(20, 18, 24, 204)
    private val accentColor = ColorUtil.rgba(184, 154, 243, 255)
    private val softColor = ColorUtil.rgba(255, 255, 255, 36)

    fun renderGui() {
        RenderUtil.ROUNDED_RECT
            .priority(RenderPipeline.GUI)
            .color(surfaceColor)
            .radius(8f)
            .draw(8f, 8f, 232f, 104f)
        RenderUtil.ROUNDED_RECT
            .priority(RenderPipeline.GUI)
            .color(accentColor)
            .radius(4f)
            .draw(16f, 18f, 56f, 56f)
        RenderUtil.ROUNDED_RECT
            .priority(RenderPipeline.GUI)
            .color(softColor)
            .radius(6f)
            .draw(86f, 18f, 132f, 56f)
        RenderUtil.TEXTURE
            .priority(RenderPipeline.GUI)
            .draw("demo_icon", 26f, 26f, 40f, 40f)
        RenderUtil.TEXTURE
            .priority(RenderPipeline.GUI)
            .draw("demo_checker", 96f, 28f, 36f, 36f)
        RenderUtil.TEXTURE
            .priority(RenderPipeline.GUI)
            .color(accentColor)
            .draw("demo_checker", 146f, 28f, 36f, 36f)
        RenderUtil.flush(RenderPipeline.GUI)
    }
}
