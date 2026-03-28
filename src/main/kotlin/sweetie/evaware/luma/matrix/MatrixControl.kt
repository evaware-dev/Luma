package sweetie.evaware.luma.matrix

import net.minecraft.client.Minecraft
import org.joml.Matrix4f
import org.joml.Matrix4fStack
import sweetie.evaware.luma.scissor.ScissorControl

object MatrixControl {
    private val projectionMatrix = Matrix4f()
    private val combinedMatrix = Matrix4f()

    @JvmField
    val matrix4fStack = Matrix4fStack(32).apply {
        identity()
    }

    private var tm00 = 1f
    private var tm01 = 0f
    private var tm10 = 0f
    private var tm11 = 1f
    private var tm30 = 0f
    private var tm31 = 0f
    private var projectionVersion = 0

    fun beginGuiFrame() {
        matrix4fStack.clear()
        unscaledProjection()
        reset()
        ScissorControl.beginGuiFrame()
    }

    fun unscaledProjection() {
        val window = Minecraft.getInstance().window
        projectionMatrix.identity().ortho(
            0f,
            window.guiScaledWidth.toFloat(),
            window.guiScaledHeight.toFloat(),
            0f,
            -1000f,
            1000f
        )
        projectionVersion++
    }

    fun scaledProjection() {
        val window = Minecraft.getInstance().window
        projectionMatrix.identity().ortho(
            0f,
            window.guiScaledWidth.toFloat() * 0.5f,
            window.guiScaledHeight.toFloat() * 0.5f,
            0f,
            -1000f,
            1000f
        )
        projectionVersion++
    }

    fun startScale(x: Float, y: Float, scale: Float) {
        startScale(x, y, scale, scale)
    }

    fun startScale(x: Float, y: Float, scaleX: Float, scaleY: Float) {
        matrix4fStack.translate(x, y, 0f)
        matrix4fStack.scale(scaleX, scaleY, 1f)
        matrix4fStack.translate(-x, -y, 0f)
        cacheTransform()
    }

    fun translate(x: Float, y: Float) {
        matrix4fStack.translate(x, y, 0f)
        cacheTransform()
    }

    fun scale(x: Float, y: Float, z: Float = 1f) {
        matrix4fStack.scale(x, y, z)
        cacheTransform()
    }

    fun rotateZ(angleRadians: Float) {
        matrix4fStack.rotateZ(angleRadians)
        cacheTransform()
    }

    fun reset() {
        matrix4fStack.identity()
        cacheTransform()
    }

    fun pushMatrix() {
        matrix4fStack.pushMatrix()
    }

    fun popMatrix() {
        matrix4fStack.popMatrix()
        cacheTransform()
    }

    fun projection() = projectionMatrix

    fun projectionVersion() = projectionVersion

    fun current() = combinedMatrix.set(projectionMatrix).mul(matrix4fStack)

    fun transformX(x: Float, y: Float) = tm00 * x + tm10 * y + tm30

    fun transformY(x: Float, y: Float) = tm01 * x + tm11 * y + tm31

    private fun cacheTransform() {
        tm00 = matrix4fStack.m00()
        tm01 = matrix4fStack.m01()
        tm10 = matrix4fStack.m10()
        tm11 = matrix4fStack.m11()
        tm30 = matrix4fStack.m30()
        tm31 = matrix4fStack.m31()
    }
}
