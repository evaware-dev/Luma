package sweetie.evaware.luma.wrapper.frame

object FrameState {
    private var currentFrame = FrameInfo(
        guiWidth = 0f,
        guiHeight = 0f,
        windowWidth = 0f,
        windowHeight = 0f,
        guiScale = 1f
    )

    fun current(): FrameInfo = currentFrame

    internal fun update(frameInfo: FrameInfo) {
        currentFrame = frameInfo
    }
}
