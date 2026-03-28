package sweetie.evaware.renderutil.font

import sweetie.evaware.msdf.MsdfFont

object Fonts {
    val GOOGLE_SANS: MsdfFont
        get() = RenderFonts.get("google_sans")

    val GOOGLE_SANS_MEDIUM: MsdfFont
        get() = RenderFonts.get("google_sans_medium")
}
