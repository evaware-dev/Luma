package sweetie.evaware.luma.texture

data class TextureAtlasSize(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) { "Texture atlas dimensions must be positive" }
    }
}

fun interface TextureAtlasGrowthPolicy {
    fun next(
        current: TextureAtlasSize,
        required: TextureAtlasSize,
        maximum: TextureAtlasSize
    ): TextureAtlasSize?

    companion object {
        val POWER_OF_TWO = TextureAtlasGrowthPolicy { current, required, maximum ->
            val canGrowWidth = current.width < maximum.width
            val canGrowHeight = current.height < maximum.height
            if (!canGrowWidth && !canGrowHeight) {
                null
            } else {
                val growWidth = canGrowWidth && (
                    required.width > current.width ||
                        required.height <= current.height && current.width <= current.height ||
                        !canGrowHeight
                    )
                if (growWidth) {
                    TextureAtlasSize(
                        grow(current.width, required.width, maximum.width),
                        current.height
                    )
                } else {
                    TextureAtlasSize(
                        current.width,
                        grow(current.height, required.height, maximum.height)
                    )
                }
            }
        }

        private fun grow(current: Int, required: Int, maximum: Int): Int =
            maxOf(required, minOf(maximum.toLong(), current.toLong() shl 1).toInt())
    }
}

data class TextureAtlasConfig(
    val initialSize: TextureAtlasSize = TextureAtlasSize(DEFAULT_INITIAL_SIZE, DEFAULT_INITIAL_SIZE),
    val maximumSize: TextureAtlasSize = TextureAtlasSize(DEFAULT_MAXIMUM_SIZE, DEFAULT_MAXIMUM_SIZE),
    val padding: Int = DEFAULT_PADDING,
    val mipmap: Boolean = false,
    val growthPolicy: TextureAtlasGrowthPolicy = TextureAtlasGrowthPolicy.POWER_OF_TWO
) {
    init {
        require(initialSize.width <= maximumSize.width && initialSize.height <= maximumSize.height) {
            "Texture atlas initial size must fit its maximum size"
        }
        require(padding >= 0) { "Texture atlas padding must be non-negative" }
    }

    companion object {
        const val DEFAULT_INITIAL_SIZE = 256
        const val DEFAULT_MAXIMUM_SIZE = 16384
        const val DEFAULT_PADDING = 2
    }
}
