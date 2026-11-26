package sweetie.evaware.luma.backend.blaze3d

import org.joml.Matrix4f
import sweetie.evaware.luma.uniform.*

data class UniformInfo(val name: String, val type: String)

internal class VulkanUniformLayout(uniformInfos: List<UniformInfo>) {
    private val names = Array(uniformInfos.size) { uniformInfos[it].name }
    private val typeCodes = ByteArray(uniformInfos.size) { typeCodeFor(uniformInfos[it].type) }
    private val cachedIndices = IntArray(uniformInfos.size) { -1 }
    private val resolvedHandles = arrayOfNulls<UniformHandle>(uniformInfos.size)
    private var resolvedUniforms: ShaderUniforms? = null
    private var resolvedEntryCount: Int = -1

    val isEmpty: Boolean get() = typeCodes.isEmpty()

    val blockBytes: Int = run {
        var bytes = 0
        for (i in typeCodes.indices) {
            val code = typeCodes[i]
            val alignment = alignmentBytes(code)
            val size = sizeBytes(code)
            val rem = bytes % alignment
            val aligned = if (rem == 0) bytes else Math.addExact(bytes, alignment - rem)
            bytes = Math.addExact(aligned, size)
        }
        bytes
    }

    fun resolveHandles(uniforms: ShaderUniforms): Array<UniformHandle?> {
        val entries = uniforms.registry.entries
        val entryCount = entries.size
        if (resolvedUniforms === uniforms && resolvedEntryCount == entryCount) {
            return resolvedHandles
        }
        for (i in names.indices) {
            val name = names[i]
            val cached = cachedIndices[i]
            var found: UniformHandle? = null
            if (cached in entries.indices && entries[cached].name == name) {
                found = entries[cached].handle
            } else {
                for (j in entries.indices) {
                    if (entries[j].name == name) {
                        cachedIndices[i] = j
                        found = entries[j].handle
                        break
                    }
                }
            }
            resolvedHandles[i] = found
        }
        resolvedUniforms = uniforms
        resolvedEntryCount = entryCount
        return resolvedHandles
    }

    fun writeAll(writer: Std140Writer, handles: Array<UniformHandle?>) {
        val codes = typeCodes
        for (i in codes.indices) {
            val handle = handles[i]
            when (codes[i]) {
                TYPE_FLOAT1 -> {
                    val h = handle as? Float1Uniform
                    writer.putFloat(h?.value ?: 0f)
                }
                TYPE_FLOAT2 -> {
                    val h = handle as? Float2Uniform
                    if (h != null) writer.putVec2(h.first, h.second) else writer.putVec2(0f, 0f)
                }
                TYPE_FLOAT3 -> {
                    val h = handle as? Float3Uniform
                    if (h != null) writer.putVec3(h.first, h.second, h.third) else writer.putVec3(0f, 0f, 0f)
                }
                TYPE_FLOAT4 -> {
                    val h = handle as? Float4Uniform
                    if (h != null) writer.putVec4(h.first, h.second, h.third, h.fourth) else writer.putVec4(0f, 0f, 0f, 0f)
                }
                TYPE_INT1 -> {
                    val h = handle as? Int1Uniform
                    writer.putInt(h?.value ?: 0)
                }
                TYPE_MAT4 -> {
                    val h = handle as? Mat4Uniform
                    writer.putMat4(h?.value ?: IDENTITY_MATRIX)
                }
            }
            if (handle != null) handle.isDirty = false
        }
    }

    fun clearCache() {
        resolvedHandles.fill(null)
        resolvedUniforms = null
        resolvedEntryCount = -1
    }

    private companion object {
        private const val TYPE_FLOAT1: Byte = 0
        private const val TYPE_FLOAT2: Byte = 1
        private const val TYPE_FLOAT3: Byte = 2
        private const val TYPE_FLOAT4: Byte = 3
        private const val TYPE_INT1: Byte = 4
        private const val TYPE_MAT4: Byte = 5

        private val IDENTITY_MATRIX = Matrix4f()

        private fun typeCodeFor(type: String): Byte = when (type) {
            "float" -> TYPE_FLOAT1
            "vec2" -> TYPE_FLOAT2
            "vec3" -> TYPE_FLOAT3
            "vec4" -> TYPE_FLOAT4
            "int" -> TYPE_INT1
            "mat4" -> TYPE_MAT4
            else -> error("Unsupported Vulkan uniform type: $type")
        }

        private fun alignmentBytes(code: Byte): Int = when (code) {
            TYPE_FLOAT1, TYPE_INT1 -> Float.SIZE_BYTES
            TYPE_FLOAT2 -> 2 * Float.SIZE_BYTES
            TYPE_FLOAT3, TYPE_FLOAT4, TYPE_MAT4 -> 4 * Float.SIZE_BYTES
            else -> error("Unknown uniform type code: $code")
        }

        private fun sizeBytes(code: Byte): Int = when (code) {
            TYPE_FLOAT1, TYPE_INT1 -> Float.SIZE_BYTES
            TYPE_FLOAT2 -> 2 * Float.SIZE_BYTES
            TYPE_FLOAT3, TYPE_FLOAT4 -> 4 * Float.SIZE_BYTES
            TYPE_MAT4 -> 16 * Float.SIZE_BYTES
            else -> error("Unknown uniform type code: $code")
        }
    }
}

fun parseUniforms(vertex: String, fragment: String): List<UniformInfo> {
    val uboRegex = Regex("""layout\s*\([^)]*\)\s*uniform\s+LumaUniforms\s*\{([^}]+)}""", RegexOption.MULTILINE)
    val match = uboRegex.find(vertex) ?: uboRegex.find(fragment)
    if (match != null) {
        val content = match.groupValues[1]
        val list = ArrayList<UniformInfo>()
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("//")) continue
            val parts = trimmed.removeSuffix(";").trim().split(Regex("\\s+"))
            if (parts.size >= 2) {
                val name = parts.last()
                val type = parts[parts.size - 2]
                list.add(UniformInfo(name, type))
            }
        }
        return list
    }

    val list = ArrayList<String>()
    val lines = (vertex.lines() + fragment.lines())
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("uniform ") && !trimmed.contains("sampler2D")) {
            list.add(trimmed.removePrefix("uniform ").trim().removeSuffix(";").trim())
        }
    }

    return list.distinct().map { u ->
        val parts = u.split(Regex("\\s+"))
        val name = parts.last()
        val type = parts[parts.size - 2]
        UniformInfo(name, type)
    }
}
