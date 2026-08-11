package sweetie.evaware.luma.shader.translator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.vertex.VertexLayout

class RawShaderTranslatorTest {
    @Test
    fun isTheDefaultTranslator() {
        assertSame(RawShaderTranslator, Luma.shaderTranslator)
    }

    @Test
    fun preservesRawGlsl() {
        val vertex = "#version 330 core\nvoid main() {}\n"
        val fragment = "#version 330 core\nout vec4 color;\nvoid main() { color = vec4(1.0); }\n"

        val result = RawShaderTranslator.translate(vertex, fragment, VertexLayout())

        assertEquals(vertex, result.vertexSource)
        assertEquals(fragment, result.fragmentSource)
    }
}
