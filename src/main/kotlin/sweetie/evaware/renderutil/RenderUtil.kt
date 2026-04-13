package sweetie.evaware.renderutil

import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.wrapper.scissor.ScissorControl
import sweetie.evaware.luma.wrapper.texture.TextureAtlas
import sweetie.evaware.luma.wrapper.texture.TextureUploader
import sweetie.evaware.msdf.MsdfFont
import sweetie.evaware.renderutil.api.RenderPipeline
import sweetie.evaware.renderutil.font.RenderFonts
import sweetie.evaware.renderutil.renderers.RectRenderer
import sweetie.evaware.renderutil.renderers.TextRenderer
import sweetie.evaware.renderutil.renderers.TextureRenderer
import sweetie.evaware.renderutil.renderers.UberRenderer
import java.awt.image.BufferedImage

object RenderUtil {
    private val uberRenderer = UberRenderer()

    private val rectRenderer = RectRenderer(uberRenderer)
    private val textureRenderer = TextureRenderer(uberRenderer)
    private val textRenderer = TextRenderer(uberRenderer)

    private var loaded = false
    private var frameActive = false
    private var closed = false

    val RECT get() = rectRenderer.reset()
    val TEXTURE get() = textureRenderer.reset()

    fun load() {
        closed = false
        if (loaded) return

        RenderFonts.stage()
        TextureAtlas.prepare()
        RenderFonts.load()
        uberRenderer.load()
        loaded = true
    }

    fun close() {
        if (closed) return
        closed = true

        if (frameActive && Luma.hasContext()) {
            endFrame()
        }

        frameActive = false

        uberRenderer.close()
        TextureAtlas.close()
        RenderFonts.close()
        TextureUploader.close()
        Luma.close()

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

    fun renderCurrentFrame(action: () -> Unit) {
        beginCurrentFrame()
        try {
            action()
        } finally {
            endFrame()
        }
    }

    fun beginFrame() {
        beginFrame(bindMainFramebuffer = true)
    }

    fun beginCurrentFrame() {
        beginFrame(bindMainFramebuffer = false)
    }

    private fun beginFrame(bindMainFramebuffer: Boolean) {
        load()
        RenderStats.beginFrame()
        if (bindMainFramebuffer) {
            Luma.beginMainFramebufferFrame()
        } else {
            Luma.beginCurrentFramebufferFrame()
        }
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
        flushPipeline(pipeline)
    }

    fun flushAll() {
        for (pipeline in RenderPipeline.entries) {
            flushPipeline(pipeline)
        }
    }

    internal fun useUberBatch(pipeline: RenderPipeline) {
        load()
    }

    private fun flushPipeline(pipeline: RenderPipeline) {
        if (!uberRenderer.hasPending(pipeline)) return

        if (frameActive || Luma.isFrameActive()) {
            uberRenderer.flush(pipeline)
            return
        }

        Luma.renderToMainFramebuffer {
            uberRenderer.flush(pipeline)
        }
    }
}
