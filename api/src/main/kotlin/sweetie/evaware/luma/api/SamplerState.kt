package sweetie.evaware.luma.api

enum class TextureFilter {
    NEAREST,
    LINEAR
}

enum class TextureWrap {
    CLAMP_TO_EDGE,
    REPEAT
}

data class SamplerState(
    val minFilter: TextureFilter = TextureFilter.LINEAR,
    val magFilter: TextureFilter = TextureFilter.LINEAR,
    val wrapU: TextureWrap = TextureWrap.CLAMP_TO_EDGE,
    val wrapV: TextureWrap = TextureWrap.CLAMP_TO_EDGE,
    val maxAnisotropy: Int = 1,
    val maxLod: Double? = null
) {
    init {
        require(maxAnisotropy >= 1) { "maxAnisotropy must be >= 1: $maxAnisotropy" }
        if (maxLod != null) {
            require(maxLod.isFinite() && maxLod >= 0.0) { "maxLod must be finite and >= 0.0: $maxLod" }
        }
    }

    companion object {
        @JvmField
        val LINEAR_CLAMP = SamplerState(
            minFilter = TextureFilter.LINEAR,
            magFilter = TextureFilter.LINEAR,
            wrapU = TextureWrap.CLAMP_TO_EDGE,
            wrapV = TextureWrap.CLAMP_TO_EDGE
        )

        @JvmField
        val NEAREST_CLAMP = SamplerState(
            minFilter = TextureFilter.NEAREST,
            magFilter = TextureFilter.NEAREST,
            wrapU = TextureWrap.CLAMP_TO_EDGE,
            wrapV = TextureWrap.CLAMP_TO_EDGE
        )

        @JvmField
        val LINEAR_REPEAT = SamplerState(
            minFilter = TextureFilter.LINEAR,
            magFilter = TextureFilter.LINEAR,
            wrapU = TextureWrap.REPEAT,
            wrapV = TextureWrap.REPEAT
        )

        @JvmField
        val NEAREST_REPEAT = SamplerState(
            minFilter = TextureFilter.NEAREST,
            magFilter = TextureFilter.NEAREST,
            wrapU = TextureWrap.REPEAT,
            wrapV = TextureWrap.REPEAT
        )
    }
}
