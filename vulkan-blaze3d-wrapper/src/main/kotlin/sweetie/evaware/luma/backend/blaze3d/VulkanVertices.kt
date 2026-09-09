package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.vertex.VertexFormat
import sweetie.evaware.luma.vertex.ShaderVertType
import sweetie.evaware.luma.vertex.VertexLayout

fun convertLayout(layout: VertexLayout, attributeNames: Map<Int, String>, stepRate: Int = 0): VertexFormat {
    val builder = VertexFormat.builder(stepRate)
    for (i in 0 until layout.size()) {
        val layoutPos = layout.layoutPos(i)
        val name = attributeNames[layoutPos] ?: "a$layoutPos"
        val format = vertexFormat(layout.type(i), layout.count(i), layout.normalized(i))
        builder.addAttribute(name, format)
    }
    return builder.build()
}

private fun vertexFormat(type: ShaderVertType, count: Int, normalized: Boolean): GpuFormat {
    require(count in 1..4) { "Unsupported attribute component count: $count" }
    val prefix = when (count) {
        1 -> "R"
        2 -> "RG"
        3 -> "RGB"
        else -> "RGBA"
    }
    val suffix = when (type) {
        ShaderVertType.FLOAT32 -> "32_FLOAT"
        ShaderVertType.FLOAT16 -> "16_FLOAT"
        ShaderVertType.INT8 -> if (normalized) "8_SNORM" else "8_SINT"
        ShaderVertType.UINT8 -> if (normalized) "8_UNORM" else "8_UINT"
        ShaderVertType.INT16 -> if (normalized) "16_SNORM" else "16_SINT"
        ShaderVertType.UINT16 -> if (normalized) "16_UNORM" else "16_UINT"
        ShaderVertType.INT32 -> {
            require(!normalized) { "32-bit integer attributes cannot be normalized" }
            "32_SINT"
        }
        ShaderVertType.UINT32 -> {
            require(!normalized) { "32-bit integer attributes cannot be normalized" }
            "32_UINT"
        }
    }
    return GpuFormat.valueOf(prefix + suffix)
}
