package sweetie.evaware.luma

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30

interface RenderPlatform {
    fun getGuiScaledWidth(): Float
    fun getGuiScaledHeight(): Float
    fun getGuiScale(): Float
    fun getWindowHeight(): Float
    fun getViewport(viewport: IntArray): Boolean
    fun captureState(snapshot: Luma.GlStateSnapshot)
    fun restoreState(snapshot: Luma.GlStateSnapshot)
    fun getBoundTexture2d(): Int
    fun swapToMainFramebuffer(luma: Luma)
}

object DefaultRenderPlatform : RenderPlatform {
    override fun getGuiScaledWidth(): Float = 960f
    override fun getGuiScaledHeight(): Float = 540f
    override fun getGuiScale(): Float = 1f
    override fun getWindowHeight(): Float = 540f

    override fun getViewport(viewport: IntArray): Boolean = false

    override fun captureState(snapshot: Luma.GlStateSnapshot) {
        snapshot.blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND)
        snapshot.depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        snapshot.cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE)
        snapshot.drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        snapshot.readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
    }

    override fun restoreState(snapshot: Luma.GlStateSnapshot) {
        if (snapshot.blendEnabled) GL11.glEnable(GL11.GL_BLEND) else GL11.glDisable(GL11.GL_BLEND)
        if (snapshot.depthEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST) else GL11.glDisable(GL11.GL_DEPTH_TEST)
        if (snapshot.cullEnabled) GL11.glEnable(GL11.GL_CULL_FACE) else GL11.glDisable(GL11.GL_CULL_FACE)

        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, snapshot.drawFramebuffer)
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, snapshot.readFramebuffer)
    }

    override fun getBoundTexture2d(): Int {
        return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
    }

    override fun swapToMainFramebuffer(luma: Luma) {
        luma.bindFramebuffer(0, 960, 540)
    }
}
