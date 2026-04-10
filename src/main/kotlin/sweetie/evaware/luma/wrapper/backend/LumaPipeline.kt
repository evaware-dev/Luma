package sweetie.evaware.luma.wrapper.backend

import sweetie.evaware.luma.wrapper.LumaMetadata
import sweetie.evaware.luma.wrapper.api.DrawMode

class LumaPipeline private constructor(
    val debugName: String,
    val drawMode: DrawMode,
    val layout: Layout,
    val openGl: OpenGlShaders,
    val vulkan: VulkanShaders,
    val textureBinding: TextureBinding?
) {
    val usesTexture
        get() = textureBinding != null

    @Deprecated("Use layout.strideFloats", ReplaceWith("layout.strideFloats"))
    val vertexStrideFloats
        get() = layout.strideFloats

    @Deprecated("Use layout.attributes.map { it.components }", ReplaceWith("layout.attributes.map { it.components }.toIntArray()"))
    val vertexAttributes
        get() = layout.attributes.map(LumaAttribute::components).toIntArray()

    @Deprecated("Use openGl.vertexShader", ReplaceWith("openGl.vertexShader"))
    val openGlVertexShader
        get() = openGl.vertexShader

    @Deprecated("Use openGl.fragmentShader", ReplaceWith("openGl.fragmentShader"))
    val openGlFragmentShader
        get() = openGl.fragmentShader

    @Deprecated("Use vulkan.vertexShader", ReplaceWith("vulkan.vertexShader"))
    val vulkanShaderBase
        get() = vulkan.vertexShader

    constructor(
        debugName: String,
        vertexStrideFloats: Int,
        vertexAttributes: IntArray,
        usesTexture: Boolean,
        openGlVertexShader: String,
        openGlFragmentShader: String,
        vulkanShaderBase: String
    ) : this(
        debugName = debugName,
        drawMode = DrawMode.TRIANGLES,
        layout = Layout(
            vertexAttributes.mapIndexed { index, components ->
                LumaAttribute("Attribute$index", components)
            }
        ),
        openGl = OpenGlShaders(
            vertexShader = openGlVertexShader,
            fragmentShader = openGlFragmentShader
        ),
        vulkan = VulkanShaders(
            vertexShader = vulkanShaderBase
        ),
        textureBinding = if (usesTexture) TextureBinding() else null
    ) {
        require(vertexStrideFloats == layout.strideFloats) {
            "Vertex stride $vertexStrideFloats does not match layout stride ${layout.strideFloats}"
        }
    }

    class Builder internal constructor(
        private val debugName: String
    ) {
        private val attributes = mutableListOf<LumaAttribute>()
        private var drawMode = DrawMode.TRIANGLES
        private var openGl: OpenGlShaders? = null
        private var vulkan: VulkanShaders? = null
        private var textureBinding: TextureBinding? = null

        fun drawMode(drawMode: DrawMode) = apply {
            this.drawMode = drawMode
        }

        fun attribute(name: String, components: Int) = apply {
            attributes += LumaAttribute(name, components)
        }

        fun attribute(components: Int, name: String = "Attribute${attributes.size}") = apply {
            attributes += LumaAttribute(name, components)
        }

        fun attributes(vararg attributes: LumaAttribute) = apply {
            this.attributes += attributes
        }

        fun textured(
            openGlUniform: String = LumaMetadata.defaultOpenGlTextureUniform,
            vulkanSampler: String = LumaMetadata.defaultVulkanTextureSampler
        ) = apply {
            textureBinding = TextureBinding(openGlUniform, vulkanSampler)
        }

        fun openGl(
            vertexShader: String,
            fragmentShader: String,
            matrixUniform: String = LumaMetadata.defaultOpenGlMatrixUniform
        ) = apply {
            openGl = OpenGlShaders(vertexShader, fragmentShader, matrixUniform)
        }

        fun vulkan(
            vertexShader: String,
            fragmentShader: String = vertexShader,
            shaderNamespace: String = LumaMetadata.namespace,
            matrixUniform: String = LumaMetadata.defaultVulkanMatrixUniform
        ) = apply {
            val (resolvedNamespace, resolvedVertexShader) = parseShaderId(vertexShader, shaderNamespace)
            val (fragmentNamespace, resolvedFragmentShader) = parseShaderId(fragmentShader, resolvedNamespace)
            require(fragmentNamespace == resolvedNamespace) {
                "Vulkan vertex/fragment shaders must use the same namespace: $resolvedNamespace != $fragmentNamespace"
            }
            vulkan = VulkanShaders(
                vertexShader = resolvedVertexShader,
                fragmentShader = resolvedFragmentShader,
                shaderNamespace = resolvedNamespace,
                matrixUniform = matrixUniform
            )
        }

        fun build(): LumaPipeline {
            require(attributes.isNotEmpty()) { "Pipeline $debugName must declare at least one vertex attribute" }
            return LumaPipeline(
                debugName = debugName,
                drawMode = drawMode,
                layout = Layout(attributes.toList()),
                openGl = openGl ?: error("Pipeline $debugName is missing OpenGL shader paths"),
                vulkan = vulkan ?: error("Pipeline $debugName is missing Vulkan shader base"),
                textureBinding = textureBinding
            )
        }

        private fun parseShaderId(shaderId: String, defaultNamespace: String): Pair<String, String> {
            val separator = shaderId.indexOf(':')
            return if (separator < 0) {
                defaultNamespace to shaderId
            } else {
                shaderId.substring(0, separator) to shaderId.substring(separator + 1)
            }
        }
    }

    data class OpenGlShaders(
        val vertexShader: String,
        val fragmentShader: String,
        val matrixUniform: String = LumaMetadata.defaultOpenGlMatrixUniform
    )

    data class VulkanShaders(
        val vertexShader: String,
        val fragmentShader: String = vertexShader,
        val shaderNamespace: String = LumaMetadata.namespace,
        val matrixUniform: String = LumaMetadata.defaultVulkanMatrixUniform
    )

    data class TextureBinding(
        val openGlUniform: String = LumaMetadata.defaultOpenGlTextureUniform,
        val vulkanSampler: String = LumaMetadata.defaultVulkanTextureSampler
    )

    data class Layout(
        val attributes: List<LumaAttribute>
    ) {
        init {
            require(attributes.isNotEmpty()) { "Pipeline layout must contain at least one attribute" }
            require(attributes.map(LumaAttribute::name).distinct().size == attributes.size) {
                "Pipeline layout must use unique attribute names"
            }
        }

        val strideFloats = attributes.sumOf(LumaAttribute::components)
    }

    companion object {
        fun builder(debugName: String) = Builder(debugName)
    }
}

data class LumaAttribute(
    val name: String,
    val components: Int
) {
    init {
        require(name.isNotBlank()) { "Attribute name must not be blank" }
        require(components in 1..4) { "Attribute $name must use between 1 and 4 float components" }
    }
}
