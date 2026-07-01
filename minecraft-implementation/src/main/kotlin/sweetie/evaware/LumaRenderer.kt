package sweetie.evaware

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory
import sweetie.evaware.luma.LumaAssets
import sweetie.evaware.luma.minecraft.LumaMinecraft
import sweetie.evaware.luma.shader.GlslLibrary
import sweetie.evaware.renderutil.RenderUtil
import java.awt.image.BufferedImage

object LumaRenderer : ModInitializer {
    val logger = LoggerFactory.getLogger("luma")

    override fun onInitialize() {
        LumaMinecraft.install()

        GlslLibrary
            .register("scissor", LumaAssets.SCISSOR_INCLUDE)
            .register("matrix", LumaAssets.MATRIX_INCLUDE)
            .register("rect", LumaAssets.RECT_INCLUDE)
            .attach()

        RenderUtil.registerTexture("demo_icon", LumaAssets.DEMO_ICON)
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
