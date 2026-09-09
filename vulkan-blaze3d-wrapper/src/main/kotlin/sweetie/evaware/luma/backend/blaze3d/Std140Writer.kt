package sweetie.evaware.luma.backend.blaze3d

import java.nio.ByteBuffer
import org.joml.Matrix4fc

internal class Std140Writer {
    private lateinit var buffer: ByteBuffer
    private var start = 0

    fun begin(buffer: ByteBuffer) {
        this.buffer = buffer
        start = buffer.position()
    }

    fun size(): Int = buffer.position() - start

    fun putFloat(value: Float) {
        align(Float.SIZE_BYTES)
        buffer.putFloat(value)
    }

    fun putInt(value: Int) {
        align(Int.SIZE_BYTES)
        buffer.putInt(value)
    }

    fun putVec2(first: Float, second: Float) {
        align(2 * Float.SIZE_BYTES)
        buffer.putFloat(first)
        buffer.putFloat(second)
    }

    fun putVec3(first: Float, second: Float, third: Float) {
        align(4 * Float.SIZE_BYTES)
        buffer.putFloat(first)
        buffer.putFloat(second)
        buffer.putFloat(third)
        buffer.position(buffer.position() + Float.SIZE_BYTES)
    }

    fun putVec4(first: Float, second: Float, third: Float, fourth: Float) {
        align(4 * Float.SIZE_BYTES)
        buffer.putFloat(first)
        buffer.putFloat(second)
        buffer.putFloat(third)
        buffer.putFloat(fourth)
    }

    fun putMat4(matrix: Matrix4fc) {
        align(4 * Float.SIZE_BYTES)
        matrix.get(buffer)
        buffer.position(buffer.position() + 16 * Float.SIZE_BYTES)
    }

    private fun align(alignment: Int) {
        val relative = buffer.position() - start
        val remainder = relative % alignment
        if (remainder != 0) buffer.position(buffer.position() + alignment - remainder)
    }
}
