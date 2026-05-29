package sweetie.evaware.renderutil.helper

object ColorUtil {
    const val WHITE = -0x1
    const val BLACK = -0x1000000

    private const val inv255 = 1f / 255f

    fun rgba(red: Int, green: Int, blue: Int, alpha: Int = 255) =
        ((alpha and 255) shl 24) or ((red and 255) shl 16) or ((green and 255) shl 8) or (blue and 255)

    fun red(color: Int) = (color ushr 16) and 255
    fun green(color: Int) = (color ushr 8) and 255
    fun blue(color: Int) = color and 255
    fun alpha(color: Int) = (color ushr 24) and 255

    fun redf(color: Int) = red(color) * inv255
    fun greenf(color: Int) = green(color) * inv255
    fun bluef(color: Int) = blue(color) * inv255
    fun alphaf(color: Int) = alpha(color) * inv255
}
