package sweetie.evaware.luma.backend.gl

import org.joml.Matrix4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL20
import sweetie.evaware.luma.uniform.*

abstract class GlUniform(val name: String) {
    private var cachedIndex = -1

    fun getHandle(uniforms: ShaderUniforms): UniformHandle? {
        val entries = uniforms.registry.entries
        val idx = cachedIndex
        if (idx in entries.indices) {
            val entry = entries[idx]
            if (entry.name == name) {
                return entry.handle
            }
        }
        for (i in entries.indices) {
            val entry = entries[i]
            if (entry.name == name) {
                cachedIndex = i
                return entry.handle
            }
        }
        return null
    }

    abstract fun upload(location: Int, uniforms: ShaderUniforms)
}

class GlFloat1Uniform(name: String) : GlUniform(name) {
    override fun upload(location: Int, uniforms: ShaderUniforms) {
        val handle = getHandle(uniforms) as? Float1Uniform
        val v = handle?.value ?: 0f
        GL20.glUniform1f(location, v)
    }
}

class GlFloat2Uniform(name: String) : GlUniform(name) {
    override fun upload(location: Int, uniforms: ShaderUniforms) {
        val handle = getHandle(uniforms) as? Float2Uniform
        if (handle != null) {
            GL20.glUniform2f(location, handle.first, handle.second)
        } else {
            GL20.glUniform2f(location, 0f, 0f)
        }
    }
}

class GlFloat3Uniform(name: String) : GlUniform(name) {
    override fun upload(location: Int, uniforms: ShaderUniforms) {
        val handle = getHandle(uniforms) as? Float3Uniform
        if (handle != null) {
            GL20.glUniform3f(location, handle.first, handle.second, handle.third)
        } else {
            GL20.glUniform3f(location, 0f, 0f, 0f)
        }
    }
}

class GlFloat4Uniform(name: String) : GlUniform(name) {
    override fun upload(location: Int, uniforms: ShaderUniforms) {
        val handle = getHandle(uniforms) as? Float4Uniform
        if (handle != null) {
            GL20.glUniform4f(location, handle.first, handle.second, handle.third, handle.fourth)
        } else {
            GL20.glUniform4f(location, 0f, 0f, 0f, 0f)
        }
    }
}

class GlInt1Uniform(name: String) : GlUniform(name) {
    override fun upload(location: Int, uniforms: ShaderUniforms) {
        val handle = getHandle(uniforms) as? Int1Uniform
        val v = handle?.value ?: 0
        GL20.glUniform1i(location, v)
    }
}

private val IDENTITY_MATRIX = Matrix4f()

class GlMat4Uniform(name: String) : GlUniform(name) {
    private val matrixFloatBuffer = BufferUtils.createFloatBuffer(16)

    override fun upload(location: Int, uniforms: ShaderUniforms) {
        val handle = getHandle(uniforms) as? Mat4Uniform
        val matrix = handle?.value ?: IDENTITY_MATRIX
        matrixFloatBuffer.clear()
        matrix.get(matrixFloatBuffer)
        GL20.glUniformMatrix4fv(location, false, matrixFloatBuffer)
    }
}

class PreparedGlUniform(val uniform: GlUniform, val location: Int)
