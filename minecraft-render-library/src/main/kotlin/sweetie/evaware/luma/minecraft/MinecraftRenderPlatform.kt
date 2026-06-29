package sweetie.evaware.luma.minecraft

import net.minecraft.client.Minecraft
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import sweetie.evaware.luma.GraphicsBackend
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.RenderPlatform
import sweetie.evaware.luma.framebuffer.FramebufferHandle

object MinecraftRenderPlatform : RenderPlatform {

    override val activeBackend: GraphicsBackend
        get() = if (Minecraft.getInstance().window.backend().name == "OpenGL") GraphicsBackend.OPENGL
        else GraphicsBackend.OTHER

    override fun getGuiScaledWidth(): Float = Minecraft.getInstance().window.guiScaledWidth.toFloat()
    override fun getGuiScaledHeight(): Float = Minecraft.getInstance().window.guiScaledHeight.toFloat()
    override fun getGuiScale(): Float = Minecraft.getInstance().window.guiScale.toFloat()
    override fun getWindowHeight(): Float = Minecraft.getInstance().window.height.toFloat()

    override fun getViewport(viewport: IntArray): Boolean {
        val colorTexture = try {
            Minecraft.getInstance().gameRenderer.mainRenderTarget().colorTextureView
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

    override fun swapToMainFramebuffer(luma: Luma) {
        val target = Minecraft.getInstance().gameRenderer.mainRenderTarget()
        val colorTexture = target.colorTextureView ?: return
        val fbo = FramebufferHandle.resolve(colorTexture, target.depthTextureView)
        val w = colorTexture.getWidth(0)
        val h = colorTexture.getHeight(0)
        
        if (activeBackend == GraphicsBackend.OPENGL) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo)
            GL11.glViewport(0, 0, w, h)
        }
    }
}