package sweetie.evaware.luma.uniform

import org.joml.Matrix4f
import sweetie.evaware.luma.shader.ShaderInputApi

class ShaderUniforms : ShaderInputApi<ShaderUniforms>() {
    private val registry = UniformRegistry()

    internal fun resolve(programId: Int) {
        registry.resolve(programId)
    }

    override fun onRegisterUniform(type: ShaderUniformType, name: String) = when (type) {
        ShaderUniformType.FLOAT1 -> registry.registerFloat1(name)
        ShaderUniformType.FLOAT2 -> registry.registerFloat2(name)
        ShaderUniformType.FLOAT3 -> registry.registerFloat3(name)
        ShaderUniformType.FLOAT4 -> registry.registerFloat4(name)
        ShaderUniformType.MAT4 -> registry.registerMat4(name)
        ShaderUniformType.INT1 -> registry.registerInt1(name)
    }

    override fun onWriteUniformFloat(
        count: Int,
        handle: UniformHandle,
        first: Float,
        second: Float,
        third: Float,
        fourth: Float
    ) {
        when (count) {
            1 -> registry.setFloat1(handle as Float1Uniform, first)
            2 -> registry.setFloat2(handle as Float2Uniform, first, second)
            3 -> registry.setFloat3(handle as Float3Uniform, first, second, third)
            4 -> registry.setFloat4(handle as Float4Uniform, first, second, third, fourth)
            else -> error("Unsupported float uniform payload size: $count")
        }
    }

    override fun onWriteUniformInt(handle: Int1Uniform, value: Int) {
        registry.setInt(handle, value)
    }

    override fun onWriteUniformMat4(handle: Mat4Uniform, matrix: Matrix4f) {
        registry.setMatrix4(handle, matrix)
    }

    internal fun projectionMat4(handle: Mat4Uniform, matrix: Matrix4f, version: Int) = apply {
        registry.setProjectionMatrix4(handle, matrix, version)
    }
}
