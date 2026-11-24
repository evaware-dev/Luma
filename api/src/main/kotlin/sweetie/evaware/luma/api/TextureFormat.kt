package sweetie.evaware.luma.api

enum class TextureFormat(
    val channels: Int,
    val bytesPerPixel: Int
) {
    RGBA8(channels = 4, bytesPerPixel = 4),
    RG8(channels = 2, bytesPerPixel = 2),
    R8(channels = 1, bytesPerPixel = 1),
    RGBA16F(channels = 4, bytesPerPixel = 8);

    val supportsBufferedImageUpload: Boolean
        get() = this != RGBA16F

    fun byteSize(width: Int, height: Int): Int {
        require(width > 0 && height > 0) { "Texture dimensions must be positive: ${width}x${height}" }
        return Math.multiplyExact(Math.multiplyExact(width, height), bytesPerPixel)
    }
}
