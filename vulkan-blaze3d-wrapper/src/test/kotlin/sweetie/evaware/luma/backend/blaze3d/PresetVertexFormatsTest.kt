package sweetie.evaware.luma.backend.blaze3d.preset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import sweetie.evaware.luma.vertex.ShaderVertType

class PresetVertexFormatsTest {
    @Test
    fun mapsAllSupportedTypesCountsAndNormalization() {
        assertEquals(PresetFormat.R32_FLOAT, ShaderVertType.FLOAT32.toPresetFormat(1, false))
        assertEquals(PresetFormat.RG32_FLOAT, ShaderVertType.FLOAT32.toPresetFormat(2, false))
        assertEquals(PresetFormat.RGB32_FLOAT, ShaderVertType.FLOAT32.toPresetFormat(3, false))
        assertEquals(PresetFormat.RGBA32_FLOAT, ShaderVertType.FLOAT32.toPresetFormat(4, true))

        assertEquals(PresetFormat.R16_FLOAT, ShaderVertType.FLOAT16.toPresetFormat(1, false))
        assertEquals(PresetFormat.RG16_FLOAT, ShaderVertType.FLOAT16.toPresetFormat(2, false))
        assertEquals(PresetFormat.RGB16_FLOAT, ShaderVertType.FLOAT16.toPresetFormat(3, false))
        assertEquals(PresetFormat.RGBA16_FLOAT, ShaderVertType.FLOAT16.toPresetFormat(4, true))

        assertEquals(PresetFormat.R8_SINT, ShaderVertType.INT8.toPresetFormat(1, false))
        assertEquals(PresetFormat.RG8_SNORM, ShaderVertType.INT8.toPresetFormat(2, true))
        assertEquals(PresetFormat.RGB8_SINT, ShaderVertType.INT8.toPresetFormat(3, false))
        assertEquals(PresetFormat.RGBA8_SNORM, ShaderVertType.INT8.toPresetFormat(4, true))

        assertEquals(PresetFormat.R8_UINT, ShaderVertType.UINT8.toPresetFormat(1, false))
        assertEquals(PresetFormat.RG8_UNORM, ShaderVertType.UINT8.toPresetFormat(2, true))
        assertEquals(PresetFormat.RGB8_UINT, ShaderVertType.UINT8.toPresetFormat(3, false))
        assertEquals(PresetFormat.RGBA8_UNORM, ShaderVertType.UINT8.toPresetFormat(4, true))

        assertEquals(PresetFormat.R16_SINT, ShaderVertType.INT16.toPresetFormat(1, false))
        assertEquals(PresetFormat.RG16_SNORM, ShaderVertType.INT16.toPresetFormat(2, true))
        assertEquals(PresetFormat.RGB16_SINT, ShaderVertType.INT16.toPresetFormat(3, false))
        assertEquals(PresetFormat.RGBA16_SNORM, ShaderVertType.INT16.toPresetFormat(4, true))

        assertEquals(PresetFormat.R16_UINT, ShaderVertType.UINT16.toPresetFormat(1, false))
        assertEquals(PresetFormat.RG16_UNORM, ShaderVertType.UINT16.toPresetFormat(2, true))
        assertEquals(PresetFormat.RGB16_UINT, ShaderVertType.UINT16.toPresetFormat(3, false))
        assertEquals(PresetFormat.RGBA16_UNORM, ShaderVertType.UINT16.toPresetFormat(4, true))

        assertEquals(PresetFormat.R32_SINT, ShaderVertType.INT32.toPresetFormat(1, false))
        assertEquals(PresetFormat.RG32_SINT, ShaderVertType.INT32.toPresetFormat(2, false))
        assertEquals(PresetFormat.RGB32_SINT, ShaderVertType.INT32.toPresetFormat(3, false))
        assertEquals(PresetFormat.RGBA32_SINT, ShaderVertType.INT32.toPresetFormat(4, false))

        assertEquals(PresetFormat.R32_UINT, ShaderVertType.UINT32.toPresetFormat(1, false))
        assertEquals(PresetFormat.RG32_UINT, ShaderVertType.UINT32.toPresetFormat(2, false))
        assertEquals(PresetFormat.RGB32_UINT, ShaderVertType.UINT32.toPresetFormat(3, false))
        assertEquals(PresetFormat.RGBA32_UINT, ShaderVertType.UINT32.toPresetFormat(4, false))
    }

    @Test
    fun rejectsInvalidCountsAndNormalized32BitIntegers() {
        assertThrows(IllegalArgumentException::class.java) {
            ShaderVertType.FLOAT32.toPresetFormat(0, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShaderVertType.FLOAT32.toPresetFormat(5, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShaderVertType.INT32.toPresetFormat(2, true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShaderVertType.UINT32.toPresetFormat(4, true)
        }
    }
}
