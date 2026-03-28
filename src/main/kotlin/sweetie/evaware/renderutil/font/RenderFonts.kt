package sweetie.evaware.renderutil.font

import com.google.gson.Gson
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import sweetie.evaware.luma.texture.TextureAtlas
import sweetie.evaware.msdf.MsdfFont
import sweetie.evaware.msdf.MsdfGlyph

object RenderFonts {
    private val gson = Gson()
    private val sources = LinkedHashMap<String, FontAsset>()
    private val dataByName = HashMap<String, FontData>()
    private val fonts = LinkedHashMap<String, MsdfFont>()
    private var staged = false
    private var loaded = false

    fun register(name: String, dataPath: String, imagePath: String = dataPath.removeSuffix(".json") + ".png") {
        require(!staged && !loaded) { "Fonts are already staged" }
        sources[name] = FontAsset(name, dataPath, imagePath)
    }

    fun stage() {
        if (staged) return

        for ((name, asset) in sources) {
            dataByName[name] = loadFontData(asset.dataPath)
            TextureAtlas.registerResource("font:$name", asset.imagePath)
        }

        staged = true
    }

    fun load() {
        if (loaded) return
        stage()

        for ((name, _) in sources) {
            val data = dataByName[name] ?: continue
            val region = TextureAtlas.region("font:$name")
            val atlasWidth = data.atlas.width
            val atlasHeight = data.atlas.height
            val glyphs = HashMap<Int, MsdfGlyph>(data.glyphs.size)
            val bottomOrigin = data.atlas.yOrigin == "bottom"

            for (glyphData in data.glyphs) {
                val atlasBounds = glyphData.atlasBounds
                val planeBounds = glyphData.planeBounds
                val localMinU = if (atlasBounds != null) atlasBounds.left / atlasWidth else 0f
                val localMaxU = if (atlasBounds != null) atlasBounds.right / atlasWidth else 0f
                val localMinV = if (atlasBounds == null) {
                    0f
                } else if (bottomOrigin) {
                    1f - atlasBounds.top / atlasHeight
                } else {
                    atlasBounds.top / atlasHeight
                }
                val localMaxV = if (atlasBounds == null) {
                    0f
                } else if (bottomOrigin) {
                    1f - atlasBounds.bottom / atlasHeight
                } else {
                    atlasBounds.bottom / atlasHeight
                }

                glyphs[glyphData.unicode] = MsdfGlyph(
                    code = glyphData.unicode,
                    advance = glyphData.advance,
                    minU = region.uOffset + localMinU * region.uScale,
                    minV = region.vOffset + localMinV * region.vScale,
                    maxU = region.uOffset + localMaxU * region.uScale,
                    maxV = region.vOffset + localMaxV * region.vScale,
                    planeLeft = planeBounds?.left ?: 0f,
                    planeRight = planeBounds?.right ?: 0f,
                    planeTop = planeBounds?.top ?: 0f,
                    planeBottom = planeBounds?.bottom ?: 0f
                )
            }

            fonts[name] = MsdfFont(
                name = name,
                lineHeight = data.metrics.lineHeight,
                ascender = data.metrics.ascender,
                descender = data.metrics.descender,
                range = data.atlas.range,
                glyphs = glyphs
            )
        }

        dataByName.clear()
        loaded = true
    }

    fun close() {
        dataByName.clear()
        fonts.clear()
        staged = false
        loaded = false
    }

    fun clearSources() {
        close()
        sources.clear()
    }

    fun get(name: String) = fonts[name] ?: error("Missing font: $name")

    private fun loadFontData(path: String): FontData {
        val stream = javaClass.classLoader.getResourceAsStream(path)
            ?: error("Missing font data resource: $path")

        stream.use {
            InputStreamReader(it, StandardCharsets.UTF_8).use { reader ->
                return gson.fromJson(reader, FontData::class.java)
                    ?: error("Unable to parse font data: $path")
            }
        }
    }
}
