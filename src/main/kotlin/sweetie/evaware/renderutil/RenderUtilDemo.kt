package sweetie.evaware.renderutil

import sweetie.evaware.luma.platform.minecraft.MinecraftRenderListener
import sweetie.evaware.luma.wrapper.LumaMetadata
import java.awt.image.BufferedImage

object RenderUtilDemo : MinecraftRenderListener {
    private var initialized = false

    override fun initialize() {
        if (initialized) return
        initialized = true

        RenderUtil.registerTexture("demo_icon", LumaMetadata.asset("icon.png"))
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
    }

    override fun renderGui(partialTick: Float) {
        RenderUtil.renderCurrentFrame {
            RenderTest.renderGui()
        }
    }

    override fun close() {
        initialized = false
        RenderUtil.close()
    }
}
