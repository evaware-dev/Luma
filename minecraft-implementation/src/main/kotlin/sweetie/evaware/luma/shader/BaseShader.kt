package sweetie.evaware.luma.shader

import sweetie.evaware.luma.LumaAssets
import sweetie.evaware.luma.shader.Shader

abstract class BaseShader(
    val fragmentName: String,
    val vertexName: String = fragmentName
) : Shader(
    LumaAssets.coreShader(fragmentName),
    LumaAssets.coreShader(vertexName)
) {
    abstract fun setupLayout()

    override fun load() {
        setupLayout()
        super.load()
    }

    override fun close() {
        super.close()
    }
}
