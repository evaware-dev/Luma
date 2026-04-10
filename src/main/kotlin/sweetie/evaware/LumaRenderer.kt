package sweetie.evaware

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory
import sweetie.evaware.luma.wrapper.LumaMetadata
import sweetie.evaware.luma.wrapper.shader.LumaGlslLibrary
import sweetie.evaware.renderutil.font.RenderFonts

object LumaRenderer : ModInitializer {
    val logger = LoggerFactory.getLogger(LumaMetadata.loggerName)

    override fun onInitialize() {
        LumaGlslLibrary
            .register("font_renderer", LumaMetadata.includeShader("font_renderer.glsl"))
            .register("scissor", LumaMetadata.includeShader("scissor.glsl"))
            .attach()

        RenderFonts.register(
            "google_sans",
            LumaMetadata.font("google_sans/google_sans_regular.json")
        )
        RenderFonts.register(
            "google_sans_medium",
            LumaMetadata.font("google_sans/google_sans_medium.json")
        )

        logger.info(LumaMetadata.message("initialized"))
    }
}
