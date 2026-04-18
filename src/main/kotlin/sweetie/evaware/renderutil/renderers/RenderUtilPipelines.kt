package sweetie.evaware.renderutil.renderers

import sweetie.evaware.luma.wrapper.LumaMetadata
import sweetie.evaware.luma.wrapper.backend.LumaAttribute
import sweetie.evaware.luma.wrapper.backend.LumaPipeline
import sweetie.evaware.luma.wrapper.shader.OpenGlShader
import sweetie.evaware.luma.wrapper.shader.VulkanShader
import sweetie.evaware.luma.wrapper.shader.WrapperShaderSet

internal object RenderUtilPipelines {
    val uber = LumaPipeline.builder("renderutil_uber")
        .attributes(
            LumaAttribute("Position", 2),
            LumaAttribute("UV0", 2),
            LumaAttribute("Color", 4),
            LumaAttribute("State", 2),
            LumaAttribute("Scissor", 4)
        )
        .textured()
        .shaders(
            WrapperShaderSet(
                openGl = OpenGlShader(
                    vertexShader = LumaMetadata.coreShader("uber.vert"),
                    fragmentShader = LumaMetadata.coreShader("uber.frag")
                ),
                vulkan = VulkanShader(LumaMetadata.coreVulkanShader("uber"))
            )
        )
        .build()
}
