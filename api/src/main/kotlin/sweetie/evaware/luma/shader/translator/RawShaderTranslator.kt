package sweetie.evaware.luma.shader.translator

import sweetie.evaware.luma.shader.GlslLibrary
import sweetie.evaware.luma.vertex.VertexLayout

object RawShaderTranslator : ShaderTranslator {
    override fun translate(
        vertexSource: String,
        fragmentSource: String,
        layout: VertexLayout
    ) = TranslationResult(
        GlslLibrary.resolve(vertexSource),
        GlslLibrary.resolve(fragmentSource)
    )
}
