package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.vertex.VertexFormat
import sweetie.evaware.luma.vertex.VertexLayout

fun convertLayout(layout: VertexLayout, attributeNames: Map<Int, String>): VertexFormat {
    val builder = VertexFormat.builder(if (layout.instanced) 1 else 0)
    for (i in 0 until layout.size()) {
        val layoutPos = layout.layoutPos(i)
        val name = attributeNames[layoutPos] ?: "a$layoutPos"
        val format = when (layout.count(i)) {
            1 -> GpuFormat.R32_FLOAT
            2 -> GpuFormat.RG32_FLOAT
            3 -> GpuFormat.RGB32_FLOAT
            4 -> GpuFormat.RGBA32_FLOAT
            else -> error("Unsupported attribute component count: ${layout.count(i)}")
        }
        builder.addAttribute(name, format)
    }
    return builder.build()
}
