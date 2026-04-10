package sweetie.evaware.luma.wrapper.scissor

import sweetie.evaware.luma.wrapper.frame.FrameInfo
import sweetie.evaware.luma.wrapper.frame.FrameState
import kotlin.test.Test
import kotlin.test.assertContentEquals

class ScissorControlTest {
    @Test
    fun `scissor state uses frame info instead of minecraft window`() {
        FrameState.update(
            FrameInfo(
                guiWidth = 200f,
                guiHeight = 100f,
                windowWidth = 400f,
                windowHeight = 300f,
                guiScale = 2f
            )
        )

        ScissorControl.beginGuiFrame()
        ScissorControl.push(10f, 20f, 30f, 40f)

        val current = FloatArray(4)
        ScissorControl.copyCurrent(current)

        assertContentEquals(floatArrayOf(20f, 180f, 80f, 260f), current)
    }
}
