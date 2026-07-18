package sweetie.evaware.luma.backend.blaze3d

data class VulkanBackendConfig(
    val initialVertexBufferBytes: Int = 1 shl 10,
    val initialUniformBufferBytes: Int = 1 shl 8,
    val initialVertexStagingBytes: Int = 1 shl 20,
    val initialUniformStagingBytes: Int = 1 shl 16,
    val uniformScratchBytes: Int = 1 shl 8,
    val precompileDefaultPipeline: Boolean = true
) {
    init {
        require(initialVertexBufferBytes > 0) { "Initial vertex buffer capacity must be positive" }
        require(initialUniformBufferBytes > 0) { "Initial uniform buffer capacity must be positive" }
        require(initialVertexStagingBytes > 0) { "Initial vertex staging capacity must be positive" }
        require(initialUniformStagingBytes > 0) { "Initial uniform staging capacity must be positive" }
        require(uniformScratchBytes > 0) { "Uniform scratch capacity must be positive" }
    }
}
