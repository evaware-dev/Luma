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
        } catch (_: Throwable) {
            false
        }

        return if (isBlaze3d) {
            val sharedUniforms = extractUniformLines(vertResolved).ifEmpty { extractUniformLines(fragResolved) }
            val vertOutputs = extractShaderOutputs(vertResolved)
            val vertOutputsMap = vertOutputs.mapIndexed { index, name -> name to index }.toMap()
            TranslationResult(
                translateShader(vertResolved, true, sharedUniforms, vertOutputsMap, true),
                translateShader(fragResolved, true, sharedUniforms, vertOutputsMap, false)
            )
        } else {
            TranslationResult(
                translateShader(vertResolved, false, emptyList(), emptyMap(), true),
                translateShader(fragResolved, false, emptyList(), emptyMap(), false)
            )
        }
    }

    private fun extractUniformLines(source: String): List<String> {
        val lines = source.lines()
        val list = ArrayList<String>()
        var inUniforms = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed == "@uniforms") {
                inUniforms = true
                continue
            }
            if (trimmed == "@end") {
                inUniforms = false
                break
            }
            if (inUniforms && trimmed.isNotEmpty()) {
                list.add(line)
            }
        }
        return list
    }

    private fun extractShaderOutputs(source: String): List<String> {
        val list = ArrayList<String>()
        val regex = Regex("""^\s*out\s+[a-zA-Z0-9_]+\s+([a-zA-Z0-9_]+)\s*;""")
        for (line in source.lines()) {
            val match = regex.find(line)
            if (match != null) {
                list.add(match.groupValues[1])
            }
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
        val result = StringBuilder()
        
        var inUniforms = false
        val uniformLines = ArrayList<String>()
        val renames = ArrayList<Pair<String, String>>()
        val hasUniformsBlock = source.contains("@uniforms")
        val hasVersion = lines.any { it.trim().startsWith("#version") }

        if (toBlaze3d && !hasVersion) {
            result.appendLine("#version 450")
            if (!hasUniformsBlock && sharedUniformLines.isNotEmpty()) {
                result.appendLine()
                result.appendLine("layout(std140, binding = 0) uniform ${LumaNames.UNIFORMS_BLOCK} {")
                for (uLine in sharedUniformLines) {
                    val cleanLine = uLine.replace(Regex("""^\s*uniform\s+"""), "    ")
                    result.appendLine(cleanLine)
                }
                result.appendLine("};")
                result.appendLine()
            }
        }

        for (line in lines) {
            val trimmed = line.trim()

            if (trimmed.startsWith("#version")) {
                if (toBlaze3d) {
                    result.appendLine("#version 450")
                    if (!hasUniformsBlock && sharedUniformLines.isNotEmpty()) {
                        result.appendLine()
                        result.appendLine("layout(std140, binding = 0) uniform ${LumaNames.UNIFORMS_BLOCK} {")
                        for (uLine in sharedUniformLines) {
                            val cleanLine = uLine.replace(Regex("""^\s*uniform\s+"""), "    ")
                            result.appendLine(cleanLine)
                        }
                        result.appendLine("};")
                    }
                } else {
                    result.appendLine(line)
                }
                continue
            }

            if (trimmed == "@uniforms") {
                inUniforms = true
                continue
            }

            if (trimmed == "@end") {
                inUniforms = false
                if (toBlaze3d) {
                    if (sharedUniformLines.isNotEmpty()) {
                        result.appendLine("layout(std140, binding = 0) uniform ${LumaNames.UNIFORMS_BLOCK} {")
                        for (uLine in sharedUniformLines) {
                            val cleanLine = uLine.replace(Regex("""^\s*uniform\s+"""), "    ")
                            result.appendLine(cleanLine)
                        }
                        result.appendLine("};")
                    }
                } else {
                    for (uLine in uniformLines) {
                        result.appendLine(uLine)
                    }
                }
                uniformLines.clear()
                continue
            }

            if (inUniforms) {
                if (trimmed.isNotEmpty()) {
                    uniformLines.add(line)
                }
                continue
            }

            val inMatch = Regex("""^\s*@in\s+(\d+)\s+([a-zA-Z0-9_]+)\s+([a-zA-Z0-9_]+)\s+([a-zA-Z0-9_]+)\s*$""").matchEntire(line)
            if (inMatch != null) {
                val location = inMatch.groupValues[1]
                val type = inMatch.groupValues[2]
                val name = inMatch.groupValues[3]
                val backendName = inMatch.groupValues[4]

                if (toBlaze3d) {
                    result.appendLine("// @in $location $type $name $backendName")
                    result.appendLine("layout(location = $location) in $type $backendName;")
                    renames.add(name to backendName)
                } else {
                    result.appendLine("layout(location = $location) in $type $name;")
                }
                continue
            }

            val samplerMatch = Regex("""^\s*@sampler\s+([a-zA-Z0-9_]+)\s+([a-zA-Z0-9_]+)\s+(\d+)\s*$""").matchEntire(line)
            if (samplerMatch != null) {
                val type = samplerMatch.groupValues[1]
                val name = samplerMatch.groupValues[2]
                val index = samplerMatch.groupValues[3].toInt()

                if (toBlaze3d) {
                    val backendName = "Sampler$index"
                    val binding = if (sharedUniformLines.isNotEmpty()) index + 1 else index
                    result.appendLine("layout(binding = $binding) uniform $type $backendName;")
                    renames.add(name to backendName)
                } else {
                    result.appendLine("uniform $type $name;")
                }
                continue
            }

            val outMatch = Regex("""^out\s+([a-zA-Z0-9_]+)\s+([a-zA-Z0-9_]+)\s*;$""").matchEntire(trimmed)
            if (outMatch != null && toBlaze3d) {
                val type = outMatch.groupValues[1]
                val name = outMatch.groupValues[2]
                if (isVertex) {
                    val loc = vertOutputsMap[name]
                    if (loc != null) {
                        result.appendLine("layout(location = $loc) out $type $name;")
                        continue
                    }
                } else {
                    result.appendLine("layout(location = 0) out $type $name;")
                    continue
                }
            }

            val inVarMatch = Regex("""^in\s+([a-zA-Z0-9_]+)\s+([a-zA-Z0-9_]+)\s*;$""").matchEntire(trimmed)
            if (inVarMatch != null && toBlaze3d && !isVertex) {
                val type = inVarMatch.groupValues[1]
                val name = inVarMatch.groupValues[2]
                val loc = vertOutputsMap[name]
                if (loc != null) {
                    result.appendLine("layout(location = $loc) in $type $name;")
                    continue
                }
            }

            result.appendLine(line)
        }

        var finalSource = result.toString()
        for ((oldName, newName) in renames) {
            finalSource = finalSource.replace(Regex("\\b$oldName\\b"), newName)
        }

        return finalSource
    }
}
