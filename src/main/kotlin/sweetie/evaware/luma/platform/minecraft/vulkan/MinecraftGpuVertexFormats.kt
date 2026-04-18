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
        VertexFormatElement.register(nextElementId++, 0, format(attribute))
    }

    private fun format(attribute: LumaAttribute) = when (attribute.type) {
        sweetie.evaware.luma.wrapper.vertex.ShaderVertType.FLOAT -> when (attribute.components) {
            1 -> GpuFormat.R32_FLOAT
            2 -> GpuFormat.RG32_FLOAT
            3 -> GpuFormat.RGB32_FLOAT
            4 -> GpuFormat.RGBA32_FLOAT
            else -> error("Unsupported attribute size: ${attribute.components}")
        }
        sweetie.evaware.luma.wrapper.vertex.ShaderVertType.BYTE -> when (attribute.components) {
            1 -> if (attribute.normalized) GpuFormat.R8_SNORM else GpuFormat.R8_SINT
            2 -> if (attribute.normalized) GpuFormat.RG8_SNORM else GpuFormat.RG8_SINT
            3 -> if (attribute.normalized) GpuFormat.RGB8_SNORM else GpuFormat.RGB8_SINT
            4 -> if (attribute.normalized) GpuFormat.RGBA8_SNORM else GpuFormat.RGBA8_SINT
            else -> error("Unsupported attribute size: ${attribute.components}")
        }
        sweetie.evaware.luma.wrapper.vertex.ShaderVertType.UNSIGNED_BYTE -> when (attribute.components) {
            1 -> if (attribute.normalized) GpuFormat.R8_UNORM else GpuFormat.R8_UINT
            2 -> if (attribute.normalized) GpuFormat.RG8_UNORM else GpuFormat.RG8_UINT
            3 -> if (attribute.normalized) GpuFormat.RGB8_UNORM else GpuFormat.RGB8_UINT
            4 -> if (attribute.normalized) GpuFormat.RGBA8_UNORM else GpuFormat.RGBA8_UINT
            else -> error("Unsupported attribute size: ${attribute.components}")
        }
        sweetie.evaware.luma.wrapper.vertex.ShaderVertType.SHORT -> when (attribute.components) {
            1 -> if (attribute.normalized) GpuFormat.R16_SNORM else GpuFormat.R16_SINT
            2 -> if (attribute.normalized) GpuFormat.RG16_SNORM else GpuFormat.RG16_SINT
            3 -> if (attribute.normalized) GpuFormat.RGB16_SNORM else GpuFormat.RGB16_SINT
            4 -> if (attribute.normalized) GpuFormat.RGBA16_SNORM else GpuFormat.RGBA16_SINT
            else -> error("Unsupported attribute size: ${attribute.components}")
        }
        sweetie.evaware.luma.wrapper.vertex.ShaderVertType.UNSIGNED_SHORT -> when (attribute.components) {
            1 -> if (attribute.normalized) GpuFormat.R16_UNORM else GpuFormat.R16_UINT
            2 -> if (attribute.normalized) GpuFormat.RG16_UNORM else GpuFormat.RG16_UINT
            3 -> if (attribute.normalized) GpuFormat.RGB16_UNORM else GpuFormat.RGB16_UINT
            4 -> if (attribute.normalized) GpuFormat.RGBA16_UNORM else GpuFormat.RGBA16_UINT
            else -> error("Unsupported attribute size: ${attribute.components}")
        }
        sweetie.evaware.luma.wrapper.vertex.ShaderVertType.INT -> {
            require(!attribute.normalized) { "INT attributes cannot be normalized in Vulkan" }
            when (attribute.components) {
                1 -> GpuFormat.R32_SINT
                2 -> GpuFormat.RG32_SINT
                3 -> GpuFormat.RGB32_SINT
                4 -> GpuFormat.RGBA32_SINT
                else -> error("Unsupported attribute size: ${attribute.components}")
            }
        }
        sweetie.evaware.luma.wrapper.vertex.ShaderVertType.UNSIGNED_INT -> {
            require(!attribute.normalized) { "UNSIGNED_INT attributes cannot be normalized in Vulkan" }
            when (attribute.components) {
                1 -> GpuFormat.R32_UINT
                2 -> GpuFormat.RG32_UINT
                3 -> GpuFormat.RGB32_UINT
                4 -> GpuFormat.RGBA32_UINT
                else -> error("Unsupported attribute size: ${attribute.components}")
            }
        }
    }
}
