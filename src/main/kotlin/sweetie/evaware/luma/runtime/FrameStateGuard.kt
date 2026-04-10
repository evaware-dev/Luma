package sweetie.evaware.luma.runtime

internal interface FrameStateGuard {
    fun beginManagedFrame(bindFramebuffer: (() -> Unit)? = null)

    fun endManagedFrame()

    fun renderOutsideFrame(action: () -> Unit)

    fun invalidateBindings()
}
