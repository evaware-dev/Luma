package sweetie.evaware.luma.texture

import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import javax.imageio.ImageIO

object TextureAtlas {
    private const val whiteId = "luma:white"
    private const val packPadding = 4

    data class Region(
        val uOffset: Float,
        val vOffset: Float,
        val uScale: Float,
        val vScale: Float
    )

    private class Source(val loader: () -> BufferedImage)

    private class PreparedSource(
        val id: String,
        val image: BufferedImage
    )

    private class Packed(
        val id: String,
        val image: BufferedImage,
        val x: Int,
        val y: Int
    )

    private class PackedAtlas(
        val size: Int,
        val entries: List<Packed>
    )

    private val sources = LinkedHashMap<String, Source>()
    private val regions = HashMap<String, Region>()
    private var texture: TextureHandle? = null

    fun register(id: String, loader: () -> BufferedImage) {
        if (texture != null) return
        sources[id] = Source(loader)
    }

    fun registerResource(id: String, path: String) {
        register(id) {
            javaClass.classLoader.getResourceAsStream(path)?.use { input ->
                val image = ImageIO.read(input) ?: error("Unable to decode texture: $path")
                ensureArgb(image)
            } ?: error("Missing texture resource: $path")
        }
    }

    @Synchronized
    fun prepare() {
        if (texture != null) return
        ensureWhiteSource()
        if (sources.isEmpty()) error("Texture atlas has no sources")

        val prepared = ArrayList<PreparedSource>(sources.size)
        for ((id, source) in sources) {
            prepared += PreparedSource(id, ensureArgb(source.loader()))
        }

        val packedAtlas = pack(prepared)
        val atlas = BufferedImage(packedAtlas.size, packedAtlas.size, BufferedImage.TYPE_INT_ARGB)
        val atlasPixels = (atlas.raster.dataBuffer as DataBufferInt).data
        val atlasStride = atlas.width

        for (entry in packedAtlas.entries) {
            val image = entry.image
            val sourcePixels = (image.raster.dataBuffer as DataBufferInt).data
            val width = image.width
            val height = image.height
            var sourceIndex = 0
            var row = 0
            var targetIndex = entry.y * atlasStride + entry.x

            while (row < height) {
                System.arraycopy(sourcePixels, sourceIndex, atlasPixels, targetIndex, width)
                sourceIndex += width
                targetIndex += atlasStride
                row++
            }
        }

        texture = TextureUploader.upload(atlas)
        saveRegions(packedAtlas.size, packedAtlas.entries)
    }

    fun texture() = texture ?: error("Texture atlas is not prepared")

    fun region(id: String) = regions[id] ?: error("Missing texture atlas region: $id")

    fun whiteRegion() = region(whiteId)

    fun close() {
        texture?.close()
        texture = null
        regions.clear()
    }

    private fun ensureWhiteSource() {
        if (sources.containsKey(whiteId)) return
        register(whiteId) {
            val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
            image.setRGB(0, 0, -0x1)
            image
        }
    }

    private fun ensureArgb(image: BufferedImage): BufferedImage {
        if (image.type == BufferedImage.TYPE_INT_ARGB) return image

        val width = image.width
        val height = image.height
        val converted = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val pixels = IntArray(width * height)
        image.getRGB(0, 0, width, height, pixels, 0, width)
        converted.setRGB(0, 0, width, height, pixels, 0, width)
        return converted
    }

    private fun pack(images: List<PreparedSource>): PackedAtlas {
        val sorted = ArrayList(images)
        sorted.sortByDescending { it.image.height }

        val maxDimension = sorted.fold(1) { current, entry ->
            maxOf(current, entry.image.width, entry.image.height)
        }
        val totalArea = sorted.fold(0L) { current, entry ->
            current + entry.image.width.toLong() * entry.image.height.toLong()
        }

        var size = 1
        while (size < maxDimension) size = size shl 1
        while (size.toLong() * size < totalArea + totalArea / 4L) size = size shl 1

        while (size <= 16384) {
            val packed = tryPack(sorted, size)
            if (packed != null) {
                return PackedAtlas(size, packed)
            }
            size = size shl 1
        }

        error("Unable to pack texture atlas")
    }

    private fun tryPack(images: List<PreparedSource>, size: Int): List<Packed>? {
        val packed = ArrayList<Packed>(images.size)
        var x = 0
        var y = 0
        var rowHeight = 0

        for (entry in images) {
            val image = entry.image
            val paddedWidth = image.width + packPadding
            val paddedHeight = image.height + packPadding
            if (paddedWidth > size || paddedHeight > size) return null
            if (x + paddedWidth > size) {
                x = 0
                y += rowHeight
                rowHeight = 0
            }
            if (y + paddedHeight > size) return null

            packed += Packed(entry.id, image, x, y)
            x += paddedWidth
            if (paddedHeight > rowHeight) {
                rowHeight = paddedHeight
            }
        }

        return packed
    }

    private fun saveRegions(size: Int, packed: List<Packed>) {
        regions.clear()
        val inverse = 1f / size.toFloat()

        for (entry in packed) {
            regions[entry.id] = Region(
                entry.x * inverse,
                entry.y * inverse,
                entry.image.width * inverse,
                entry.image.height * inverse
            )
        }
    }
}
