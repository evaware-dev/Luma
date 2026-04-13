package sweetie.evaware.luma.platform.minecraft

internal class MinecraftRenderDispatcher(
    private val onInitialize: () -> Unit,
    private val onClose: () -> Unit
) {
    private val listeners = LinkedHashSet<MinecraftRenderListener>()
    private var initialized = false

    fun register(listener: MinecraftRenderListener) {
        if (!listeners.add(listener)) return
        if (initialized) {
            listener.initialize()
        }
    }

    fun initialize() {
        if (initialized) return
        onInitialize()
        initialized = true

        for (listener in listeners) {
            listener.initialize()
        }
    }

    fun renderHud(partialTick: Float) {
        if (!initialized) return
        for (listener in listeners) {
            listener.renderHud(partialTick)
        }
    }

    fun renderGui(partialTick: Float) {
        if (!initialized) return
        for (listener in listeners) {
            listener.renderGui(partialTick)
        }
    }

    fun close() {
        val snapshot = listeners.toTypedArray()
        for (index in snapshot.lastIndex downTo 0) {
            snapshot[index].close()
        }
        listeners.clear()
        initialized = false
        onClose()
    }
}
