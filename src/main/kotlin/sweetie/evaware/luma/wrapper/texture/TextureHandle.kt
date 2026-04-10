package sweetie.evaware.luma.wrapper.texture

import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.wrapper.api.Unloadable
import sweetie.evaware.luma.wrapper.resource.LumaResources
import java.awt.image.BufferedImage

class TextureHandle internal constructor(
    private val source: BufferedImage,
    val width: Int,
    val height: Int,
    internal val debugName: String
) : Unloadable, AutoCloseable {
    private var closed = false

    fun bind() {
        Luma.bindTexture(this)
    }

    internal fun image(): BufferedImage {
        require(!closed) { "Texture handle is closed" }
        return source
    }

    val isClosed
        get() = closed

    override fun unload() {
        close()
    }

    override fun close() {
        if (closed) return
        closed = true
        LumaResources.untrack(this)
    }
}
