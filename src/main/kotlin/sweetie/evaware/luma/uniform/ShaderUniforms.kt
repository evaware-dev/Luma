package sweetie.evaware.luma.uniform

import org.joml.Matrix4f

class ShaderUniforms {
    private val registry = UniformRegistry()

    fun float1(name: String) = registry.registerFloat1(name)

    fun float2(name: String) = registry.registerFloat2(name)

    fun float3(name: String) = registry.registerFloat3(name)

    fun float4(name: String) = registry.registerFloat4(name)

    fun int1(name: String) = registry.registerInt1(name)

    fun mat4(name: String) = registry.registerMat4(name)

    internal fun resolve(programId: Int) {
        registry.resolve(programId)
    }

    fun float1(handle: Float1Uniform, value: Float) {
        registry.setFloat1(handle, value)
    }

    fun float2(handle: Float2Uniform, first: Float, second: Float) {
        registry.setFloat2(handle, first, second)
    }

    fun float3(handle: Float3Uniform, first: Float, second: Float, third: Float) {
        registry.setFloat3(handle, first, second, third)
    }

    fun float4(handle: Float4Uniform, first: Float, second: Float, third: Float, fourth: Float) {
        registry.setFloat4(handle, first, second, third, fourth)
    }

    fun int1(handle: Int1Uniform, value: Int) {
        registry.setInt(handle, value)
    }

    fun mat4(handle: Mat4Uniform, matrix: Matrix4f) {
        registry.setMatrix4(handle, matrix)
    }

    internal fun projectionMat4(handle: Mat4Uniform, matrix: Matrix4f, version: Int) {
        registry.setProjectionMatrix4(handle, matrix, version)
    }
}
