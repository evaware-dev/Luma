package sweetie.evaware.luma.wrapper.shader

import sweetie.evaware.luma.wrapper.LumaMetadata

sealed interface WrapperShader {
    val matrixUniform: String
}

data class OpenGlShader(
    val vertexShader: String,
    val fragmentShader: String,
    override val matrixUniform: String = LumaMetadata.defaultOpenGlMatrixUniform
) : WrapperShader

data class VulkanShader(
    val vertexShader: String,
    val fragmentShader: String = vertexShader,
    val shaderNamespace: String = LumaMetadata.namespace,
    override val matrixUniform: String = LumaMetadata.defaultVulkanMatrixUniform
) : WrapperShader
