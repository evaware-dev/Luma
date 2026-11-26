package sweetie.evaware.luma.shader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import sweetie.evaware.luma.uniform.Float4Uniform
import sweetie.evaware.luma.vertex.ShaderVertices

class ShaderInputsTest {
    @Test
    fun `shader inputs owns vertices and uniforms`() {
        val inputs = ShaderInputs()
        inputs.vertices.float2(0).float4(1)
        assertEquals(2, inputs.vertices.layout.size())
        assertEquals(6, inputs.vertices.layout.strideFloats)
    }

    @Test
    fun `shader exposes compatible input views`() {
        val shader = Shader("assets/luma-renderer/shaders/core/rect_triangle.frag", "assets/luma-renderer/shaders/core/rect_triangle.vert")

        assertSame(shader.inputs.vertices, shader.vertices)
        assertSame(shader.inputs.uniforms, shader.uniforms)
        shader.close()
    }

    @Test
    fun `vertices typed declarations build expected stride`() {
        val vertices = ShaderVertices()
        vertices.vec1(0).vec3(1).vec4(2)

        assertEquals(3, vertices.layout.size())
        assertEquals(8, vertices.layout.strideFloats)
    }

    @Test
    fun `uniform vector aliases keep typed handles`() {
        val inputs = ShaderInputs()
        val color: Float4Uniform = inputs.uniforms.vec4("uColor")

        assertSame(inputs.uniforms, inputs.uniforms.vec4(color, 1f, 1f, 1f, 1f))
    }
}
