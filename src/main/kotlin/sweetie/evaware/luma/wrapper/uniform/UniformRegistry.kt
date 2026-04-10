package sweetie.evaware.luma.wrapper.uniform

import org.joml.Matrix4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL20

class UniformRegistry {
    private class Entry(
        val name: String,
        val handle: UniformHandle
    )

    private val entries = mutableListOf<Entry>()
    private val matrixBuffer = BufferUtils.createFloatBuffer(16)

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

    fun resolve(programId: Int) {
        for (index in entries.indices) {
            val entry = entries[index]
            entry.handle.resolve(GL20.glGetUniformLocation(programId, entry.name))
        }
    }

    fun uploadFloat1(handle: Float1Uniform, value: Float) {
        val location = handle.location
        if (location < 0) return
        if (!handle.shouldUpload(value)) return
        GL20.glUniform1f(location, value)
    }

    fun uploadFloat2(handle: Float2Uniform, first: Float, second: Float) {
        val location = handle.location
        if (location < 0) return
        if (!handle.shouldUpload(first, second)) return
        GL20.glUniform2f(location, first, second)
    }

    fun uploadFloat3(handle: Float3Uniform, first: Float, second: Float, third: Float) {
        val location = handle.location
        if (location < 0) return
        if (!handle.shouldUpload(first, second, third)) return
        GL20.glUniform3f(location, first, second, third)
    }

    fun uploadFloat4(handle: Float4Uniform, first: Float, second: Float, third: Float, fourth: Float) {
        val location = handle.location
        if (location < 0) return
        if (!handle.shouldUpload(first, second, third, fourth)) return
        GL20.glUniform4f(location, first, second, third, fourth)
    }

    fun uploadInt(handle: Int1Uniform, value: Int) {
        val location = handle.location
        if (location < 0) return
        if (!handle.shouldUpload(value)) return
        GL20.glUniform1i(location, value)
    }

    fun uploadMatrix4(handle: Mat4Uniform, matrix: Matrix4f) {
        val location = handle.location
        if (location < 0) return
        handle.invalidateProjectionVersion()
        uploadMatrix(location, matrix)
    }

    fun uploadProjectionMatrix4(handle: Mat4Uniform, matrix: Matrix4f, version: Int) {
        val location = handle.location
        if (location < 0) return
        if (!handle.shouldUploadProjectionVersion(version)) return
        uploadMatrix(location, matrix)
    }

    private fun uploadMatrix(location: Int, matrix: Matrix4f) {
        matrixBuffer.clear()
        matrix.get(matrixBuffer)
        GL20.glUniformMatrix4fv(location, false, matrixBuffer)
    }
}
