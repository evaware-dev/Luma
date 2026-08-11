package sweetie.evaware.luma.shader.translator

import sweetie.evaware.luma.LumaNames

internal object DirectiveShaderLowerer {
    fun translate(vertexSource: String, fragmentSource: String, target: ShaderTarget): TranslationResult {
        val blaze3d = target == ShaderTarget.BLAZE3D
        if (!blaze3d) {
            return TranslationResult(
                lower(vertexSource, false, emptyList(), emptyMap(), true),
                lower(fragmentSource, false, emptyList(), emptyMap(), false)
            )
        }

        val sharedUniforms = extractUniformLines(vertexSource).ifEmpty {
            extractUniformLines(fragmentSource)
        }
        val outputs = extractShaderOutputs(vertexSource)
        val outputLocations = HashMap<String, Int>(outputs.size)
        for (index in outputs.indices) outputLocations[outputs[index]] = index

        return TranslationResult(
            lower(vertexSource, true, sharedUniforms, outputLocations, true),
            lower(fragmentSource, true, sharedUniforms, outputLocations, false)
        )
    }

    private fun extractUniformLines(source: String): List<String> {
        val result = ArrayList<String>()
        var inside = false
        for (line in source.lineSequence()) {
            val trimmed = line.trim()
            when {
                trimmed == UNIFORMS_OPEN -> inside = true
                trimmed == UNIFORMS_CLOSE -> return result
                inside && trimmed.isNotEmpty() -> result.add(line)
            }
        }
        return result
    }

    private fun extractShaderOutputs(source: String): List<String> {
        val result = ArrayList<String>()
        for (line in source.lineSequence()) {
            OUT_VARYING.find(line)?.let { result.add(it.groupValues[2]) }
        }
        return result
    }

    private fun lower(
        source: String,
        blaze3d: Boolean,
        sharedUniformLines: List<String>,
        outputLocations: Map<String, Int>,
        vertex: Boolean
    ): String {
        val lines = source.lines()
        val result = StringBuilder(source.length + 128)
        val uniformLines = ArrayList<String>()
        val renames = ArrayList<Pair<String, String>>()
        val hasUniformsBlock = source.contains(UNIFORMS_OPEN)
        val hasVersion = lines.any { it.trim().startsWith(VERSION_DIRECTIVE) }
        val emitSharedBlock = blaze3d && !hasUniformsBlock && sharedUniformLines.isNotEmpty()

        if (blaze3d && !hasVersion) {
            appendBlaze3dHeader(result, sharedUniformLines, emitSharedBlock, true)
        }

        var insideUniforms = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith(VERSION_DIRECTIVE)) {
                if (blaze3d) {
                    appendBlaze3dHeader(result, sharedUniformLines, emitSharedBlock, false)
                } else {
                    result.appendLine(line)
                }
                continue
            }
            if (trimmed == UNIFORMS_OPEN) {
                insideUniforms = true
                continue
            }
            if (trimmed == UNIFORMS_CLOSE) {
                insideUniforms = false
                if (blaze3d) {
                    if (sharedUniformLines.isNotEmpty()) appendUniformBlock(result, sharedUniformLines)
                } else {
                    uniformLines.forEach(result::appendLine)
                }
                uniformLines.clear()
                continue
            }
            if (insideUniforms) {
                if (trimmed.isNotEmpty()) uniformLines.add(line)
                continue
            }
            if (appendAttribute(result, line, blaze3d, renames)) continue
            if (appendSampler(result, line, blaze3d, sharedUniformLines.isNotEmpty(), renames)) continue
            if (appendVarying(result, trimmed, blaze3d, vertex, outputLocations)) continue
            result.appendLine(line)
        }

        if (blaze3d) renames.add(VERTEX_ID_GL to VERTEX_ID_VULKAN)
        return applyRenames(result.toString(), renames)
    }

    private fun appendBlaze3dHeader(
        result: StringBuilder,
        sharedUniformLines: List<String>,
        emitBlock: Boolean,
        trailingBlank: Boolean
    ) {
        result.appendLine(LumaNames.BLAZE3D_GLSL_VERSION)
        if (!emitBlock) return
        result.appendLine()
        appendUniformBlock(result, sharedUniformLines)
        if (trailingBlank) result.appendLine()
    }

    private fun appendUniformBlock(result: StringBuilder, uniformLines: List<String>) {
        result.appendLine("layout(std140, binding = 0) uniform ${LumaNames.UNIFORMS_BLOCK} {")
        for (line in uniformLines) result.appendLine(line.replace(UNIFORM_KEYWORD, "    "))
        result.appendLine("};")
    }

    private fun appendAttribute(
        result: StringBuilder,
        line: String,
        blaze3d: Boolean,
        renames: MutableList<Pair<String, String>>
    ): Boolean {
        val match = ATTRIBUTE.matchEntire(line) ?: return false
        val (location, type, name, backendName) = match.destructured
        if (blaze3d) {
            result.appendLine("// ${LumaNames.ATTRIBUTE_DIRECTIVE} $location $type $name $backendName")
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
        blaze3d: Boolean,
        hasSharedUniforms: Boolean,
        renames: MutableList<Pair<String, String>>
    ): Boolean {
        val match = SAMPLER.matchEntire(line) ?: return false
        val type = match.groupValues[1]
        val name = match.groupValues[2]
        val index = match.groupValues[3].toInt()
        if (blaze3d) {
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
        line: String,
        blaze3d: Boolean,
        vertex: Boolean,
        outputLocations: Map<String, Int>
    ): Boolean {
        if (!blaze3d) return false
        OUT_VARYING.matchEntire(line)?.let { match ->
            val type = match.groupValues[1]
            val name = match.groupValues[2]
            val location = if (vertex) outputLocations[name] else 0
            if (location != null) {
                result.appendLine("layout(location = $location) out $type $name;")
                return true
            }
        }
        if (!vertex) {
            IN_VARYING.matchEntire(line)?.let { match ->
                val type = match.groupValues[1]
                val name = match.groupValues[2]
                val location = outputLocations[name]
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
        val alternation = renames.asSequence()
            .map { Regex.escape(it.first) }
            .sortedByDescending(String::length)
            .joinToString("|")
        return Regex("""\b(?:$alternation)\b""").replace(source) { match ->
            replacements[match.value] ?: match.value
        }
    }

    private const val VERSION_DIRECTIVE = "#version"
    private const val UNIFORMS_OPEN = "@uniforms"
    private const val UNIFORMS_CLOSE = "@end"
    private const val VERTEX_ID_GL = "gl_VertexID"
    private const val VERTEX_ID_VULKAN = "gl_VertexIndex"

    private val UNIFORM_KEYWORD = Regex("""^\s*uniform\s+""")
    private val ATTRIBUTE = Regex(
        """^\s*${Regex.escape(LumaNames.ATTRIBUTE_DIRECTIVE)}\s+(\d+)\s+([a-zA-Z0-9_]+)\s+([a-zA-Z0-9_]+)\s+([a-zA-Z0-9_]+)\s*$"""
    )
    private val SAMPLER = Regex("""^\s*@sampler\s+([a-zA-Z0-9_]+)\s+([a-zA-Z0-9_]+)\s+(\d+)\s*$""")
    private val OUT_VARYING = Regex("""^\s*out\s+([a-zA-Z0-9_]+)\s+([a-zA-Z0-9_]+)\s*;""")
    private val IN_VARYING = Regex("""^in\s+([a-zA-Z0-9_]+)\s+([a-zA-Z0-9_]+)\s*;$""")
}
