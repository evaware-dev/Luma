package sweetie.evaware.luma.minecraft

import com.mojang.blaze3d.opengl.GlStateManager
import net.minecraft.client.Minecraft
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL33
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
        if (snapshot.blendEnabled) {
            GL11.glEnable(GL11.GL_BLEND)
            GlStateManager._enableBlend()
        } else {
            GL11.glDisable(GL11.GL_BLEND)
            GlStateManager._disableBlend()
        }

        if (snapshot.depthEnabled) {
            GL11.glEnable(GL11.GL_DEPTH_TEST)
            GlStateManager._enableDepthTest()
        } else {
            GL11.glDisable(GL11.GL_DEPTH_TEST)
            GlStateManager._disableDepthTest()
        }

        if (snapshot.cullEnabled) {
            GL11.glEnable(GL11.GL_CULL_FACE)
            GlStateManager._enableCull()
        } else {
            GL11.glDisable(GL11.GL_CULL_FACE)
            GlStateManager._disableCull()
        }

        GL14.glBlendFuncSeparate(snapshot.blendSrcRgb, snapshot.blendDstRgb, snapshot.blendSrcAlpha, snapshot.blendDstAlpha)
        GlStateManager._blendFuncSeparate(snapshot.blendSrcRgb, snapshot.blendDstRgb, snapshot.blendSrcAlpha, snapshot.blendDstAlpha)
    }

    override fun activeTexture(texture: Int) {
        GL13.glActiveTexture(texture)
        GlStateManager._activeTexture(texture)
    }
    override fun bindTexture2d(textureId: Int) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
        GlStateManager._bindTexture(textureId)
    }
    override fun useProgram(programId: Int) {
        GL20.glUseProgram(programId)
        GlStateManager._glUseProgram(programId)
    }
    override fun bindVertexArray(vertexArrayId: Int) {
        GL30.glBindVertexArray(vertexArrayId)
        GlStateManager._glBindVertexArray(vertexArrayId)
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
            colorTexture.getWidth(0), colorTexture.getHeight(0)
        )
    }

    override fun bindFramebuffer(target: Int, framebufferId: Int) = GlStateManager._glBindFramebuffer(target, framebufferId)
    override fun viewport(x: Int, y: Int, width: Int, height: Int) = GlStateManager._viewport(x, y, width, height)

    override fun getActiveTextureUnit(): Int = GL13.GL_TEXTURE0 + GlStateManager.activeTexture
    override fun getBoundTextureForUnit(unit: Int): Int {
        val currentActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit)
        val boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        GL13.glActiveTexture(currentActive)
        return boundTexture
    }
    override fun getSamplerForUnit(unit: Int): Int = GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING, unit)
    override fun getActiveProgram(): Int = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
    override fun getActiveVertexArray(): Int = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
    override fun getActiveArrayBuffer(): Int = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
    override fun getBlendEquationRgb(): Int = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB)
    override fun getBlendEquationAlpha(): Int = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA)
}