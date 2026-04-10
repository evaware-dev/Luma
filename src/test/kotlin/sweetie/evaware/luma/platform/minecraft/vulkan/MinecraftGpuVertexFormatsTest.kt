package sweetie.evaware.luma.platform.minecraft.vulkan

import com.mojang.blaze3d.vertex.VertexFormat
import sweetie.evaware.luma.wrapper.backend.LumaPipeline
import kotlin.test.Test
import kotlin.test.assertEquals

class MinecraftGpuVertexFormatsTest {
    @Test
    fun `vulkan vertex format follows pipeline layout and draw mode`() {
        val pipeline = LumaPipeline.builder("format-test")
            .attribute("Position", 2)
            .attribute("Normal", 3)
            .attribute("Color", 4)
            .openGl("test.vert", "test.frag")
            .vulkan("core/format_test")
            .build()

        val format = MinecraftGpuVertexFormats.format(pipeline)

        assertEquals(listOf("Position", "Normal", "Color"), format.elementAttributeNames)
        assertEquals(9 * Float.SIZE_BYTES, format.vertexSize)
        assertEquals(VertexFormat.Mode.TRIANGLES, MinecraftGpuVertexFormats.drawMode(pipeline.drawMode))
    }
}
