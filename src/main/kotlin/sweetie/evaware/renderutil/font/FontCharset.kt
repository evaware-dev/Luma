package sweetie.evaware.renderutil.font

object FontCharset {
    private val mainCharset by lazy(LazyThreadSafetyMode.NONE) {
        val stream = javaClass.classLoader.getResourceAsStream("assets/luma-renderer/fonts/charset.txt")
            ?: return@lazy ""
        stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    fun main() = mainCharset

    fun full() = mainCharset
}
