package sweetie.evaware.luma.wrapper.backend

import sweetie.evaware.luma.wrapper.LumaMetadata
import sweetie.evaware.luma.wrapper.api.DrawMode
import sweetie.evaware.luma.wrapper.shader.OpenGlShader
import sweetie.evaware.luma.wrapper.shader.VulkanShader
import sweetie.evaware.luma.wrapper.shader.WrapperShaderSet
import sweetie.evaware.luma.wrapper.texture.WrapperTextureBinding
import sweetie.evaware.luma.wrapper.vertex.ShaderVertType

class LumaPipeline private constructor(
    val debugName: String,
    val drawMode: DrawMode,
    val layout: Layout,
    val shaders: WrapperShaderSet,
    val textureBinding: WrapperTextureBinding?
) {
    val openGl get() = shaders.openGl

    val vulkan get() = shaders.vulkan

    val usesTexture
        get() = textureBinding != null

    @Deprecated("Use layout.strideBytes", ReplaceWith("layout.strideBytes"))
    val vertexStrideFloats
        get() = layout.strideBytes / Float.SIZE_BYTES

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
        shaders = WrapperShaderSet(
            openGl = OpenGlShader(
                vertexShader = openGlVertexShader,
                fragmentShader = openGlFragmentShader
            ),
            vulkan = VulkanShader(
                vertexShader = vulkanShaderBase
            )
        ),
        textureBinding = if (usesTexture) WrapperTextureBinding() else null
    ) {
        require(vertexStrideFloats * Float.SIZE_BYTES == layout.strideBytes) {
            "Vertex stride ${vertexStrideFloats * Float.SIZE_BYTES} bytes does not match layout stride ${layout.strideBytes}"
        }
    }

    class Builder internal constructor(
        private val debugName: String
    ) {
        private val attributes = mutableListOf<LumaAttribute>()
        private var drawMode = DrawMode.TRIANGLES
        private var openGl: OpenGlShader? = null
        private var vulkan: VulkanShader? = null
        private var textureBinding: WrapperTextureBinding? = null

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

        fun shaders(openGl: OpenGlShader, vulkan: VulkanShader) = apply {
            this.openGl = openGl
            this.vulkan = vulkan
        }

        fun shaders(shaderSet: WrapperShaderSet) = apply {
            shaders(shaderSet.openGl, shaderSet.vulkan)
        }

        fun textured(
            openGlUniform: String = LumaMetadata.defaultOpenGlTextureUniform,
            vulkanSampler: String = LumaMetadata.defaultVulkanTextureSampler
        ) = apply {
            textureBinding = WrapperTextureBinding(
                openGl = sweetie.evaware.luma.wrapper.texture.OpenGlTextureBinding(openGlUniform),
                vulkan = sweetie.evaware.luma.wrapper.texture.VulkanTextureBinding(vulkanSampler)
            )
        }

        @Deprecated("Prefer a single shaders(...) call with WrapperShaderSet")
        fun openGl(
            vertexShader: String,
            fragmentShader: String,
            matrixUniform: String = LumaMetadata.defaultOpenGlMatrixUniform
        ) = apply {
            openGl = OpenGlShader(vertexShader, fragmentShader, matrixUniform)
        }

        @Deprecated("Prefer a single shaders(...) call with WrapperShaderSet")
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
            vulkan = VulkanShader(
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
                shaders = WrapperShaderSet(
                    openGl = openGl ?: error("Pipeline $debugName is missing OpenGL shader paths"),
                    vulkan = vulkan ?: error("Pipeline $debugName is missing Vulkan shader base")
                ),
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

    data class Layout(
        val attributes: List<LumaAttribute>
    ) {
        init {
            require(attributes.isNotEmpty()) { "Pipeline layout must contain at least one attribute" }
            require(attributes.map(LumaAttribute::name).distinct().size == attributes.size) {
                "Pipeline layout must use unique attribute names"
            }
        }

        val strideBytes = attributes.sumOf(LumaAttribute::byteSize)

        @Deprecated("Use strideBytes", ReplaceWith("strideBytes"))
        val strideFloats = strideBytes / Float.SIZE_BYTES
    }

    companion object {
        fun builder(debugName: String) = Builder(debugName)
    }
}

data class LumaAttribute(
    val name: String,
    val components: Int,
    val type: ShaderVertType = ShaderVertType.FLOAT,
    val normalized: Boolean = false
) {
    init {
        require(name.isNotBlank()) { "Attribute name must not be blank" }
        require(components in 1..4) { "Attribute $name must use between 1 and 4 components" }
        require(type.floating || !normalized || type.byteSize <= Short.SIZE_BYTES) {
            "Normalized attributes are only supported for 8-bit and 16-bit fixed-point types"
        }
    }

    val byteSize = components * type.byteSize
}
