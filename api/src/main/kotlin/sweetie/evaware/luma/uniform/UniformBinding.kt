package sweetie.evaware.luma.uniform

abstract class UniformBinding(val name: String) {
    private var cachedIndex = -1

    fun getHandle(uniforms: ShaderUniforms): UniformHandle? {
        val entries = uniforms.registry.entries

        val cached = cachedIndex
        if (cached in entries.indices && entries[cached].name == name) {
            return entries[cached].handle
        }

        for (i in entries.indices) {
            if (entries[i].name == name) {
                cachedIndex = i
                return entries[i].handle
            }
        }
        return null
    }
}
