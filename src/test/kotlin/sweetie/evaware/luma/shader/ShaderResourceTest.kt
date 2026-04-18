package sweetie.evaware.luma.shader

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ShaderResourceTest {
    @Test
    fun `scissor is vertex payload for batching`() {
        val uberVertex = resourceText("assets/luma-renderer/shaders/core/uber.vert")
        val uberFragment = resourceText("assets/luma-renderer/shaders/core/uber.frag")
        val roundedVertex = resourceText("assets/luma-renderer/shaders/core/rect_quad.vert")
        val roundedFragment = resourceText("assets/luma-renderer/shaders/core/rect_quad.frag")

        assertContains(uberVertex, "layout(location = 3) in vec4 a3")
        assertContains(roundedVertex, "layout(location = 5) in vec4 a5")
        assertContains(uberVertex, "out vec4 vScissor")
        assertContains(roundedVertex, "out vec4 vScissor")
        assertContains(uberFragment, "scissorVisible(vScissor")
        assertContains(roundedFragment, "scissorVisible(vScissor")
        assertFalse(uberFragment.contains("uniform vec4 uScissor"))
        assertFalse(roundedFragment.contains("uniform vec4 uScissor"))
    }

    private fun resourceText(path: String) = javaClass.classLoader.getResourceAsStream(path)
        ?.bufferedReader()
        ?.use { it.readText() }
        ?: error("Missing shader resource: $path")
}
