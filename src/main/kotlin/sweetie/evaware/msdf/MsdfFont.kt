package sweetie.evaware.msdf

class MsdfFont(
    val name: String,
    val lineHeight: Float,
    val ascender: Float,
    val descender: Float,
    val range: Float,
    private val glyphs: Map<Int, MsdfGlyph>
) {
    fun glyph(code: Int) = glyphs[code]
}
