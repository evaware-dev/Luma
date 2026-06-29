package sweetie.evaware.luma.api

enum class RenderTargetFormat {
    RGBA8,
    RGBA16F
}

interface RenderTargetHandle : AutoCloseable {
    val width: Int
    val height: Int
    val colorTexture: TextureHandle
}
