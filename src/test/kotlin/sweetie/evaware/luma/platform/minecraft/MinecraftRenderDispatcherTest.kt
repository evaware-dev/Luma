package sweetie.evaware.luma.platform.minecraft

import kotlin.test.Test
import kotlin.test.assertEquals

class MinecraftRenderDispatcherTest {
    @Test
    fun `late registration initializes listener immediately and dispatches callbacks`() {
        val events = mutableListOf<String>()
        val dispatcher = MinecraftRenderDispatcher(
            onInitialize = { events += "host:init" },
            onClose = { events += "host:close" }
        )
        val first = RecordingListener("first", events)
        val second = RecordingListener("second", events)

        dispatcher.register(first)
        dispatcher.initialize()
        dispatcher.register(second)
        dispatcher.renderHud(0.25f)
        dispatcher.renderGui(0.75f)
        dispatcher.close()

        assertEquals(
            listOf(
                "host:init",
                "first:init",
                "second:init",
                "first:hud:0.25",
                "second:hud:0.25",
                "first:gui:0.75",
                "second:gui:0.75",
                "second:close",
                "first:close",
                "host:close"
            ),
            events
        )
    }
}

private class RecordingListener(
    private val name: String,
    private val events: MutableList<String>
) : MinecraftRenderListener {
    override fun initialize() {
        events += "$name:init"
    }

    override fun renderHud(partialTick: Float) {
        events += "$name:hud:$partialTick"
    }

    override fun renderGui(partialTick: Float) {
        events += "$name:gui:$partialTick"
    }

    override fun close() {
        events += "$name:close"
    }
}
