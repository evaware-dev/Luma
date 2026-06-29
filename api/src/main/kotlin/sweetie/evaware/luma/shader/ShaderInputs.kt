package sweetie.evaware.luma.shader

import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.ShaderVertices

class ShaderInputs : AutoCloseable {
    val vertices = ShaderVertices()
    val uniforms = ShaderUniforms()

    internal fun requireConfigured() {
        vertices.requireConfigured()
    }

    override fun close() {
        vertices.close()
    }
}
