package sweetie.evaware.luma.wrapper.backend

import sweetie.evaware.luma.wrapper.RenderApiType
import sweetie.evaware.luma.wrapper.frame.FrameInfo

interface RenderHost {
    object None : RenderHost {
        override fun frameInfo() = FrameInfo(0f, 0f, 0f, 0f, 1f)
    }

    fun renderType(): RenderApiType = RenderApiType.OPEN_GL

    fun frameInfo(): FrameInfo

    fun beginFrame(renderType: RenderApiType) = Unit

    fun endFrame(renderType: RenderApiType) = Unit
}
