package sweetie.evaware.luma.shader.translator

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    @Test
    fun populatesStructuredReflectionForBlaze3dTarget() {
        val vertex = """
            #version 330 core
            @in 0 vec2 aPos Position
            @in 1 vec2 aUv UV0
            @uniforms
            uniform mat4 Projection;
            uniform vec4 Tint;
            @end
            out vec2 vUv;
            void main() {
                vUv = aUv;
                gl_Position = Projection * vec4(aPos, 0.0, 1.0);
            }
        """.trimIndent()
        val fragment = """
            #version 330 core
            @uniforms
            uniform vec4 Tint;
            uniform float Alpha;
            @end
            @sampler sampler2D uDiffuse 0
            @sampler sampler2D uMask 1
            in vec2 vUv;
            out vec4 fragColor;
            void main() {
                fragColor = texture(uDiffuse, vUv) * texture(uMask, vUv) * Tint * Alpha;
            }
        """.trimIndent()

        val result = DefaultShaderTranslator(ShaderTarget.BLAZE3D)
            .translate(vertex, fragment, VertexLayout())

        val reflection = assertNotNull(result.reflection)
        assertEquals(
            listOf(
                ShaderAttributeInfo(0, "vec2", "aPos", "Position"),
                ShaderAttributeInfo(1, "vec2", "aUv", "UV0")
            ),
            reflection.attributes
        )
        assertEquals(
            listOf(
                ShaderUniformInfo("Projection", "mat4"),
                ShaderUniformInfo("Tint", "vec4"),
                ShaderUniformInfo("Alpha", "float")
            ),
            reflection.uniforms
        )
        assertEquals(
            listOf(
                ShaderSamplerInfo("uDiffuse", "Sampler0", "sampler2D", unit = 0, binding = 1),
                ShaderSamplerInfo("uMask", "Sampler1", "sampler2D", unit = 1, binding = 2)
            ),
            reflection.samplers
        )
    }

    @Test
    fun populatesStructuredReflectionForOpenGlTargetWithoutUniformOffset() {
        val vertex = """
            #version 330 core
            @in 0 vec2 aPos Position
            void main() { gl_Position = vec4(aPos, 0.0, 1.0); }
        """.trimIndent()
        val fragment = """
            #version 330 core
            @sampler sampler2D uFont 0
            out vec4 fragColor;
            void main() { fragColor = texture(uFont, vec2(0.0)); }
        """.trimIndent()

        val result = DefaultShaderTranslator(ShaderTarget.OPENGL)
            .translate(vertex, fragment, VertexLayout())

        val reflection = assertNotNull(result.reflection)
        assertEquals(listOf(ShaderAttributeInfo(0, "vec2", "aPos", "aPos")), reflection.attributes)
        assertEquals(emptyList(), reflection.uniforms)
        assertEquals(
            listOf(ShaderSamplerInfo("uFont", "uFont", "sampler2D", unit = 0, binding = 0)),
            reflection.samplers
        )
    }
}
