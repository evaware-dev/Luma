package sweetie.evaware.luma.uniform

import org.joml.Matrix4f

class UniformRegistry {
    class Entry(
        val name: String,
        val handle: UniformHandle
    )

    val entries = ArrayList<Entry>(8)

    fun registerFloat1(name: String) = Float1Uniform().also {
        entries.add(Entry(name, it))
    }

    fun registerFloat2(name: String) = Float2Uniform().also {
        entries.add(Entry(name, it))
    }

    fun registerFloat3(name: String) = Float3Uniform().also {
        entries.add(Entry(name, it))
    }

    fun registerFloat4(name: String) = Float4Uniform().also {
        entries.add(Entry(name, it))
    }

    fun registerInt1(name: String) = Int1Uniform().also {
        entries.add(Entry(name, it))
    }

    fun registerMat4(name: String) = Mat4Uniform().also {
        entries.add(Entry(name, it))
    }

    fun setFloat1(handle: Float1Uniform, value: Float) {
        handle.value = value
    }

    fun setFloat2(handle: Float2Uniform, first: Float, second: Float) {
        handle.first = first
        handle.second = second
    }

    fun setFloat3(handle: Float3Uniform, first: Float, second: Float, third: Float) {
        handle.first = first
        handle.second = second
        handle.third = third
    }

    fun setFloat4(handle: Float4Uniform, first: Float, second: Float, third: Float, fourth: Float) {
        handle.first = first
        handle.second = second
        handle.third = third
        handle.fourth = fourth
    }

    fun setInt(handle: Int1Uniform, value: Int) {
        handle.value = value
    }

    fun setMatrix4(handle: Mat4Uniform, matrix: Matrix4f) {
        if (!handle.value.equals(matrix, MATRIX_EQUAL_EPSILON)) {
            handle.value.set(matrix)
            handle.isDirty = true
        }
    }

    private companion object {
        private const val MATRIX_EQUAL_EPSILON = 1.0E-6f
    }

    fun setProjectionMatrix4(handle: Mat4Uniform, matrix: Matrix4f, version: Int) {
        if (handle.projectionVersion != version) {
            handle.value.set(matrix)
            handle.projectionVersion = version
            handle.isDirty = true
        }
    }
}
