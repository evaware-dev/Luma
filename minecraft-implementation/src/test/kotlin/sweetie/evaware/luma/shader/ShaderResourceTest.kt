package sweetie.evaware.luma.shader

import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertContains
import kotlin.test.assertFalse
import sweetie.evaware.luma.shader.translator.DefaultShaderTranslator
import sweetie.evaware.luma.shader.translator.ShaderTarget
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.GraphicsBackend
import sweetie.evaware.luma.RenderPlatform
import sweetie.evaware.luma.vertex.VertexLayout
import sweetie.evaware.luma.vertex.ShaderVertType
import sweetie.evaware.luma.backend.blaze3d.Program
import net.minecraft.resources.Identifier
import org.lwjgl.util.shaderc.Shaderc
import java.io.File

class ShaderResourceTest {
    @BeforeTest
    fun setUp() {
        GlslLibrary.register("scissor", "assets/luma/shaders/include/scissor.glsl")
        GlslLibrary.register("matrix", "assets/luma/shaders/include/matrix.glsl")
        GlslLibrary.register("rect", "assets/luma/shaders/include/rect.glsl")
    }

    @Test
    fun testProgramInfoParsingForVulkan() {
        val originalPlatform = Luma.platform
        try {
            Luma.platform = object : RenderPlatform {
                override val activeBackend = GraphicsBackend.OTHER
                override fun getGuiScaledWidth() = 0f
                override fun getGuiScaledHeight() = 0f
                override fun getGuiScale() = 1f
                override fun getWindowHeight() = 0f
                override fun getViewport(viewport: IntArray) = false
            }
            val translator = DefaultShaderTranslator(ShaderTarget.BLAZE3D)
            val layout = VertexLayout().apply {
                add(ShaderVertType.FLOAT, 2, 0)
                add(ShaderVertType.FLOAT, 2, 1)
                add(ShaderVertType.FLOAT, 4, 2)
                add(ShaderVertType.FLOAT, 4, 3)
                add(ShaderVertType.FLOAT, 4, 4)
                add(ShaderVertType.FLOAT, 4, 5)
            }
            val vert = resourceText("assets/luma/shaders/core/rounded_rect.vert")
            val frag = resourceText("assets/luma/shaders/core/rounded_rect.frag")
            val result = translator.translate(vert, frag, layout)
            val info = Program(
                Identifier.fromNamespaceAndPath("luma", "test"),
                result.vertexSource,
                result.fragmentSource,
                layout
            )
            println("uniformInfos size = ${info.uniformInfos.size}")
            for (u in info.uniformInfos) {
                println("  uniform: ${u.name} (${u.type})")
            }
            println("attributeNames size = ${info.attributeNames.size}")
            for ((loc, name) in info.attributeNames) {
                println("  attribute: $loc -> $name")
            }
        } finally {
            Luma.platform = originalPlatform
        }
    }
    @Test
    fun testVulkanTranslatorAttributesPreserved() {
        val originalPlatform = Luma.platform
        try {
            Luma.platform = object : RenderPlatform {
                override val activeBackend = GraphicsBackend.OTHER
                override fun getGuiScaledWidth() = 0f
                override fun getGuiScaledHeight() = 0f
                override fun getGuiScale() = 1f
                override fun getWindowHeight() = 0f
                override fun getViewport(viewport: IntArray) = false
            }
            val translator = DefaultShaderTranslator(ShaderTarget.BLAZE3D)
            val layout = VertexLayout().apply {
                add(ShaderVertType.FLOAT, 2, 0)
                add(ShaderVertType.FLOAT, 4, 1)
            }
            val vert = "@in 0 vec2 a0 Position\nvoid main() {}"
            val frag = "void main() {}"
            val result = translator.translate(vert, frag, layout)
            assertContains(result.vertexSource, "// @in 0 vec2 Position Position")
            assertContains(result.vertexSource, "layout(location = 0) in vec2 Position;")
        } finally {
            Luma.platform = originalPlatform
        }
    }
    @Test
    fun testRectQuadTranslation() {
        val originalPlatform = Luma.platform
        try {
            Luma.platform = object : RenderPlatform {
                override val activeBackend = GraphicsBackend.OTHER
                override fun getGuiScaledWidth() = 0f
                override fun getGuiScaledHeight() = 0f
                override fun getGuiScale() = 1f
                override fun getWindowHeight() = 0f
                override fun getViewport(viewport: IntArray) = false
            }
            val translator = DefaultShaderTranslator(ShaderTarget.BLAZE3D)
            val layout = VertexLayout().apply {
                add(ShaderVertType.FLOAT, 2, 0)
                add(ShaderVertType.FLOAT, 2, 1)
                add(ShaderVertType.FLOAT, 4, 2)
                add(ShaderVertType.FLOAT, 4, 3)
                add(ShaderVertType.FLOAT, 4, 4)
                add(ShaderVertType.FLOAT, 4, 5)
            }
            val vert = resourceText("assets/luma/shaders/core/rounded_rect.vert")
            val frag = resourceText("assets/luma/shaders/core/rounded_rect.frag")
            val result = translator.translate(vert, frag, layout)
            println("--- TRANSLATED VERTEX SHADER ---")
            println(result.vertexSource)
            println("--- TRANSLATED FRAGMENT SHADER ---")
            println(result.fragmentSource)
        } finally {
            Luma.platform = originalPlatform
        }
    }
    @Test
    fun `scissor is vertex payload for batching`() {
        val roundedVertex = resourceText("assets/luma/shaders/core/rounded_rect.vert")
        val roundedFragment = resourceText("assets/luma/shaders/core/rounded_rect.frag")

        assertContains(roundedVertex, "@in 4 vec4 a4 UvRect")
        assertContains(roundedVertex, "@in 5 vec4 a5 Scissor")
        assertContains(roundedVertex, "out vec4 vScissor")
        assertContains(roundedFragment, "@sampler sampler2D uTexture 0")
        assertContains(roundedFragment, "scissorVisible(vScissor")
        assertFalse(roundedFragment.contains("uniform vec4 uScissor"))
    }

    @Test
    fun testShadercCompilation() {
        val vertFile = File("run/debug_shader_program_3_TRIANGLES_vert.glsl")
        val fragFile = File("run/debug_shader_program_3_TRIANGLES_frag.glsl")
        if (!vertFile.exists() || !fragFile.exists()) {
            println("Shader files do not exist in run/ directory")
            return
        }
        val vertSource = vertFile.readText()
        val fragSource = fragFile.readText()

        val compiler = Shaderc.shaderc_compiler_initialize()
        val options = Shaderc.shaderc_compile_options_initialize()

        try {
            val vertResult = Shaderc.shaderc_compile_into_spv(
                compiler,
                vertSource,
                Shaderc.shaderc_glsl_vertex_shader,
                "vert.glsl",
                "main",
                options
            )
            val vertStatus = Shaderc.shaderc_result_get_compilation_status(vertResult)
            if (vertStatus != Shaderc.shaderc_compilation_status_success) {
                val error = Shaderc.shaderc_result_get_error_message(vertResult)
                println("Vertex Shader compilation failed with status $vertStatus:")
                println(error)
            } else {
                println("Vertex Shader compiled successfully!")
            }
            Shaderc.shaderc_result_release(vertResult)

            val fragResult = Shaderc.shaderc_compile_into_spv(
                compiler,
                fragSource,
                Shaderc.shaderc_glsl_fragment_shader,
                "frag.glsl",
                "main",
                options
            )
            val fragStatus = Shaderc.shaderc_result_get_compilation_status(fragResult)
            if (fragStatus != Shaderc.shaderc_compilation_status_success) {
                val error = Shaderc.shaderc_result_get_error_message(fragResult)
                println("Fragment Shader compilation failed with status $fragStatus:")
                println(error)
            } else {
                println("Fragment Shader compiled successfully!")
            }
            Shaderc.shaderc_result_release(fragResult)
        } finally {
            Shaderc.shaderc_compile_options_release(options)
            Shaderc.shaderc_compiler_release(compiler)
        }
    }

    @Test
    fun printBlaze3dMethods() {
        val classes = listOf(
            "com.mojang.blaze3d.shaders.ShaderSource",
            "com.mojang.blaze3d.shaders.ShaderType",
            "net.minecraft.client.Minecraft"
        )
        for (className in classes) {
            try {
                val clazz = Class.forName(className)
                println("Class: ${clazz.name}")
                println("  Fields:")
                for (field in clazz.declaredFields) {
                    println("    ${field.type.name} ${field.name}")
                }
                println("  Methods:")
                for (method in clazz.declaredMethods) {
                    val params = method.parameterTypes.joinToString(", ") { it.name }
                    println("    ${method.returnType.name} ${method.name}($params)")
                }
            } catch (e: Exception) {
                println("Failed to load $className: ${e.message}")
            }
        }
    }

    private fun resourceText(path: String) = javaClass.classLoader.getResourceAsStream(path)
        ?.bufferedReader()
        ?.use { it.readText() }
        ?: error("Missing shader resource: $path")
}
