package sweetie.evaware

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory
import sweetie.evaware.luma.LumaAssets
import sweetie.evaware.luma.minecraft.LumaMinecraft
import sweetie.evaware.luma.shader.GlslLibrary

object LumaRenderer : ModInitializer {
    val logger = LoggerFactory.getLogger("luma")

    override fun onInitialize() {
        LumaMinecraft.install()

        GlslLibrary
            .register("scissor", LumaAssets.SCISSOR_INCLUDE)
            .register("matrix", LumaAssets.MATRIX_INCLUDE)
            .register("rect", LumaAssets.RECT_INCLUDE)
            .attach()

        logger.info("Luma initialized")
    }
}
