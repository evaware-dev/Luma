package sweetie.evaware.luma.backend.gl

data class GlBackendConfig(
    val initialTextureUnits: Int = 4
) {
    init {
        require(initialTextureUnits > 0) { "Initial texture unit capacity must be positive" }
    }
}
