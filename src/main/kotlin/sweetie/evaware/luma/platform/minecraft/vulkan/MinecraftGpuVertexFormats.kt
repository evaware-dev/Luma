package sweetie.evaware.luma.platform.minecraft.vulkan

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.VertexFormatElement
import sweetie.evaware.luma.wrapper.api.DrawMode
import sweetie.evaware.luma.wrapper.backend.LumaAttribute
import sweetie.evaware.luma.wrapper.backend.LumaPipeline

internal object MinecraftGpuVertexFormats {
    private var nextElementId = 8
    private val elements = mutableMapOf<LumaAttribute, VertexFormatElement>()
    private val formats = mutableMapOf<LumaPipeline.Layout, VertexFormat>()

    fun format(spec: LumaPipeline): VertexFormat = formats.getOrPut(spec.layout) {
        VertexFormat.builder().apply {
            for (attribute in spec.layout.attributes) {
                add(attribute.name, element(attribute))
            }
        }.build()
    }

    fun drawMode(drawMode: DrawMode) = when (drawMode) {
        DrawMode.TRIANGLES -> VertexFormat.Mode.TRIANGLES
        DrawMode.TRIANGLE_STRIP -> VertexFormat.Mode.TRIANGLE_STRIP
        DrawMode.TRIANGLE_FAN -> VertexFormat.Mode.TRIANGLE_FAN
        DrawMode.LINES -> VertexFormat.Mode.LINES
        DrawMode.LINE_STRIP -> VertexFormat.Mode.DEBUG_LINE_STRIP
        DrawMode.POINTS -> VertexFormat.Mode.POINTS
    }

    private fun element(attribute: LumaAttribute): VertexFormatElement = elements.getOrPut(attribute) {
        VertexFormatElement.register(nextElementId++, 0, format(attribute.components))
    }

    private fun format(components: Int) = when (components) {
        1 -> GpuFormat.R32_FLOAT
        2 -> GpuFormat.RG32_FLOAT
        3 -> GpuFormat.RGB32_FLOAT
        4 -> GpuFormat.RGBA32_FLOAT
        else -> error("Unsupported attribute size: $components")
    }
}
