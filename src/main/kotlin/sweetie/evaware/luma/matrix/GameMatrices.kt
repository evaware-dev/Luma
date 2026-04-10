package sweetie.evaware.luma.matrix

import org.joml.Matrix4f

object GameMatrices {
    fun beginGuiFrame() = sweetie.evaware.luma.wrapper.matrix.GameMatrices.beginGuiFrame()

    fun pushMatrix() = sweetie.evaware.luma.wrapper.matrix.GameMatrices.pushMatrix()

    fun popMatrix() = sweetie.evaware.luma.wrapper.matrix.GameMatrices.popMatrix()

    fun translate(x: Float, y: Float, z: Float = 0f) =
        sweetie.evaware.luma.wrapper.matrix.GameMatrices.translate(x, y, z)

    fun scale(x: Float, y: Float, z: Float = 1f) =
        sweetie.evaware.luma.wrapper.matrix.GameMatrices.scale(x, y, z)

    fun current(): Matrix4f = sweetie.evaware.luma.wrapper.matrix.GameMatrices.current()
}
