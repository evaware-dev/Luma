package sweetie.evaware.luma.platform.minecraft

import com.mojang.blaze3d.systems.RenderSystem
import sweetie.evaware.luma.wrapper.RenderApiType

internal object MinecraftRenderTypeResolver {
    fun current(): RenderApiType {
        val device = RenderSystem.tryGetDevice() ?: return RenderApiType.OPEN_GL
        return when (device.getDeviceInfo().backendName().lowercase()) {
            "vulkan" -> RenderApiType.VULKAN
            else -> RenderApiType.OPEN_GL
        }
    }
}
