package sweetie.evaware.luma.texture

import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.Preparable
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import javax.imageio.ImageIO

class TextureAtlas(
    val config: TextureAtlasConfig = TextureAtlasConfig()
) : Preparable, AutoCloseable {
    class Region internal constructor(
        private val atlas: TextureAtlas,
        val id: String
    ) {
        var x = 0
            internal set
        var y = 0
            internal set
        var width = 0
            internal set
        var height = 0
            internal set

        @Volatile
        var isReady = false
            internal set

        val uOffset get() = x / atlas.width.toFloat()
        val vOffset get() = y / atlas.height.toFloat()
        val uScale get() = width / atlas.width.toFloat()
        val vScale get() = height / atlas.height.toFloat()
    }

    private class PendingSource(
        val region: Region,
        val loader: () -> BufferedImage
    )

    private class LoadedSource(
        val region: Region,
        val image: BufferedImage
    )

    private class Placement(
        val allocation: AtlasAllocation,
        val resized: Boolean
    )

    private val allocator: AtlasAllocator = ShelfAtlasAllocator(config.padding)
    private val regions = LinkedHashMap<String, Region>()
    private val pending = ArrayList<PendingSource>()

    private var backingImage: BufferedImage? = null
    private var texture: Texture? = null

    @Volatile
    private var hasPending = false

    @Volatile
    override var isPrepared = false
        private set

    val width get() = backingImage?.width ?: config.initialSize.width
    val height get() = backingImage?.height ?: config.initialSize.height
    val size get() = TextureAtlasSize(width, height)

    @Synchronized
    fun register(id: String, loader: () -> BufferedImage): Region {
        require(id.isNotBlank()) { "Texture atlas source id must not be blank" }
        check(id !in regions) { "Texture atlas source is already registered: $id" }
        return Region(this, id).also { region ->
            regions[id] = region
            pending += PendingSource(region, loader)
            hasPending = true
        }
    }

    fun registerResource(id: String, path: String): Region = register(id) {
        javaClass.classLoader.getResourceAsStream(path)?.use { input ->
            ImageIO.read(input)?.let(::ensureArgb) ?: error("Unable to decode texture: $path")
        } ?: error("Missing texture resource: $path")
    }

    fun put(id: String, image: BufferedImage): Region = register(id) { image }

    @Synchronized
    override fun prepare() {
        if (isPrepared) return
        ensureWhiteSource()
        val work = loadPending(sortByHeight = backingImage == null)
        apply(work, recreateTexture = true)
        isPrepared = true
    }

    fun processPending() {
        if (!isPrepared || !hasPending) return
        synchronized(this) {
            if (!isPrepared || !hasPending) return
            apply(loadPending(sortByHeight = false), recreateTexture = false)
        }
    }

    fun texture(): Texture = texture ?: error("Texture atlas is not prepared")

    @Synchronized
    fun region(id: String): Region = regions[id] ?: error("Missing texture atlas region: $id")

    fun whiteRegion(): Region = region(WHITE_ID)

    @Synchronized
    override fun close() {
        texture?.close()
        texture = null
        isPrepared = false
        regions.values.forEach { it.isReady = false }
    }

    private fun loadPending(sortByHeight: Boolean): List<LoadedSource> {
        val loaded = pending.map { source ->
            LoadedSource(source.region, ensureArgb(source.loader()))
        }
        pending.clear()
        hasPending = false
        return if (sortByHeight) loaded.sortedByDescending { it.image.height } else loaded
    }

    private fun apply(sources: List<LoadedSource>, recreateTexture: Boolean) {
        var resized = ensureBackingImage(sources)
        for (source in sources) {
            val allocation = allocate(source.image)
            resized = resized || allocation.resized
            val position = allocation.allocation
            copy(source.image, position.x, position.y)
            source.region.x = position.x
            source.region.y = position.y
            source.region.width = source.image.width
            source.region.height = source.image.height
        }

        if (recreateTexture || resized) {
            replaceTexture()
            regions.values.forEach { region ->
                if (region.width > 0 && region.height > 0) region.isReady = true
            }
            return
        }

        val handle = texture().handle ?: error("Texture atlas handle is null")
        for (source in sources) {
            Luma.backend.updateTexture(handle, source.region.x, source.region.y, source.image)
            source.region.isReady = true
        }
    }

    private fun allocate(image: BufferedImage): Placement {
        val required = TextureAtlasSize(
            image.width + config.padding,
            image.height + config.padding
        )
        var resized = false
        while (true) {
            allocator.allocate(image.width, image.height, size)?.let { return Placement(it, resized) }
            val next = config.growthPolicy.next(size, required, config.maximumSize)
                ?: error(
                    "Texture atlas is full: ${size.width}x${size.height}, " +
                        "maximum ${config.maximumSize.width}x${config.maximumSize.height}"
                )
            resizeBackingImage(next)
            resized = true
        }
    }

    private fun ensureBackingImage(sources: List<LoadedSource>): Boolean {
        if (backingImage != null) return false
        var target = config.initialSize
        val required = TextureAtlasSize(
            maxOf(target.width, sources.maxOfOrNull { it.image.width + config.padding } ?: 1),
            maxOf(target.height, sources.maxOfOrNull { it.image.height + config.padding } ?: 1)
        )
        val requiredArea = sources.sumOf { source ->
            (source.image.width + config.padding).toLong() *
                (source.image.height + config.padding).toLong()
        }
        while (
            target.width < required.width ||
            target.height < required.height ||
            target.width.toLong() * target.height < requiredArea
        ) {
            target = config.growthPolicy.next(target, required, config.maximumSize)
                ?: error("Texture atlas sources exceed the configured maximum size")
        }
        backingImage = BufferedImage(
            target.width,
            target.height,
            BufferedImage.TYPE_INT_ARGB
        )
        return true
    }

    private fun resizeBackingImage(next: TextureAtlasSize) {
        val current = requireNotNull(backingImage)
        val resized = BufferedImage(next.width, next.height, BufferedImage.TYPE_INT_ARGB)
        val source = (current.raster.dataBuffer as DataBufferInt).data
        val target = (resized.raster.dataBuffer as DataBufferInt).data
        repeat(current.height) { row ->
            System.arraycopy(source, row * current.width, target, row * next.width, current.width)
        }
        backingImage = resized
    }

    private fun replaceTexture() {
        val next = Texture(requireNotNull(backingImage), config.mipmap)
        next.load()
        val previous = texture
        texture = next
        previous?.close()
    }

    private fun copy(image: BufferedImage, x: Int, y: Int) {
        val source = (image.raster.dataBuffer as DataBufferInt).data
        val targetImage = requireNotNull(backingImage)
        val target = (targetImage.raster.dataBuffer as DataBufferInt).data
        repeat(image.height) { row ->
            System.arraycopy(source, row * image.width, target, (y + row) * targetImage.width + x, image.width)
        }
    }

    private fun ensureWhiteSource() {
        if (WHITE_ID in regions) return
        register(WHITE_ID) {
            BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).apply { setRGB(0, 0, -0x1) }
        }
    }

    private fun ensureArgb(image: BufferedImage): BufferedImage {
        if (image.type == BufferedImage.TYPE_INT_ARGB && image.raster.dataBuffer is DataBufferInt) return image
        return BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB).also { converted ->
            val graphics = converted.createGraphics()
            try {
                graphics.drawImage(image, 0, 0, null)
            } finally {
                graphics.dispose()
            }
        }
    }

    companion object {
        private const val WHITE_ID = "luma:white"
    }
}
