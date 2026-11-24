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

    abstract fun write(writer: Std140Writer, handle: UniformHandle?)

    fun write(writer: Std140Writer, uniforms: ShaderUniforms) {
        write(writer, getHandle(uniforms))
    }
}

internal class VulkanFloat1Uniform(name: String, type: String) :
    VulkanUniform(name, type, Float.SIZE_BYTES, Float.SIZE_BYTES) {
    override fun write(writer: Std140Writer, handle: UniformHandle?) {
        val typed = handle as? Float1Uniform
        writer.putFloat(typed?.value ?: 0f)
    }
}

internal class VulkanFloat2Uniform(name: String, type: String) :
    VulkanUniform(name, type, 2 * Float.SIZE_BYTES, 2 * Float.SIZE_BYTES) {
    override fun write(writer: Std140Writer, handle: UniformHandle?) {
        val typed = handle as? Float2Uniform
        if (typed != null) {
            writer.putVec2(typed.first, typed.second)
        } else {
            writer.putVec2(0f, 0f)
        }
    }
}

internal class VulkanFloat3Uniform(name: String, type: String) :
    VulkanUniform(name, type, 4 * Float.SIZE_BYTES, 4 * Float.SIZE_BYTES) {
    override fun write(writer: Std140Writer, handle: UniformHandle?) {
        val typed = handle as? Float3Uniform
        if (typed != null) {
            writer.putVec3(typed.first, typed.second, typed.third)
        } else {
            writer.putVec3(0f, 0f, 0f)
        }
    }
}

internal class VulkanFloat4Uniform(name: String, type: String) :
    VulkanUniform(name, type, 4 * Float.SIZE_BYTES, 4 * Float.SIZE_BYTES) {
    override fun write(writer: Std140Writer, handle: UniformHandle?) {
        val typed = handle as? Float4Uniform
        if (typed != null) {
            writer.putVec4(typed.first, typed.second, typed.third, typed.fourth)
        } else {
            writer.putVec4(0f, 0f, 0f, 0f)
        }
    }
}

internal class VulkanInt1Uniform(name: String, type: String) :
    VulkanUniform(name, type, Int.SIZE_BYTES, Int.SIZE_BYTES) {
    override fun write(writer: Std140Writer, handle: UniformHandle?) {
        val typed = handle as? Int1Uniform
        writer.putInt(typed?.value ?: 0)
    }
}

private val IDENTITY_MATRIX = Matrix4f()

internal class VulkanMat4Uniform(name: String, type: String) :
    VulkanUniform(name, type, 4 * Float.SIZE_BYTES, 16 * Float.SIZE_BYTES) {
    override fun write(writer: Std140Writer, handle: UniformHandle?) {
        val typed = handle as? Mat4Uniform
        writer.putMat4(typed?.value ?: IDENTITY_MATRIX)
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
