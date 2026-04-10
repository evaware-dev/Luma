package sweetie.evaware.luma.scissor

object ScissorControl {
    fun beginGuiFrame() = sweetie.evaware.luma.wrapper.scissor.ScissorControl.beginGuiFrame()

    fun clear() = sweetie.evaware.luma.wrapper.scissor.ScissorControl.clear()

    fun push(x: Float, y: Float, width: Float, height: Float) =
        sweetie.evaware.luma.wrapper.scissor.ScissorControl.push(x, y, width, height)

    fun pop() = sweetie.evaware.luma.wrapper.scissor.ScissorControl.pop()

    fun copyCurrent(out: FloatArray, offset: Int = 0) =
        sweetie.evaware.luma.wrapper.scissor.ScissorControl.copyCurrent(out, offset)
}
