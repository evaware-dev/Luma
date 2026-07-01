package sweetie.evaware.luma.shader.translator

import sweetie.evaware.luma.GraphicsBackend
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.LumaNames
import sweetie.evaware.luma.vertex.VertexLayout
import sweetie.evaware.luma.shader.GlslLibrary

class DefaultShaderTranslator : ShaderTranslator {

    override fun translate(
        vertexSource: String,
        fragmentSource: String,
        layout: VertexLayout
    ): TranslationResult {
        val vertResolved = GlslLibrary.resolve(vertexSource)
        val fragResolved = GlslLibrary.resolve(fragmentSource)

        val isBlaze3d = try {
            Luma.platform.activeBackend != GraphicsBackend.OPENGL
        } catch (_: Exception) {
            false
        }

        if (!isBlaze3d) {
            return TranslationResult(
                translateShader(vertResolved, false, emptyList(), emptyMap(), true),
                translateShader(fragResolved, false, emptyList(), emptyMap(), false)
            )
        }

        val sharedUniforms = extractUniformLines(vertResolved).ifEmpty { extractUniformLines(fragResolved) }
        val vertOutputs = extractShaderOutputs(vertResolved)
        val vertOutputsMap = vertOutputs.mapIndexed { index, name -> name to index }.toMap()

        return TranslationResult(
            translateShader(vertResolved, true, sharedUniforms, vertOutputsMap, true),
            translateShader(fragResolved, true, sharedUniforms, vertOutputsMap, false)
        )
    }

    private fun extractUniformLines(source: String): List<String> {
        val list = ArrayList<String>()
        var inUniforms = false
        for (line in source.lines()) {
            val trimmed = line.trim()
            when {
                trimmed == UNIFORMS_OPEN -> inUniforms = true
                trimmed == UNIFORMS_CLOSE -> return list
                inUniforms && trimmed.isNotEmpty() -> list.add(line)
            }
        }
        return list
    }

    private fun extractShaderOutputs(source: String): List<String> {
        val list = ArrayList<String>()
        for (line in source.lines()) {
            OUT_VARYING.find(line)?.let { list.add(it.groupValues[2]) }
        }
        return list
    }

    private fun translateShader(
        source: String,
        toBlaze3d: Boolean,
        sharedUniformLines: List<String>,
        vertOutputsMap: Map<String, Int>,
        isVertex: Boolean
    ): String {
        val lines = source.lines()
        val result = StringBuilder(source.length + 128)

        val uniformLines = ArrayList<String>()
        val renames = ArrayList<Pair<String, String>>()
        val hasUniformsBlock = source.contains(UNIFORMS_OPEN)
        val hasVersion = lines.any { it.trim().startsWith(VERSION_DIRECTIVE) }
        val emitSharedBlock = toBlaze3d && !hasUniformsBlock && sharedUniformLines.isNotEmpty()

        if (toBlaze3d && !hasVersion) {
            appendBlaze3dHeader(result, sharedUniformLines, emitSharedBlock, trailingBlank = true)
        }

        var inUniforms = false
        for (line in lines) {
            val trimmed = line.trim()

            if (trimmed.startsWith(VERSION_DIRECTIVE)) {
                if (toBlaze3d) {
                    appendBlaze3dHeader(result, sharedUniformLines, emitSharedBlock, trailingBlank = false)
                } else {
                    result.appendLine(line)
                }
                continue
            }

            if (trimmed == UNIFORMS_OPEN) {
                inUniforms = true
                continue
            }

            if (trimmed == UNIFORMS_CLOSE) {
                inUniforms = false
                if (toBlaze3d) {
                    if (sharedUniformLines.isNotEmpty()) {
                        appendUniformBlock(result, sharedUniformLines)
                    }
                } else {
                    uniformLines.forEach(result::appendLine)
                }
                uniformLines.clear()
                continue
            }

            if (inUniforms) {
                if (trimmed.isNotEmpty()) uniformLines.add(line)
                continue
            }

            if (appendAttribute(result, line, toBlaze3d, renames)) continue
            if (appendSampler(result, line, toBlaze3d, sharedUniformLines.isNotEmpty(), renames)) continue
            if (appendVarying(result, trimmed, toBlaze3d, isVertex, vertOutputsMap)) continue

            result.appendLine(line)
        }

        if (toBlaze3d) {
            renames.add(VERTEX_ID_GL to VERTEX_ID_VULKAN)
        }

        return applyRenames(result.toString(), renames)
    }

    private fun appendBlaze3dHeader(
        result: StringBuilder,
        sharedUniformLines: List<String>,
        emitBlock: Boolean,
        trailingBlank: Boolean
    ) {
        result.appendLine(LumaNames.BLAZE3D_GLSL_VERSION)
        if (emitBlock) {
            result.appendLine()
            appendUniformBlock(result, sharedUniformLines)
            if (trailingBlank) result.appendLine()
        }
    }

    private fun appendUniformBlock(result: StringBuilder, uniformLines: List<String>) {
        result.appendLine("layout(std140, binding = 0) uniform ${LumaNames.UNIFORMS_BLOCK} {")
        for (line in uniformLines) {
            result.appendLine(line.replace(UNIFORM_KEYWORD, "    "))
        }
        result.appendLine("};")
    }

    private fun appendAttribute(
        result: StringBuilder,
        line: String,
        toBlaze3d: Boolean,
        renames: MutableList<Pair<String, String>>
    ): Boolean {
        val match = ATTRIBUTE.matchEntire(line) ?: return false
        val (location, type, name, backendName) = match.destructured
        if (toBlaze3d) {
            result.appendLine("// @in $location $type $name $backendName")
            result.appendLine("layout(location = $location) in $type $backendName;")
            renames.add(name to backendName)
        } else {
            result.appendLine("layout(location = $location) in $type $name;")
        }
        return true
    }

    private fun appendSampler(
        result: StringBuilder,
        line: String,
        toBlaze3d: Boolean,
        hasSharedUniforms: Boolean,
        renames: MutableList<Pair<String, String>>
    ): Boolean {
        val match = SAMPLER.matchEntire(line) ?: return false
        val type = match.groupValues[1]
        val name = match.groupValues[2]
        val index = match.groupValues[3].toInt()
        if (toBlaze3d) {
            val backendName = "${LumaNames.SAMPLER_PREFIX}$index"
            val binding = if (hasSharedUniforms) index + 1 else index
            result.appendLine("layout(binding = $binding) uniform $type $backendName;")
            renames.add(name to backendName)
        } else {
            result.appendLine("uniform $type $name;")
        }
        return true
    }

    private fun appendVarying(
        result: StringBuilder,
        trimmed: String,
        toBlaze3d: Boolean,
        isVertex: Boolean,
        vertOutputsMap: Map<String, Int>
    ): Boolean {
        if (!toBlaze3d) return false

        OUT_VARYING.matchEntire(trimmed)?.let { match ->
            val type = match.groupValues[1]
            val name = match.groupValues[2]
            val location = if (isVertex) vertOutputsMap[name] else 0
            if (location != null) {
                result.appendLine("layout(location = $location) out $type $name;")
                return true
            }
        }

        if (!isVertex) {
            IN_VARYING.matchEntire(trimmed)?.let { match ->
                val type = match.groupValues[1]
                val name = match.groupValues[2]
                val location = vertOutputsMap[name]
                if (location != null) {
                    result.appendLine("layout(location = $location) in $type $name;")
                    return true
                }
            }
        }

        return false
    }

    private fun applyRenames(source: String, renames: List<Pair<String, String>>): String {
        if (renames.isEmpty()) return source

        val replacements = HashMap<String, String>(renames.size * 2)
        for ((old, new) in renames) replacements[old] = new

        val alternation = renames
            .map { Regex.escape(it.first) }
            .sortedByDescending { it.length }
            .joinToString("|")
        val pattern = Regex("""\b(?:$alternation)\b""")

        return pattern.replace(source) { match -> replacements[match.value] ?: match.value }
    }

    private companion object {
        const val VERSION_DIRECTIVE = "#version"
        const val UNIFORMS_OPEN = "@uniforms"
        const val UNIFORMS_CLOSE = "@end"

        const val VERTEX_ID_GL = "gl_VertexID"
        const val VERTEX_ID_VULKAN = "gl_VertexIndex"

        val UNIFORM_KEYWORD = Regex("""^\s*uniform\s+""")
        val ATTRIBUTE = Regex("""^\s*@in\s+(\d+)\s+([a-zA-Z0-9_]+)\s+([a-zA-Z0-9_]+)\s+([a-zA-Z0-9_]+)\s*$""")
        val SAMPLER = Regex("""^\s*@sampler\s+([a-zA-Z0-9_]+)\s+([a-zA-Z0-9_]+)\s+(\d+)\s*$""")
        val OUT_VARYING = Regex("""^\s*out\s+([a-zA-Z0-9_]+)\s+([a-zA-Z0-9_]+)\s*;""")
        val IN_VARYING = Regex("""^in\s+([a-zA-Z0-9_]+)\s+([a-zA-Z0-9_]+)\s*;$""")
    }
}
