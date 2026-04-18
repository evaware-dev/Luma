package sweetie.evaware.luma.shader

import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.ShaderVertices

class ShaderInputs : AutoCloseable {
    val vertices = ShaderVertices()
    val uniforms = ShaderUniforms()

    internal fun requireConfigured() {
        vertices.requireConfigured()
    }

    internal fun beforeLink(programId: Int) {
        vertices.bindLocations(programId)
    }

    internal fun afterLink(programId: Int, vao: Int, vbo: Int) {
        vertices.bind(vao, vbo)
        uniforms.resolve(programId)
    }

    internal fun upload(vbo: Int, drawMode: Int) = vertices.upload(vbo, drawMode)

    override fun close() {
        vertices.close()
    }
}
