package sweetie.evaware.luma.minecraft

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import sweetie.evaware.luma.GraphicsBackend
import sweetie.evaware.luma.LumaNames
import sweetie.evaware.luma.RenderPlatform

object MinecraftRenderPlatform : RenderPlatform {
    override val activeBackend: GraphicsBackend
        get() = if (RenderSystem.getDevice().deviceInfo.backendName == LumaNames.OPENGL_BACKEND_NAME) GraphicsBackend.OPENGL
        else GraphicsBackend.OTHER

    override fun getGuiScaledWidth(): Float = Minecraft.getInstance().window.guiScaledWidth.toFloat()
    override fun getGuiScaledHeight(): Float = Minecraft.getInstance().window.guiScaledHeight.toFloat()
    override fun getGuiScale(): Float = Minecraft.getInstance().window.guiScale.toFloat()
    override fun getWindowHeight(): Float = Minecraft.getInstance().window.height.toFloat()

    override fun getViewport(viewport: IntArray): Boolean {
        val colorTexture = try {
            Minecraft.getInstance().gameRenderer.mainRenderTarget().colorTextureView
        } catch (_: Throwable) {
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
}
