package sweetie.evaware.luma.backend.gl

import org.lwjgl.opengl.GL20
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.uniform.*
import sweetie.evaware.luma.vertex.VertexLayout

class Program(
    val programId: Int,
    val layout: VertexLayout
) : ProgramHandle {
    val vertexBuffer = GlVertexBuffer(layout)
    private val uniformLocations = HashMap<String, Int>()
    private var preparedUniforms: List<PreparedGlUniform>? = null

    fun getUniformLocation(name: String): Int {
        return uniformLocations.getOrPut(name) {
            GL20.glGetUniformLocation(programId, name)
        }
    }

    fun getPreparedUniforms(uniforms: ShaderUniforms): List<PreparedGlUniform> {
        var list = preparedUniforms
        if (list == null) {
            list = uniforms.registry.entries.mapNotNull { entry ->
                val loc = getUniformLocation(entry.name)
                if (loc >= 0) {
                    val uniform = when (entry.handle) {
                        is Float1Uniform -> GlFloat1Uniform(entry.name)
                        is Float2Uniform -> GlFloat2Uniform(entry.name)
                        is Float3Uniform -> GlFloat3Uniform(entry.name)
                        is Float4Uniform -> GlFloat4Uniform(entry.name)
                        is Int1Uniform -> GlInt1Uniform(entry.name)
                        is Mat4Uniform -> GlMat4Uniform(entry.name)
                        else -> error("Unsupported OpenGL uniform type")
                    }
                    PreparedGlUniform(uniform, loc)
                } else {
                    null
                }
            }
            preparedUniforms = list
        }
        return list
    }

    override fun close() {
        vertexBuffer.close()
        GL20.glDeleteProgram(programId)
    }
}
