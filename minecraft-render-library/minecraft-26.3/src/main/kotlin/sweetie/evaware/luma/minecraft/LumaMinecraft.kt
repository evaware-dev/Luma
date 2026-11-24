package sweetie.evaware.luma.minecraft

import sweetie.evaware.luma.GraphicsBackend
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.LazyRenderBackend
import sweetie.evaware.luma.backend.blaze3d.preset.RenderpearlPreset
import sweetie.evaware.luma.backend.blaze3d.preset.VulkanPresets
import sweetie.evaware.luma.shader.translator.DefaultShaderTranslator
import sweetie.evaware.luma.shader.translator.ShaderTarget

object LumaMinecraft {
    fun install() {
        val preset = RenderpearlPreset()
        VulkanPresets.active = preset
        Luma.platform = MinecraftRenderPlatform
        Luma.shaderTranslator = DefaultShaderTranslator {
            if (MinecraftRenderPlatform.activeBackend == GraphicsBackend.OPENGL) {
                ShaderTarget.OPENGL
            } else {
                ShaderTarget.BLAZE3D
            }
        }
        Luma.backend = LazyBackend(preset)
    }
}

class LazyBackend(
    preset: RenderpearlPreset = RenderpearlPreset()
) : LazyRenderBackend({
    if (MinecraftRenderPlatform.activeBackend == GraphicsBackend.OPENGL) {
        sweetie.evaware.luma.backend.gl.Backend()
    } else {
        sweetie.evaware.luma.backend.blaze3d.Backend(preset)
    }
})
