package sweetie.evaware.luma.wrapper.backend

import sweetie.evaware.luma.wrapper.api.DrawMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LumaPipelineTest {
    @Test
    fun `builder keeps user-defined shader ids, layout, and draw mode`() {
        val pipeline = LumaPipeline.builder("custom")
            .attribute("Position", 2)
            .attribute("UV0", 2)
            .attribute("Color", 4)
            .drawMode(DrawMode.TRIANGLE_STRIP)
            .textured(openGlUniform = "diffuseSampler", vulkanSampler = "Diffuse")
            .openGl(
                vertexShader = "assets/example/shaders/custom.vert",
                fragmentShader = "assets/example/shaders/custom.frag",
                matrixUniform = "ProjectionMatrix"
            )
            .vulkan("example:core/custom_pipeline", matrixUniform = "Transforms")
            .build()

        assertEquals("custom", pipeline.debugName)
        assertEquals(DrawMode.TRIANGLE_STRIP, pipeline.drawMode)
        assertEquals(8, pipeline.layout.strideFloats)
        assertEquals(listOf("Position", "UV0", "Color"), pipeline.layout.attributes.map { it.name })
        assertEquals("assets/example/shaders/custom.vert", pipeline.openGl.vertexShader)
        assertEquals("ProjectionMatrix", pipeline.openGl.matrixUniform)
        assertEquals("core/custom_pipeline", pipeline.vulkan.vertexShader)
        assertEquals("core/custom_pipeline", pipeline.vulkan.fragmentShader)
        assertEquals("example", pipeline.vulkan.shaderNamespace)
        assertEquals("Transforms", pipeline.vulkan.matrixUniform)
        assertEquals("diffuseSampler", pipeline.textureBinding?.openGlUniform)
        assertEquals("Diffuse", pipeline.textureBinding?.vulkanSampler)
        assertTrue(pipeline.usesTexture)
    }

    @Test
    fun `legacy constructor stays triangles and defaults unnamed attributes`() {
        val pipeline = LumaPipeline(
            debugName = "legacy",
            vertexStrideFloats = 6,
            vertexAttributes = intArrayOf(2, 4),
            usesTexture = false,
            openGlVertexShader = "legacy.vert",
            openGlFragmentShader = "legacy.frag",
            vulkanShaderBase = "core/legacy"
        )

        assertEquals(DrawMode.TRIANGLES, pipeline.drawMode)
        assertEquals(listOf("Attribute0", "Attribute1"), pipeline.layout.attributes.map { it.name })
        assertFalse(pipeline.usesTexture)
    }
}
