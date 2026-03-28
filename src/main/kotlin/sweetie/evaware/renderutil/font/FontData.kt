package sweetie.evaware.renderutil.font

import com.google.gson.annotations.SerializedName

class FontData {
    var atlas: AtlasData = AtlasData()
    var metrics: MetricsData = MetricsData()
    var glyphs: List<GlyphData> = emptyList()

    class AtlasData {
        var type: String = ""

        @SerializedName("distanceRange")
        var range: Float = 0f

        var width: Float = 0f
        var height: Float = 0f
        var yOrigin: String = "bottom"
    }

    class MetricsData {
        var lineHeight: Float = 0f
        var ascender: Float = 0f
        var descender: Float = 0f
    }

    class GlyphData {
        var unicode: Int = 0
        var advance: Float = 0f
        var planeBounds: BoundsData? = null
        var atlasBounds: BoundsData? = null
    }

    class BoundsData {
        var left: Float = 0f
        var top: Float = 0f
        var right: Float = 0f
        var bottom: Float = 0f
    }
}
