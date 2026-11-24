package sweetie.evaware.luma.shader.translator

data class ShaderAttributeInfo(
    val location: Int,
    val type: String,
    val name: String,
    val backendName: String = name
)

data class ShaderUniformInfo(
    val name: String,
    val type: String
)

data class ShaderSamplerInfo(
    val name: String,
    val backendName: String,
    val type: String,
    val unit: Int,
    val binding: Int = unit
)

data class ShaderReflection(
    val attributes: List<ShaderAttributeInfo> = emptyList(),
    val uniforms: List<ShaderUniformInfo> = emptyList(),
    val samplers: List<ShaderSamplerInfo> = emptyList()
)
