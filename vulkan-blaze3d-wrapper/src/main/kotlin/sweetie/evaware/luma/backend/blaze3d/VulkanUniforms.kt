package sweetie.evaware.luma.backend.blaze3d

import org.joml.Matrix4f
import sweetie.evaware.luma.uniform.*

data class UniformInfo(val name: String, val type: String)

internal abstract class VulkanUniform(
    name: String,
    val type: String,
    val alignmentBytes: Int,
    val sizeBytes: Int
) : UniformBinding(name) {

    fun isHandleDirty(uniforms: ShaderUniforms): Boolean =
        getHandle(uniforms)?.isDirty ?: false

    fun clearDirty(uniforms: ShaderUniforms) {
        getHandle(uniforms)?.isDirty = false
    }

    abstract fun write(writer: Std140Writer, uniforms: ShaderUniforms)
}

internal class VulkanFloat1Uniform(name: String, type: String) :
    VulkanUniform(name, type, Float.SIZE_BYTES, Float.SIZE_BYTES) {
    override fun write(writer: Std140Writer, uniforms: ShaderUniforms) {
        val handle = getHandle(uniforms) as? Float1Uniform
        val v = handle?.value ?: 0f
        writer.putFloat(v)
    }
}

internal class VulkanFloat2Uniform(name: String, type: String) :
    VulkanUniform(name, type, 2 * Float.SIZE_BYTES, 2 * Float.SIZE_BYTES) {
    override fun write(writer: Std140Writer, uniforms: ShaderUniforms) {
        val handle = getHandle(uniforms) as? Float2Uniform
        if (handle != null) {
            writer.putVec2(handle.first, handle.second)
        } else {
            writer.putVec2(0f, 0f)
        }
    }
}

internal class VulkanFloat3Uniform(name: String, type: String) :
    VulkanUniform(name, type, 4 * Float.SIZE_BYTES, 4 * Float.SIZE_BYTES) {
    override fun write(writer: Std140Writer, uniforms: ShaderUniforms) {
        val handle = getHandle(uniforms) as? Float3Uniform
        if (handle != null) {
            writer.putVec3(handle.first, handle.second, handle.third)
        } else {
            writer.putVec3(0f, 0f, 0f)
        }
    }
}

internal class VulkanFloat4Uniform(name: String, type: String) :
    VulkanUniform(name, type, 4 * Float.SIZE_BYTES, 4 * Float.SIZE_BYTES) {
    override fun write(writer: Std140Writer, uniforms: ShaderUniforms) {
        val handle = getHandle(uniforms) as? Float4Uniform
        if (handle != null) {
            writer.putVec4(handle.first, handle.second, handle.third, handle.fourth)
        } else {
            writer.putVec4(0f, 0f, 0f, 0f)
        }
    }
}

internal class VulkanInt1Uniform(name: String, type: String) :
    VulkanUniform(name, type, Int.SIZE_BYTES, Int.SIZE_BYTES) {
    override fun write(writer: Std140Writer, uniforms: ShaderUniforms) {
        val handle = getHandle(uniforms) as? Int1Uniform
        val v = handle?.value ?: 0
        writer.putInt(v)
    }
}

private val IDENTITY_MATRIX = Matrix4f()

internal class VulkanMat4Uniform(name: String, type: String) :
    VulkanUniform(name, type, 4 * Float.SIZE_BYTES, 16 * Float.SIZE_BYTES) {
    override fun write(writer: Std140Writer, uniforms: ShaderUniforms) {
        val handle = getHandle(uniforms) as? Mat4Uniform
        val matrix = handle?.value ?: IDENTITY_MATRIX
        writer.putMat4(matrix)
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
