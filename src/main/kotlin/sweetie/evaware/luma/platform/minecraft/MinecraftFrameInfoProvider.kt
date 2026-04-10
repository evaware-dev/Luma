package sweetie.evaware.luma.platform.minecraft

import net.minecraft.client.Minecraft
import sweetie.evaware.luma.wrapper.frame.FrameInfo

internal object MinecraftFrameInfoProvider {
    fun current(): FrameInfo {
        val window = Minecraft.getInstance().window
        return FrameInfo(
            guiWidth = window.guiScaledWidth.toFloat(),
            guiHeight = window.guiScaledHeight.toFloat(),
            windowWidth = window.width.toFloat(),
            windowHeight = window.height.toFloat(),
            guiScale = window.guiScale.toFloat()
        )
    }
}
