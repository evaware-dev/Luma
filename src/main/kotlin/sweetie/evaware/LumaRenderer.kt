package sweetie.evaware

import net.fabricmc.api.ClientModInitializer
import org.slf4j.LoggerFactory
import sweetie.evaware.luma.platform.minecraft.MinecraftRenderHooks
import sweetie.evaware.luma.wrapper.LumaMetadata
import sweetie.evaware.luma.wrapper.shader.LumaGlslLibrary
import sweetie.evaware.renderutil.RenderUtilDemo
import sweetie.evaware.renderutil.font.RenderFonts

object LumaRenderer : ClientModInitializer {
    val logger = LoggerFactory.getLogger(LumaMetadata.loggerName)

    override fun onInitializeClient() {
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

        MinecraftRenderHooks.register(RenderUtilDemo)
        logger.info(LumaMetadata.message("initialized"))
    }
}
