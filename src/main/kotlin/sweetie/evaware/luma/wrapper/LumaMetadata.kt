package sweetie.evaware.luma.wrapper

object LumaMetadata {
    const val displayName = "Luma"
    const val rendererName = "$displayName Renderer"
    const val namespace = "luma-renderer"
    const val loggerName = namespace
    const val whiteTextureId = "$namespace:white"
    const val vulkanPassLabel = "$namespace-vulkan-pass"
    const val defaultOpenGlMatrixUniform = "uMatrix"
    const val defaultOpenGlTextureUniform = "uTexture"
    const val defaultVulkanMatrixUniform = "Projection"
    const val defaultVulkanTextureSampler = "Sampler0"

    fun asset(path: String) = "assets/$namespace/$path"

    fun shader(path: String) = asset("shaders/$path")

    fun coreShader(path: String) = shader("core/$path")

    fun includeShader(path: String) = shader("include/$path")

    fun font(path: String) = asset("fonts/$path")

    fun pipelineLabel(name: String) = "$namespace-$name"

    fun textureLabel(name: String) = "$namespace-texture:$name"

    fun message(text: String) = "$displayName $text"

    fun coreVulkanShader(name: String) = "core/$name"
}
