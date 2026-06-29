package sweetie.evaware.luma.shader

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import sweetie.evaware.luma.uniform.Float4Uniform
import sweetie.evaware.luma.vertex.ShaderVertices

class ShaderInputsTest {
    @Test
    fun `shader inputs owns vertices and uniforms`() {
        val inputs = ShaderInputs()

        try {
            assertTrue(inputs.vertices is ShaderInputApi<*>)
            assertTrue(inputs.uniforms is ShaderInputApi<*>)

            inputs.vertices.float2(0).float4(1)
            inputs.vertices
                .vec2(1f, 2f)
                .vec4(1f, 1f, 1f, 1f)

            assertTrue(inputs.vertices.hasVertices())
        } finally {
            inputs.close()
        }
    }

    @Test
    fun `shader exposes compatible input views`() {
        val shader = Shader(
            "assets/luma-renderer/shaders/core/rect_triangle.frag",
            "assets/luma-renderer/shaders/core/rect_triangle.vert"
        )

        assertSame(shader.inputs.vertices, shader.vertices)
        assertSame(shader.inputs.uniforms, shader.uniforms)
        shader.close()
    }

    @Test
    fun `vertices typed declarations match grouped writer`() {
        val vertices = ShaderVertices()

        try {
            vertices.vec1(0).vec3(1).vec4(2)
            vertices
                .vec1(1f)
                .vec3(2f, 3f, 4f)
                .vec4(5f, 6f, 7f, 8f)

            assertTrue(vertices.hasVertices())
        } finally {
            vertices.close()
        }
    }

    @Test
    fun `uniform vector aliases keep typed handles`() {
        val inputs = ShaderInputs()
        val color: Float4Uniform = inputs.uniforms.vec4("uColor")

        assertSame(inputs.uniforms, inputs.uniforms.vec4(color, 1f, 1f, 1f, 1f))
    }

    @Test
    fun `input api rejects unsupported direction`() {
        val inputs = ShaderInputs()

        try {
            assertFailsWith<IllegalStateException> {
                inputs.vertices.vec4("uColor")
            }
            assertFailsWith<IllegalStateException> {
                inputs.uniforms.vec4(0)
            }
        } finally {
            inputs.close()
        }
    }
}
