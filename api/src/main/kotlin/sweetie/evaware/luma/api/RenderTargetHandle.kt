package sweetie.evaware.luma.api

enum class RenderTargetFormat {
    RGBA8,
    RGBA16F
}

interface RenderTargetHandle : AutoCloseable {
    val width: Int
    val height: Int
    val format: RenderTargetFormat get() = RenderTargetFormat.RGBA8
    val colorTexture: TextureHandle
    val depthTexture: TextureHandle? get() = null
    val hasStencil: Boolean get() = false
}
