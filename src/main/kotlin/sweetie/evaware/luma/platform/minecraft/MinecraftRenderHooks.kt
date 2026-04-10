package sweetie.evaware.luma.platform.minecraft

import sweetie.evaware.luma.Luma

object MinecraftRenderHooks {
    fun initialize() {
        Luma.installHost(MinecraftRenderHost)
        Luma.renderTypePredicate = MinecraftRenderTypeResolver::current
    }

    fun close() {
        Luma.close()
    }
}
