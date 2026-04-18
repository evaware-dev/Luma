package sweetie.evaware.renderutil

import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.CloseableResourceBase
import sweetie.evaware.luma.resource.LumaResources
import sweetie.evaware.luma.scissor.ScissorControl
import sweetie.evaware.luma.texture.TextureAtlas
import sweetie.evaware.luma.texture.TextureUploader
import sweetie.evaware.renderutil.api.IBatch
import sweetie.evaware.renderutil.api.RenderApi
import sweetie.evaware.renderutil.api.RenderPipeline
import sweetie.evaware.renderutil.renderers.*
import java.awt.image.BufferedImage

object RenderUtil : CloseableResourceBase(), RenderApi {
    private var uberRenderer = UberRenderer()
    private var roundedRectRenderer = RoundedRectRenderer()
    private var rectRenderer = RectRenderer(uberRenderer)
    private var textureRenderer = TextureRenderer(uberRenderer)

    private var activeBatch: IBatch? = null
    private var activePipeline: RenderPipeline? = null
    private var loaded = false
    private var frameActive = false

    val RECT get() = rectRenderer.reset()
    val TEXTURE get() = textureRenderer.reset()
    val ROUNDED_RECT get() = roundedRectRenderer.reset()

    fun load() {
        if (isClosed) {
            rebuildRenderers()
            reopenResource()
        }
        if (loaded) return

        TextureAtlas.prepare()
        uberRenderer.load()
        roundedRectRenderer.load()
        loaded = true
    }

    override fun close() {
        if (!markClosed()) return

        if (frameActive && Luma.hasContext()) {
            endFrame()
        }

        frameActive = false

        uberRenderer.close()
        roundedRectRenderer.close()
        TextureAtlas.close()
        TextureUploader.close()
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

    override fun rect(x: Float, y: Float, width: Float, height: Float, color: Int, pipeline: RenderPipeline) {
        RECT
            .priority(pipeline)
            .color(color)
            .draw(x, y, width, height)
    }

    override fun texture(
        id: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Int,
        pipeline: RenderPipeline
    ) {
        TEXTURE
            .priority(pipeline)
            .color(color)
            .draw(id, x, y, width, height)
    }

    override fun roundedRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        color: Int,
        pipeline: RenderPipeline
    ) {
        ROUNDED_RECT
            .priority(pipeline)
            .color(color)
            .radius(radius)
            .draw(x, y, width, height)
    }

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

    override fun scissor(x: Float, y: Float, width: Float, height: Float, action: () -> Unit) {
        startScissor(x, y, width, height)
        try {
            action()
        } finally {
            endScissor()
        }
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

    private fun rebuildRenderers() {
        uberRenderer = UberRenderer()
        roundedRectRenderer = RoundedRectRenderer()
        rectRenderer = RectRenderer(uberRenderer)
        textureRenderer = TextureRenderer(uberRenderer)
    }
}
