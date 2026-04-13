package sweetie.evaware.luma.platform.minecraft

interface MinecraftRenderListener : AutoCloseable {
    fun initialize() = Unit

    fun renderHud(partialTick: Float) = Unit

    fun renderGui(partialTick: Float) = Unit

    override fun close() = Unit
}
