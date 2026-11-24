package sweetie.evaware.luma.backend.blaze3d.preset

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.vertex.VertexFormat
import sweetie.evaware.luma.vertex.VertexLayout

fun convertLayout(layout: VertexLayout, attributeNames: Map<Int, String>, stepRate: Int = 0): VertexFormat {
    val builder = VertexFormat.builder(stepRate)
    for (i in 0 until layout.size()) {
        val layoutPos = layout.layoutPos(i)
        val name = attributeNames[layoutPos] ?: "a$layoutPos"
        val format = GpuFormat.valueOf(layout.type(i).toPresetFormat(layout.count(i), layout.normalized(i)).name)
        builder.addAttribute(name, format)
    }
    return builder.build()
}
