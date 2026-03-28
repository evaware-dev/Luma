package sweetie.evaware.renderutil

import java.awt.image.BufferedImage
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.resource.LumaResources
import sweetie.evaware.luma.scissor.ScissorControl
import sweetie.evaware.luma.texture.TextureAtlas
import sweetie.evaware.msdf.MsdfFont
import sweetie.evaware.renderutil.api.IBatch
import sweetie.evaware.renderutil.api.RenderPipeline
import sweetie.evaware.renderutil.font.RenderFonts
import sweetie.evaware.renderutil.renderers.RectRenderer
import sweetie.evaware.renderutil.renderers.RoundedRectRenderer
import sweetie.evaware.renderutil.renderers.TextRenderer
import sweetie.evaware.renderutil.renderers.TextureRenderer
import sweetie.evaware.renderutil.renderers.UberRenderer

object RenderUtil {
    private val uberRenderer = UberRenderer()
    private val roundedRectRenderer = RoundedRectRenderer()

    private val rectRenderer = RectRenderer(uberRenderer)
    private val textureRenderer = TextureRenderer(uberRenderer)
    private val textRenderer = TextRenderer(uberRenderer)

    private var activeBatch: IBatch? = null
    private var activePipeline: RenderPipeline? = null
    private var loaded = false
    private var frameActive = false

    val RECT get() = rectRenderer.reset()
    val TEXTURE get() = textureRenderer.reset()
    val ROUNDED_RECT get() = roundedRectRenderer.reset()

    fun load() {
        if (loaded) return

        RenderFonts.stage()
        TextureAtlas.prepare()
        RenderFonts.load()
        uberRenderer.load()
        roundedRectRenderer.load()
        loaded = true
    }

    fun close() {
        if (frameActive) {
            endFrame()
        }

        uberRenderer.close()
        roundedRectRenderer.close()
        TextureAtlas.close()
        RenderFonts.close()
        LumaResources.closeAll()

        activeBatch = null
        activePipeline = null
        loaded = false
    }

    fun registerTexture(id: String, path: String) {
        TextureAtlas.registerResource(id, path)
    }

    fun registerTexture(id: String, loader: () -> BufferedImage) {
        TextureAtlas.register(id, loader)
    }

    fun text(font: MsdfFont, pipeline: RenderPipeline = RenderPipeline.GUI) = textRenderer.reset(font, pipeline)

    fun renderFrame(action: () -> Unit) {
        beginFrame()
        try {
            action()
        } finally {
            endFrame()
        }
    }

    fun beginFrame() {
        load()
        RenderStats.beginFrame()
        Luma.beginMainFramebufferFrame()
        frameActive = true
    }

    fun endFrame() {
        flushAll()
        if (!frameActive) return
        Luma.endFrame()
        frameActive = false
    }

    fun startScissor(x: Float, y: Float, width: Float, height: Float) {
        ScissorControl.push(x, y, width, height)
    }

    fun endScissor() {
        ScissorControl.pop()
    }

    fun flush(pipeline: RenderPipeline) {
        val currentPipeline = activePipeline
        if (currentPipeline != null && currentPipeline != pipeline) {
            flushActiveBatch()
        }
        flushPipeline(pipeline)
        if (activePipeline == pipeline) {
            activeBatch = null
            activePipeline = null
        }
    }

    fun flushAll() {
        flushActiveBatch()
        for (pipeline in RenderPipeline.entries) {
            flushPipeline(pipeline)
        }
    }

    internal fun useUberBatch(pipeline: RenderPipeline) {
        switchTo(uberRenderer, pipeline)
    }

    internal fun useRoundedBatch(pipeline: RenderPipeline) {
        switchTo(roundedRectRenderer, pipeline)
    }

    private fun switchTo(batch: IBatch, pipeline: RenderPipeline) {
        load()
        if (activeBatch === batch && activePipeline == pipeline) return
        flushActiveBatch()
        activeBatch = batch
        activePipeline = pipeline
    }

    private fun flushActiveBatch() {
        val pipeline = activePipeline ?: return
        flushPipeline(pipeline)
        activeBatch = null
        activePipeline = null
    }

    private fun flushPipeline(pipeline: RenderPipeline) {
        if (!uberRenderer.hasPending(pipeline) && !roundedRectRenderer.hasPending(pipeline)) return

        if (frameActive || Luma.isFrameActive()) {
            uberRenderer.flush(pipeline)
            roundedRectRenderer.flush(pipeline)
            return
        }

        Luma.renderToMainFramebuffer {
            uberRenderer.flush(pipeline)
            roundedRectRenderer.flush(pipeline)
        }
    }
}
