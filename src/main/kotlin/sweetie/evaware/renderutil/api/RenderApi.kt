package sweetie.evaware.renderutil.api

import sweetie.evaware.renderutil.helper.ColorUtil

interface RenderApi {
    fun rect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Int = ColorUtil.WHITE,
        pipeline: RenderPipeline = RenderPipeline.GUI
    )

    fun texture(
        id: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Int = ColorUtil.WHITE,
        pipeline: RenderPipeline = RenderPipeline.GUI
    )

    fun roundedRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        color: Int = ColorUtil.WHITE,
        pipeline: RenderPipeline = RenderPipeline.GUI
    )

    fun scissor(x: Float, y: Float, width: Float, height: Float, action: () -> Unit)
}
