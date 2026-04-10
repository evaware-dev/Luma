package sweetie.evaware.luma.texture

import java.awt.image.BufferedImage

typealias TextureHandle = sweetie.evaware.luma.wrapper.texture.TextureHandle
typealias Region = sweetie.evaware.luma.wrapper.texture.TextureAtlas.Region

object TextureAtlas {
    fun register(id: String, loader: () -> BufferedImage) =
        sweetie.evaware.luma.wrapper.texture.TextureAtlas.register(id, loader)

    fun registerResource(id: String, path: String) =
        sweetie.evaware.luma.wrapper.texture.TextureAtlas.registerResource(id, path)

    fun prepare() = sweetie.evaware.luma.wrapper.texture.TextureAtlas.prepare()

    fun texture(): TextureHandle = sweetie.evaware.luma.wrapper.texture.TextureAtlas.texture()

    fun region(id: String): Region = sweetie.evaware.luma.wrapper.texture.TextureAtlas.region(id)

    fun whiteRegion(): Region = sweetie.evaware.luma.wrapper.texture.TextureAtlas.whiteRegion()

    fun close() = sweetie.evaware.luma.wrapper.texture.TextureAtlas.close()
}

object TextureUploader {
    fun upload(image: BufferedImage): TextureHandle =
        sweetie.evaware.luma.wrapper.texture.TextureUploader.upload(image)

    fun close() = sweetie.evaware.luma.wrapper.texture.TextureUploader.close()
}
