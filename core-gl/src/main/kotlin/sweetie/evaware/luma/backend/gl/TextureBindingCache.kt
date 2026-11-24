package sweetie.evaware.luma.backend.gl

internal class TextureBindingCache(initialUnits: Int) {
    private var textures = arrayOfNulls<GlTexture>(initialUnits)

    fun isBound(unit: Int, texture: GlTexture): Boolean {
        ensureCapacity(unit + 1)
        return textures[unit] === texture
    }

    fun bind(unit: Int, texture: GlTexture) {
        ensureCapacity(unit + 1)
        textures[unit] = texture
    }

    inline fun forEachBoundUnit(texture: GlTexture, action: (Int) -> Unit) {
        val current = textures
        for (unit in current.indices) {
            if (current[unit] === texture) {
                action(unit)
            }
        }
    }

    fun invalidateAll() {
        textures.fill(null)
    }

    private fun ensureCapacity(required: Int) {
        if (required <= textures.size) return
        val previousSize = textures.size
        val capacity = maxOf(required, maxOf(1, previousSize shl 1))
        textures = textures.copyOf(capacity)
    }
}
