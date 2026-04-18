package sweetie.evaware.luma.wrapper.resource

import sweetie.evaware.luma.wrapper.RenderApiType
import sweetie.evaware.luma.wrapper.texture.TextureBinding

interface LumaRenderTarget : AutoCloseable {
    val debugName: String
    val apiType: RenderApiType
    val width: Int
    val height: Int

    fun ensureSize(width: Int, height: Int)

    fun textureBinding(): TextureBinding
}
