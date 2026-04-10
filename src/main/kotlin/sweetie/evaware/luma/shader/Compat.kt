package sweetie.evaware.luma.shader

typealias Shader = sweetie.evaware.luma.wrapper.shader.Shader

object LumaGlslLibrary {
    fun register(name: String, path: String) =
        sweetie.evaware.luma.wrapper.shader.LumaGlslLibrary.register(name, path)

    fun attach() = sweetie.evaware.luma.wrapper.shader.LumaGlslLibrary.attach()

    fun resolve(source: String) = sweetie.evaware.luma.wrapper.shader.LumaGlslLibrary.resolve(source)
}
