package sweetie.evaware.renderutil

import org.lwjgl.glfw.GLFW
import sweetie.evaware.luma.Luma
import kotlin.test.AfterTest
import kotlin.test.Test

class RenderUtilCloseTest {
    @AfterTest
    fun resetContextProvider() {
        Luma.contextProvider = { GLFW.glfwGetCurrentContext() != 0L }
    }

    @Test
    fun `close is idempotent without gl context`() {
        Luma.contextProvider = { false }

        RenderUtil.close()
        RenderUtil.close()
    }
}
