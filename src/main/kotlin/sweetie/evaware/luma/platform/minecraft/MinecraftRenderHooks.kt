package sweetie.evaware.luma.platform.minecraft

import sweetie.evaware.luma.Luma

object MinecraftRenderHooks {
    private val dispatcher = MinecraftRenderDispatcher(
        onInitialize = {
            Luma.installHost(MinecraftRenderHost)
            Luma.renderTypePredicate = MinecraftRenderTypeResolver::current
        },
        onClose = Luma::close
    )

    fun register(listener: MinecraftRenderListener) {
        dispatcher.register(listener)
    }

    fun initialize() {
        dispatcher.initialize()
    }

    fun renderHud(partialTick: Float) {
        dispatcher.renderHud(partialTick)
    }

    fun renderGui(partialTick: Float) {
        dispatcher.renderGui(partialTick)
    }

    fun close() {
        dispatcher.close()
    }
}
