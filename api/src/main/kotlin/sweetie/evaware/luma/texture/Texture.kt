package sweetie.evaware.luma.texture

import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.resource.GlResources
import java.awt.image.BufferedImage

class Texture(
    val image: BufferedImage,
    val mipmap: Boolean = true
) : AutoCloseable {
    var handle: TextureHandle? = null
    private var loaded = false

    val width: Int get() = handle?.width ?: image.width
    val height: Int get() = handle?.height ?: image.height

    fun load() {
        if (loaded) return
        handle = Luma.backend.createTexture(image, mipmap)
        GlResources.track(this)
        loaded = true
    }

    fun bind(unit: Int = 0) {
        if (!loaded) load()
        handle?.let { Luma.bindTexture(it, unit) }
    }

    override fun close() {
        if (loaded) {
            handle?.close()
            handle = null
            loaded = false
            GlResources.untrack(this)
        }
    }
}
