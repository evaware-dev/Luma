package sweetie.evaware.luma.platform.minecraft.vulkan

import com.mojang.blaze3d.shaders.ShaderSource
import com.mojang.blaze3d.shaders.ShaderType
import net.minecraft.resources.Identifier
import sweetie.evaware.luma.wrapper.shader.LumaGlslLibrary

internal object MinecraftGpuShaderSource : ShaderSource {
    override fun get(id: Identifier, type: ShaderType): String {
        val extension = when (type.name.lowercase()) {
            "vertex" -> "vsh"
            "fragment" -> "fsh"
            else -> error("Unsupported shader type: $type")
        }
        val resourcePath = "assets/${id.namespace}/shaders/${id.path}.$extension"
        return javaClass.classLoader.getResourceAsStream(resourcePath)
            ?.bufferedReader()
            ?.use { LumaGlslLibrary.resolve(it.readText()) }
            ?: error("Missing shader resource: $resourcePath")
    }
}
