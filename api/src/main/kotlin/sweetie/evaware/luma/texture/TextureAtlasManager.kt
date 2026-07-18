package sweetie.evaware.luma.texture

import sweetie.evaware.luma.api.Preparable

object TextureAtlasManager : Preparable, AutoCloseable {
    private val atlases = LinkedHashMap<String, TextureAtlas>()

    @Volatile
    private var atlasSnapshot = emptyArray<TextureAtlas>()

    override val isPrepared: Boolean
        @Synchronized get() = atlases.values.all(TextureAtlas::isPrepared)

    @Synchronized
    fun register(id: String, atlas: TextureAtlas): TextureAtlas {
        require(id.isNotBlank()) { "Texture atlas id must not be blank" }
        check(id !in atlases) { "Texture atlas is already registered: $id" }
        atlases[id] = atlas
        updateSnapshot()
        return atlas
    }

    @Synchronized
    fun create(id: String, config: TextureAtlasConfig = TextureAtlasConfig()): TextureAtlas =
        register(id, TextureAtlas(config))

    @Synchronized
    fun getOrCreate(id: String, config: TextureAtlasConfig = TextureAtlasConfig()): TextureAtlas =
        atlases[id] ?: create(id, config)

    @Synchronized
    operator fun get(id: String): TextureAtlas =
        atlases[id] ?: error("Missing texture atlas: $id")

    @Synchronized
    fun find(id: String): TextureAtlas? = atlases[id]

    @Synchronized
    fun ids(): Set<String> = LinkedHashSet(atlases.keys)

    override fun prepare() {
        atlasSnapshot.forEach(TextureAtlas::prepare)
    }

    fun processPending() {
        atlasSnapshot.forEach(TextureAtlas::processPending)
    }

    fun remove(id: String, close: Boolean = true): TextureAtlas? {
        val atlas = synchronized(this) {
            atlases.remove(id).also { if (it != null) updateSnapshot() }
        } ?: return null
        if (close) atlas.close()
        return atlas
    }

    override fun close() {
        val registered = synchronized(this) {
            val copy = atlases.values.toList()
            atlases.clear()
            updateSnapshot()
            copy
        }
        registered.forEach(TextureAtlas::close)
    }

    private fun updateSnapshot() {
        atlasSnapshot = atlases.values.toTypedArray()
    }
}
