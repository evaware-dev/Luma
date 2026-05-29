package sweetie.evaware.luma.minecraft

import com.mojang.blaze3d.opengl.GlStateManager
import net.minecraft.client.Minecraft
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.RenderPlatform
import sweetie.evaware.luma.framebuffer.FramebufferHandle

object MinecraftRenderPlatform : RenderPlatform {
    override fun getGuiScaledWidth(): Float = Minecraft.getInstance().window.guiScaledWidth.toFloat()
    override fun getGuiScaledHeight(): Float = Minecraft.getInstance().window.guiScaledHeight.toFloat()
    override fun getGuiScale(): Float = Minecraft.getInstance().window.guiScale.toFloat()
    override fun getWindowHeight(): Float = Minecraft.getInstance().window.height.toFloat()

    override fun getViewport(viewport: IntArray): Boolean {
        val colorTexture = try {
            Minecraft.getInstance().mainRenderTarget.colorTextureView
        } catch (e: Throwable) {
            null
        }
        if (colorTexture != null) {
            viewport[0] = 0
            viewport[1] = 0
            viewport[2] = colorTexture.getWidth(0)
            viewport[3] = colorTexture.getHeight(0)
            return true
        }
        return false
    }

    override fun captureState(snapshot: Luma.GlStateSnapshot) {
        snapshot.drawFramebuffer = GlStateManager.getFrameBuffer(GL30.GL_DRAW_FRAMEBUFFER)
        snapshot.readFramebuffer = GlStateManager.getFrameBuffer(GL30.GL_READ_FRAMEBUFFER)
        snapshot.blendEnabled = GlStateManager.BLEND.mode.enabled
        snapshot.depthEnabled = GlStateManager.DEPTH.mode.enabled
        snapshot.cullEnabled = GlStateManager.CULL.enable.enabled
        snapshot.blendSrcRgb = GlStateManager.BLEND.srcRgb
        snapshot.blendDstRgb = GlStateManager.BLEND.dstRgb
        snapshot.blendSrcAlpha = GlStateManager.BLEND.srcAlpha
        snapshot.blendDstAlpha = GlStateManager.BLEND.dstAlpha
    }

    override fun restoreState(snapshot: Luma.GlStateSnapshot) {
        if (snapshot.blendEnabled) GlStateManager._enableBlend() else GlStateManager._disableBlend()
        if (snapshot.depthEnabled) GlStateManager._enableDepthTest() else GlStateManager._disableDepthTest()
        if (snapshot.cullEnabled) GlStateManager._enableCull() else GlStateManager._disableCull()

        GlStateManager._blendFuncSeparate(
            snapshot.blendSrcRgb,
            snapshot.blendDstRgb,
            snapshot.blendSrcAlpha,
            snapshot.blendDstAlpha
        )
    }

    override fun getBoundTexture2d(): Int {
        val textureIndex = GlStateManager.activeTexture
        if (textureIndex !in GlStateManager.TEXTURES.indices) return 0
        return GlStateManager.TEXTURES[textureIndex].binding
    }

    override fun swapToMainFramebuffer(luma: Luma) {
        val target = Minecraft.getInstance().mainRenderTarget
        val colorTexture = target.colorTextureView ?: return
        luma.bindFramebuffer(
            FramebufferHandle.resolve(colorTexture, target.depthTextureView),
            colorTexture.getWidth(0),
            colorTexture.getHeight(0)
        )
    }

    override fun activeTexture(texture: Int) {
        GlStateManager._activeTexture(texture)
    }

    override fun bindTexture2d(textureId: Int) {
        GlStateManager._bindTexture(textureId)
    }

    override fun useProgram(programId: Int) {
        GlStateManager._glUseProgram(programId)
    }

    override fun bindVertexArray(vertexArrayId: Int) {
        GlStateManager._glBindVertexArray(vertexArrayId)
    }

    override fun bindFramebuffer(target: Int, framebufferId: Int) {
        GlStateManager._glBindFramebuffer(target, framebufferId)
    }

    override fun viewport(x: Int, y: Int, width: Int, height: Int) {
        GlStateManager._viewport(x, y, width, height)
    }
}
