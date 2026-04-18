package sweetie.evaware.luma.wrapper.texture

data class SampledTexture(
    val binding: TextureBinding,
    val sampler: LumaSampler = LumaSampler.LINEAR_CLAMP
)
