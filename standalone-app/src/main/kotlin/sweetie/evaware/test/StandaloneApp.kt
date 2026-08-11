package sweetie.evaware.test

object StandaloneApp {
    @JvmStatic
    fun main(args: Array<String>) {
        val backend = args.firstOrNull { it.startsWith("--backend=") }
            ?.substringAfter('=')
            ?.lowercase()
            ?: "gl"
        val forwarded = args.filterNot { it.startsWith("--backend=") }.toTypedArray()

        when (backend) {
            "gl", "opengl" -> GlTestApp().run(forwarded)
            "vulkan", "blaze3d" -> VulkanTestApp().run(forwarded)
            else -> error("Unknown backend '$backend'; expected gl or vulkan")
        }
    }
}
