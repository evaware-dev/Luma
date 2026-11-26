package sweetie.evaware.luma.uniform

import org.joml.Matrix4f

class ShaderUniforms {
    val registry = UniformRegistry()

    fun float1(name: String): Float1Uniform = registry.registerFloat1(name)
    fun float2(name: String): Float2Uniform = registry.registerFloat2(name)
    fun float3(name: String): Float3Uniform = registry.registerFloat3(name)
    fun float4(name: String): Float4Uniform = registry.registerFloat4(name)

    fun vec2(name: String): Float2Uniform = registry.registerFloat2(name)
    fun vec3(name: String): Float3Uniform = registry.registerFloat3(name)
    fun vec4(name: String): Float4Uniform = registry.registerFloat4(name)

    fun int1(name: String): Int1Uniform = registry.registerInt1(name)
    fun mat4(name: String): Mat4Uniform = registry.registerMat4(name)

    fun float1(handle: Float1Uniform, value: Float): ShaderUniforms {
        handle.set(value)
        return this
    }

    fun float2(handle: Float2Uniform, first: Float, second: Float): ShaderUniforms {
        handle.set(first, second)
        return this
    }

    fun float3(handle: Float3Uniform, first: Float, second: Float, third: Float): ShaderUniforms {
        handle.set(first, second, third)
        return this
    }

    fun float4(handle: Float4Uniform, first: Float, second: Float, third: Float, fourth: Float): ShaderUniforms {
        handle.set(first, second, third, fourth)
        return this
    }

    fun vec2(handle: Float2Uniform, first: Float, second: Float): ShaderUniforms {
        handle.set(first, second)
        return this
    }

    fun vec3(handle: Float3Uniform, first: Float, second: Float, third: Float): ShaderUniforms {
        handle.set(first, second, third)
        return this
    }

    fun vec4(handle: Float4Uniform, first: Float, second: Float, third: Float, fourth: Float): ShaderUniforms {
        handle.set(first, second, third, fourth)
        return this
    }

    fun int1(handle: Int1Uniform, value: Int): ShaderUniforms {
        handle.set(value)
        return this
    }

    fun mat4(handle: Mat4Uniform, matrix: Matrix4f): ShaderUniforms {
        registry.setMatrix4(handle, matrix)
        return this
    }

    internal fun projectionMat4(handle: Mat4Uniform, matrix: Matrix4f, version: Int): ShaderUniforms {
        registry.setProjectionMatrix4(handle, matrix, version)
        return this
    }
}
