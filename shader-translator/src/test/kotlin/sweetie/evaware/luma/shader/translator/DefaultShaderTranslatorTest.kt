package sweetie.evaware.luma.shader.translator

import kotlin.test.Test
import kotlin.test.assertContains
import sweetie.evaware.luma.vertex.VertexLayout

class DefaultShaderTranslatorTest {
    @Test
    fun mergesStageUniformsIntoTheBlaze3dBlock() {
        val vertex = """
            #version 330 core
            @uniforms
            uniform mat4 Projection;
            @end
            void main() { gl_Position = Projection * vec4(0.0); }
        """.trimIndent()
        val fragment = """
            #version 330 core
            @uniforms
            uniform vec4 Color;
            @end
            out vec4 fragColor;
            void main() { fragColor = Color; }
        """.trimIndent()

        val result = DefaultShaderTranslator(ShaderTarget.BLAZE3D)
            .translate(vertex, fragment, VertexLayout())

        for (source in listOf(result.vertexSource, result.fragmentSource)) {
            assertContains(source, "mat4 Projection;")
            assertContains(source, "vec4 Color;")
        }
    }

}
