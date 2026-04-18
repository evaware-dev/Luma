package sweetie.evaware.luma.shader

import org.joml.Matrix4f
import sweetie.evaware.luma.uniform.Float1Uniform
import sweetie.evaware.luma.uniform.Float2Uniform
import sweetie.evaware.luma.uniform.Float3Uniform
import sweetie.evaware.luma.uniform.Float4Uniform
import sweetie.evaware.luma.uniform.Int1Uniform
import sweetie.evaware.luma.uniform.Mat4Uniform
import sweetie.evaware.luma.uniform.ShaderUniformType
import sweetie.evaware.luma.uniform.UniformHandle

abstract class ShaderInputApi<Self : ShaderInputApi<Self>> {
    fun float(count: Int, layoutPos: Int, normalized: Boolean = false): Self {
        onDefineFloat(count, layoutPos, normalized)
        return self()
    }

    fun float1(layoutPos: Int, normalized: Boolean = false) = float(1, layoutPos, normalized)

    fun float2(layoutPos: Int, normalized: Boolean = false) = float(2, layoutPos, normalized)

    fun float3(layoutPos: Int, normalized: Boolean = false) = float(3, layoutPos, normalized)

    fun float4(layoutPos: Int, normalized: Boolean = false) = float(4, layoutPos, normalized)

    fun vec1(layoutPos: Int, normalized: Boolean = false) = float1(layoutPos, normalized)

    fun vec2(layoutPos: Int, normalized: Boolean = false) = float2(layoutPos, normalized)

    fun vec3(layoutPos: Int, normalized: Boolean = false) = float3(layoutPos, normalized)

    fun vec4(layoutPos: Int, normalized: Boolean = false) = float4(layoutPos, normalized)

    fun float1(name: String) = onRegisterUniform(ShaderUniformType.FLOAT1, name) as Float1Uniform

    fun float2(name: String) = onRegisterUniform(ShaderUniformType.FLOAT2, name) as Float2Uniform

    fun float3(name: String) = onRegisterUniform(ShaderUniformType.FLOAT3, name) as Float3Uniform

    fun float4(name: String) = onRegisterUniform(ShaderUniformType.FLOAT4, name) as Float4Uniform

    fun vec2(name: String) = float2(name)

    fun vec3(name: String) = float3(name)

    fun vec4(name: String) = float4(name)

    fun int1(name: String) = onRegisterUniform(ShaderUniformType.INT1, name) as Int1Uniform

    fun mat4(name: String) = onRegisterUniform(ShaderUniformType.MAT4, name) as Mat4Uniform

    fun float1(value: Float): Self {
        onWriteFloat(1, value, 0f, 0f, 0f)
        return self()
    }

    fun vec1(value: Float) = float1(value)

    fun vec2(first: Float, second: Float): Self {
        onWriteFloat(2, first, second, 0f, 0f)
        return self()
    }

    fun vec3(first: Float, second: Float, third: Float): Self {
        onWriteFloat(3, first, second, third, 0f)
        return self()
    }

    fun vec4(first: Float, second: Float, third: Float, fourth: Float): Self {
        onWriteFloat(4, first, second, third, fourth)
        return self()
    }

    fun float1(handle: Float1Uniform, value: Float): Self {
        onWriteUniformFloat(1, handle, value, 0f, 0f, 0f)
        return self()
    }

    fun float2(handle: Float2Uniform, first: Float, second: Float): Self {
        onWriteUniformFloat(2, handle, first, second, 0f, 0f)
        return self()
    }

    fun float3(handle: Float3Uniform, first: Float, second: Float, third: Float): Self {
        onWriteUniformFloat(3, handle, first, second, third, 0f)
        return self()
    }

    fun float4(handle: Float4Uniform, first: Float, second: Float, third: Float, fourth: Float): Self {
        onWriteUniformFloat(4, handle, first, second, third, fourth)
        return self()
    }

    fun vec2(handle: Float2Uniform, first: Float, second: Float) = float2(handle, first, second)

    fun vec3(handle: Float3Uniform, first: Float, second: Float, third: Float) = float3(handle, first, second, third)

    fun vec4(handle: Float4Uniform, first: Float, second: Float, third: Float, fourth: Float) =
        float4(handle, first, second, third, fourth)

    fun int1(handle: Int1Uniform, value: Int): Self {
        onWriteUniformInt(handle, value)
        return self()
    }

    fun mat4(handle: Mat4Uniform, matrix: Matrix4f): Self {
        onWriteUniformMat4(handle, matrix)
        return self()
    }

    protected open fun onDefineFloat(count: Int, layoutPos: Int, normalized: Boolean) {
        unsupported("float vertex layout")
    }

    protected open fun onWriteFloat(count: Int, first: Float, second: Float, third: Float, fourth: Float) {
        unsupported("float vertex payload")
    }

    protected open fun onRegisterUniform(type: ShaderUniformType, name: String): UniformHandle {
        unsupported("uniform declaration")
    }

    protected open fun onWriteUniformFloat(
        count: Int,
        handle: UniformHandle,
        first: Float,
        second: Float,
        third: Float,
        fourth: Float
    ) {
        unsupported("float uniform upload")
    }

    protected open fun onWriteUniformInt(handle: Int1Uniform, value: Int) {
        unsupported("int uniform upload")
    }

    protected open fun onWriteUniformMat4(handle: Mat4Uniform, matrix: Matrix4f) {
        unsupported("matrix uniform upload")
    }

    @Suppress("UNCHECKED_CAST")
    private fun self() = this as Self

    private fun unsupported(action: String): Nothing {
        error("${javaClass.simpleName} does not support $action")
    }
}
