package sweetie.evaware.luma

object LumaAssets {
    const val DEMO_ICON_ID = "demo_icon"
    const val DEMO_CHECKER_ID = "demo_checker"

    const val CORE_SHADER_DIR = "assets/luma/shaders/core/"
    const val INCLUDE_DIR = "assets/luma/shaders/include/"

    const val SCISSOR_INCLUDE = INCLUDE_DIR + "scissor.glsl"
    const val MATRIX_INCLUDE = INCLUDE_DIR + "matrix.glsl"
    const val RECT_INCLUDE = INCLUDE_DIR + "rect.glsl"

    const val DEMO_ICON = "assets/luma/icon.png"

    fun coreShader(name: String): String = CORE_SHADER_DIR + name
}
