package sweetie.evaware.renderutil

import org.lwjgl.glfw.GLFW
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.scissor.ScissorControl
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RenderUtilCloseTest {
    @AfterTest
    fun resetContextProvider() {
        Luma.contextProvider = { GLFW.glfwGetCurrentContext() != 0L }
        ScissorControl.clear()
    }

    @Test
    fun `close is idempotent without gl context`() {
        Luma.contextProvider = { false }

        RenderUtil.close()
        RenderUtil.close()
    }

    @Test
    fun `scissor block pops after exception`() {
        ScissorControl.clear()

        assertFailsWith<IllegalStateException> {
            RenderUtil.scissor(0f, -10f, 10f, 10f) {
                error("boom")
            }
        }

        assertEquals(0f, ScissorControl.minX())
        assertEquals(0f, ScissorControl.minY())
        assertEquals(0f, ScissorControl.maxX())
        assertEquals(0f, ScissorControl.maxY())
    }
}
