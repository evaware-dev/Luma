package sweetie.evaware.luma.shader

open class EasyShader(
    val fragmentPath: String,
    val vertexPath: String = fragmentPath,
    val namespace: String = "luma-renderer"
) : Shader(
    buildPath(namespace, fragmentPath, true),
    buildPath(namespace, vertexPath, false)
) {
    companion object {
        private fun buildPath(namespace: String, path: String, isFragment: Boolean): String {
            val ext = if (namespace == "luma-renderer") {
                if (isFragment) "frag" else "vert"
            } else {
                if (isFragment) "fsh" else "vsh"
            }
            return "assets/$namespace/shaders/core/$path.$ext"
        }
    }
}
