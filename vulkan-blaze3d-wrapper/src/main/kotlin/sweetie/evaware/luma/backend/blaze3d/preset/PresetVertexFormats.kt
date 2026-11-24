package sweetie.evaware.luma.backend.blaze3d.preset

import sweetie.evaware.luma.vertex.ShaderVertType

fun ShaderVertType.toPresetFormat(count: Int, normalized: Boolean): PresetFormat {
    require(count in 1..4) { "Unsupported attribute component count: $count" }
    return when (this) {
        ShaderVertType.FLOAT32 -> when (count) {
            1 -> PresetFormat.R32_FLOAT
            2 -> PresetFormat.RG32_FLOAT
            3 -> PresetFormat.RGB32_FLOAT
            else -> PresetFormat.RGBA32_FLOAT
        }
        ShaderVertType.FLOAT16 -> when (count) {
            1 -> PresetFormat.R16_FLOAT
            2 -> PresetFormat.RG16_FLOAT
            3 -> PresetFormat.RGB16_FLOAT
            else -> PresetFormat.RGBA16_FLOAT
        }
        ShaderVertType.INT8 -> if (normalized) when (count) {
            1 -> PresetFormat.R8_SNORM
            2 -> PresetFormat.RG8_SNORM
            3 -> PresetFormat.RGB8_SNORM
            else -> PresetFormat.RGBA8_SNORM
        } else when (count) {
            1 -> PresetFormat.R8_SINT
            2 -> PresetFormat.RG8_SINT
            3 -> PresetFormat.RGB8_SINT
            else -> PresetFormat.RGBA8_SINT
        }
        ShaderVertType.UINT8 -> if (normalized) when (count) {
            1 -> PresetFormat.R8_UNORM
            2 -> PresetFormat.RG8_UNORM
            3 -> PresetFormat.RGB8_UNORM
            else -> PresetFormat.RGBA8_UNORM
        } else when (count) {
            1 -> PresetFormat.R8_UINT
            2 -> PresetFormat.RG8_UINT
            3 -> PresetFormat.RGB8_UINT
            else -> PresetFormat.RGBA8_UINT
        }
        ShaderVertType.INT16 -> if (normalized) when (count) {
            1 -> PresetFormat.R16_SNORM
            2 -> PresetFormat.RG16_SNORM
            3 -> PresetFormat.RGB16_SNORM
            else -> PresetFormat.RGBA16_SNORM
        } else when (count) {
            1 -> PresetFormat.R16_SINT
            2 -> PresetFormat.RG16_SINT
            3 -> PresetFormat.RGB16_SINT
            else -> PresetFormat.RGBA16_SINT
        }
        ShaderVertType.UINT16 -> if (normalized) when (count) {
            1 -> PresetFormat.R16_UNORM
            2 -> PresetFormat.RG16_UNORM
            3 -> PresetFormat.RGB16_UNORM
            else -> PresetFormat.RGBA16_UNORM
        } else when (count) {
            1 -> PresetFormat.R16_UINT
            2 -> PresetFormat.RG16_UINT
            3 -> PresetFormat.RGB16_UINT
            else -> PresetFormat.RGBA16_UINT
        }
        ShaderVertType.INT32 -> {
            require(!normalized) { "32-bit integer attributes cannot be normalized" }
            when (count) {
                1 -> PresetFormat.R32_SINT
                2 -> PresetFormat.RG32_SINT
                3 -> PresetFormat.RGB32_SINT
                else -> PresetFormat.RGBA32_SINT
            }
        }
        ShaderVertType.UINT32 -> {
            require(!normalized) { "32-bit integer attributes cannot be normalized" }
            when (count) {
                1 -> PresetFormat.R32_UINT
                2 -> PresetFormat.RG32_UINT
                3 -> PresetFormat.RGB32_UINT
                else -> PresetFormat.RGBA32_UINT
            }
        }
    }
}
