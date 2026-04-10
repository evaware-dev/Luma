package sweetie.evaware.luma.wrapper.texture

import sweetie.evaware.luma.wrapper.resource.LumaResources
import java.awt.image.BufferedImage

object TextureUploader {
    private var nextId = 1

    fun upload(image: BufferedImage): TextureHandle {
        val copy = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = copy.createGraphics()
        try {
            graphics.drawImage(image, 0, 0, null)
        } finally {
            graphics.dispose()
        }

        return LumaResources.track(
            TextureHandle(
                copy,
                copy.width,
                copy.height,
                "texture-${nextId++}"
            )
        )
    }

    fun close() = Unit
}
