package sweetie.evaware.luma.opengl

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import sweetie.evaware.luma.wrapper.RenderApiType
import sweetie.evaware.luma.wrapper.backend.RenderHost
import sweetie.evaware.luma.wrapper.frame.FrameInfo

object OpenGlStandaloneRenderHost : RenderHost {
    private val viewport = BufferUtils.createIntBuffer(4)

    override fun renderType(): RenderApiType = RenderApiType.OPEN_GL

    override fun frameInfo(): FrameInfo {
        viewport.clear()
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport)
        val width = viewport.get(2).toFloat()
        val height = viewport.get(3).toFloat()
        return FrameInfo(
            guiWidth = width,
            guiHeight = height,
            windowWidth = width,
            windowHeight = height,
            guiScale = 1f
        )
    }
}
