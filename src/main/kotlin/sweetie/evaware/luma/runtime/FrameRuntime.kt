package sweetie.evaware.luma.runtime

import sweetie.evaware.luma.wrapper.LumaVertexBuffer
import sweetie.evaware.luma.wrapper.backend.LumaPipeline
import sweetie.evaware.luma.wrapper.frame.FrameInfo
import sweetie.evaware.luma.wrapper.resource.LumaRenderTarget
import sweetie.evaware.luma.wrapper.texture.SampledTexture
import sweetie.evaware.luma.wrapper.texture.TextureBinding

interface FrameRuntime : AutoCloseable {
    fun beginFrame(frameInfo: FrameInfo) = Unit

    fun endFrame() = Unit

    fun draw(pipeline: LumaPipeline, vertices: LumaVertexBuffer, texture: TextureBinding? = null)

    fun createRenderTarget(debugName: String, width: Int, height: Int): LumaRenderTarget

    fun drawTo(target: LumaRenderTarget, pipeline: LumaPipeline, vertices: LumaVertexBuffer, texture: SampledTexture? = null)
}
