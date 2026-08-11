package sweetie.evaware.luma.shader.translator

import sweetie.evaware.luma.shader.GlslLibrary
import sweetie.evaware.luma.vertex.VertexLayout

enum class ShaderTarget {
    OPENGL,
    BLAZE3D
}

class DefaultShaderTranslator(
    private val targetSelector: () -> ShaderTarget,
    private val sourceResolver: (String) -> String
) : ShaderTranslator {
    constructor(target: ShaderTarget = ShaderTarget.OPENGL) : this(
        targetSelector = { target },
        sourceResolver = GlslLibrary::resolve
    )

    constructor(targetSelector: () -> ShaderTarget) : this(
        targetSelector = targetSelector,
        sourceResolver = GlslLibrary::resolve
    )

    override fun translate(
        vertexSource: String,
        fragmentSource: String,
        layout: VertexLayout
    ): TranslationResult = DirectiveShaderLowerer.translate(
        sourceResolver(vertexSource),
        sourceResolver(fragmentSource),
        targetSelector()
    )
}
