package sweetie.evaware.luma.matrix

import org.joml.Matrix4f
import org.joml.Matrix4fStack

object MatrixControl {
    val matrix4fStack: Matrix4fStack
        get() = sweetie.evaware.luma.wrapper.matrix.MatrixControl.matrix4fStack

    fun beginGuiFrame() = sweetie.evaware.luma.wrapper.matrix.MatrixControl.beginGuiFrame()

    fun unscaledProjection() = sweetie.evaware.luma.wrapper.matrix.MatrixControl.unscaledProjection()

    fun scaledProjection() = sweetie.evaware.luma.wrapper.matrix.MatrixControl.scaledProjection()

    fun startScale(x: Float, y: Float, scale: Float) =
        sweetie.evaware.luma.wrapper.matrix.MatrixControl.startScale(x, y, scale)

    fun startScale(x: Float, y: Float, scaleX: Float, scaleY: Float) =
        sweetie.evaware.luma.wrapper.matrix.MatrixControl.startScale(x, y, scaleX, scaleY)

    fun translate(x: Float, y: Float) = sweetie.evaware.luma.wrapper.matrix.MatrixControl.translate(x, y)

    fun scale(x: Float, y: Float, z: Float = 1f) =
        sweetie.evaware.luma.wrapper.matrix.MatrixControl.scale(x, y, z)

    fun rotateZ(angleRadians: Float) = sweetie.evaware.luma.wrapper.matrix.MatrixControl.rotateZ(angleRadians)

    fun reset() = sweetie.evaware.luma.wrapper.matrix.MatrixControl.reset()

    fun pushMatrix() = sweetie.evaware.luma.wrapper.matrix.MatrixControl.pushMatrix()

    fun popMatrix() = sweetie.evaware.luma.wrapper.matrix.MatrixControl.popMatrix()

    fun projection(): Matrix4f = sweetie.evaware.luma.wrapper.matrix.MatrixControl.projection()

    fun projectionVersion() = sweetie.evaware.luma.wrapper.matrix.MatrixControl.projectionVersion()

    fun current(): Matrix4f = sweetie.evaware.luma.wrapper.matrix.MatrixControl.current()

    fun transformX(x: Float, y: Float) = sweetie.evaware.luma.wrapper.matrix.MatrixControl.transformX(x, y)

    fun transformY(x: Float, y: Float) = sweetie.evaware.luma.wrapper.matrix.MatrixControl.transformY(x, y)
}
