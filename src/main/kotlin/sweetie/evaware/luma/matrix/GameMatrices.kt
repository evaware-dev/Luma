package sweetie.evaware.luma.matrix

import org.joml.Matrix4f

object GameMatrices {
    fun beginGuiFrame() = MatrixControl.beginGuiFrame()

    fun pushMatrix() = MatrixControl.pushMatrix()

    fun popMatrix() = MatrixControl.popMatrix()

    fun translate(x: Float, y: Float, z: Float = 0f) {
        MatrixControl.translate(x, y)
    }

    fun scale(x: Float, y: Float, z: Float = 1f) {
        MatrixControl.scale(x, y, z)
    }

    fun current(): Matrix4f = MatrixControl.current()
}
