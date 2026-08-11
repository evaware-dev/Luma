package sweetie.evaware.luma.backend.gl

data class GlBackendConfig(
    val initialTextureUnits: Int = 4,
    val statePolicy: GlStatePolicy = GlStatePolicy.PRESERVE,
    val ownedDrawFramebuffer: Int = 0,
    val ownedReadFramebuffer: Int = ownedDrawFramebuffer
) {
    init {
        require(initialTextureUnits > 0) { "Initial texture unit capacity must be positive" }
    }
}

enum class GlStatePolicy {
    PRESERVE,
    OWNED
}
