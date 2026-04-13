package sweetie.evaware.luma.wrapper

import sweetie.evaware.luma.runtime.FrameRuntime
import sweetie.evaware.luma.wrapper.backend.LumaPipeline
import sweetie.evaware.luma.wrapper.backend.RenderHost
import sweetie.evaware.luma.wrapper.frame.FrameInfo
import sweetie.evaware.luma.wrapper.texture.TextureHandle

interface LumaBackend : FrameRuntime {
    val type: RenderApiType
    val requiresManagedOpenGlFrame
        get() = type == RenderApiType.OPEN_GL

    fun beginFrame(frameInfo: FrameInfo, host: RenderHost) = Unit

    override fun beginFrame(frameInfo: FrameInfo) = beginFrame(frameInfo, RenderHost.None)
}
