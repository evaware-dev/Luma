package sweetie.evaware.luma.shader.translator

import sweetie.evaware.luma.vertex.VertexLayout

fun interface ShaderTranslator {
    fun translate(vertexSource: String, fragmentSource: String, layout: VertexLayout): TranslationResult
}

data class TranslationResult @JvmOverloads constructor(
    val vertexSource: String,
    val fragmentSource: String,
    val reflection: ShaderReflection? = null
)
