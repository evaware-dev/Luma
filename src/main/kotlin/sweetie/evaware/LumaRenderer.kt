package sweetie.evaware

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory
import sweetie.evaware.luma.shader.LumaGlslLibrary
import sweetie.evaware.renderutil.RenderUtil
import sweetie.evaware.renderutil.font.RenderFonts
import java.awt.image.BufferedImage

object LumaRenderer : ModInitializer {
    val logger = LoggerFactory.getLogger("luma-renderer")

    override fun onInitialize() {
        LumaGlslLibrary
            .register("font_renderer", "assets/luma-renderer/shaders/include/font_renderer.glsl")
            .register("scissor", "assets/luma-renderer/shaders/include/scissor.glsl")
            .attach()

        RenderFonts.register(
            "google_sans",
            "assets/luma-renderer/fonts/google_sans/google_sans_regular.json"
        )
        RenderFonts.register(
            "google_sans_medium",
            "assets/luma-renderer/fonts/google_sans/google_sans_medium.json"
        )

        RenderUtil.registerTexture("demo_icon", "assets/luma-renderer/icon.png")
        RenderUtil.registerTexture("demo_checker") {
            val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            for (x in 0 until 16) {
                for (y in 0 until 16) {
                    val checker = ((x / 4) + (y / 4)) and 1
                    image.setRGB(x, y, if (checker == 0) -0x1 else -0x454546)
                }
            }
            image
        }
        logger.info("Luma initialized")
    }
}
