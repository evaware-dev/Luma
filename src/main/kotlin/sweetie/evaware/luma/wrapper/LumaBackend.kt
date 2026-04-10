package sweetie.evaware.luma.wrapper

import sweetie.evaware.luma.wrapper.backend.LumaPipeline
import sweetie.evaware.luma.wrapper.backend.RenderHost
import sweetie.evaware.luma.wrapper.frame.FrameInfo
import sweetie.evaware.luma.wrapper.texture.TextureHandle

interface LumaBackend : AutoCloseable {
    val type: RenderApiType
    val requiresManagedOpenGlFrame
        get() = type == RenderApiType.OPEN_GL

    fun beginFrame(frameInfo: FrameInfo, host: RenderHost) = Unit

    fun endFrame() = Unit

    fun draw(pipeline: LumaPipeline, vertices: LumaVertexBuffer, texture: TextureHandle? = null)
}
