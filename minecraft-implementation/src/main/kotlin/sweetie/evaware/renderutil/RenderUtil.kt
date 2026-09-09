package sweetie.evaware.renderutil

import net.minecraft.client.Minecraft
import sweetie.evaware.LumaRenderer
import sweetie.evaware.luma.GraphicsBackend
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.CloseableResourceBase
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.RenderTargetFilter
import sweetie.evaware.luma.matrix.MatrixControl
import sweetie.evaware.luma.minecraft.MinecraftRenderTargets
import sweetie.evaware.luma.resource.GlResources
import sweetie.evaware.luma.scissor.ScissorControl
import sweetie.evaware.luma.texture.TextureAtlasManager
import sweetie.evaware.renderutil.api.IBatch
import sweetie.evaware.renderutil.api.RenderApi
import sweetie.evaware.renderutil.api.RenderPipeline
import sweetie.evaware.renderutil.renderers.*
import java.awt.image.BufferedImage

internal const val TEXTURE_ATLAS_ID = "luma:minecraft-render"

object RenderUtil : CloseableResourceBase(), RenderApi {
    private var roundedRectRenderer = RoundedRectRenderer(textureAtlas())
    private var textureRectRenderer = TextureRectRenderer(roundedRectRenderer)

    private var activeBatch: IBatch? = null
    private var activePipeline: RenderPipeline? = null
    private var loaded = false
    private var frameActive = false
    private var mainTarget: RenderTargetHandle? = null

    val TEXTURE get() = textureRectRenderer.reset()
    val ROUNDED_RECT get() = roundedRectRenderer.reset()

    fun load() {
        if (isClosed) {
            rebuildRenderers()
            reopenResource()
        }
        if (loaded) return

        textureAtlas().prepare()
        roundedRectRenderer.load()
        loaded = true
    }

    override fun close() {
        if (!markClosed()) return

        if (frameActive && Luma.hasContext()) {
            endFrame()
        }

        mainTarget?.let { target ->
            try {
                if (Luma.hasContext()) {
                    Luma.backend.endRenderTarget()
                }
            } catch (_: Throwable) {
            } finally {
                target.close()
                mainTarget = null
            }
        }

        frameActive = false

        roundedRectRenderer.close()
        textureAtlas().close()
        GlResources.closeAll()
        OffscreenDemo.reset()

        activeBatch = null
        activePipeline = null
        loaded = false
    }

    fun registerTexture(id: String, path: String) {
        textureAtlas().registerResource(id, path)
    }

    fun registerTexture(id: String, loader: () -> BufferedImage) {
        textureAtlas().register(id, loader)
    }

    private fun textureAtlas() = TextureAtlasManager.getOrCreate(TEXTURE_ATLAS_ID)

    override fun rect(x: Float, y: Float, width: Float, height: Float, color: Int, pipeline: RenderPipeline) {
        ROUNDED_RECT
            .priority(pipeline)
            .color(color)
            .radius(0f)
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

    fun borrowMainTarget(filter: RenderTargetFilter = RenderTargetFilter.NEAREST): RenderTargetHandle? {
        return try {
            val target = Minecraft.getInstance().gameRenderer.mainRenderTarget()
            MinecraftRenderTargets.borrow(target, filter)
        } catch (_: Throwable) {
            null
        }
    }

    fun renderToMain(clearColor: FloatArray? = null, action: () -> Unit) {
        val target = borrowMainTarget() ?: error("Minecraft main render target is not available")
        try {
            renderToTarget(target, clearColor, action)
        } finally {
            target.close()
        }
    }

    fun beginFrame() {
        load()
        Luma.beginMainFramebufferFrame()
        frameActive = true
        if (mainTarget == null && Luma.hasContext()) {
            val target = borrowMainTarget()
            if (target != null) {
                mainTarget = target
                Luma.backend.beginRenderTarget(target, null)
            }
        }
    }

    fun endFrame() {
        flushAll()
        if (!frameActive) return
        try {
            mainTarget?.let { target ->
                try {
                    if (Luma.hasContext()) {
                        Luma.backend.endRenderTarget()
                    }
                } finally {
                    target.close()
                    mainTarget = null
                }
            }
        } finally {
            Luma.endFrame()
            frameActive = false
        }
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
        val pipelines = RenderPipeline.entries
        for (i in pipelines.indices) {
            flushPipeline(pipelines[i])
        }
    }

    fun createRenderTarget(
        width: Int,
        height: Int,
        useDepth: Boolean = false,
        format: RenderTargetFormat = RenderTargetFormat.RGBA8
    ): RenderTargetHandle {
        load()
        return Luma.backend.createRenderTarget(width, height, useDepth, format)
    }

    fun createRenderTarget(
        width: Int,
        height: Int,
        useDepth: Boolean,
        format: RenderTargetFormat,
        filter: RenderTargetFilter
    ): RenderTargetHandle {
        load()
        return Luma.backend.createRenderTarget(width, height, useDepth, format, filter)
    }

    fun renderToTarget(target: RenderTargetHandle, clearColor: FloatArray? = null, action: () -> Unit) {
        load()
        flushAll()
        Luma.backend.beginRenderTarget(target, clearColor)
        try {
            action()
            flushAll()
        } finally {
            Luma.backend.endRenderTarget()
        }
    }

    internal fun useTextureBatch(pipeline: RenderPipeline) {
        switchTo(roundedRectRenderer, pipeline)
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
        if (!roundedRectRenderer.hasPending(pipeline)) return

        if (frameActive || Luma.frameActive) {
            roundedRectRenderer.flush(pipeline)
            return
        }

        renderFrame {
            roundedRectRenderer.flush(pipeline)
        }
    }

    private fun rebuildRenderers() {
        roundedRectRenderer = RoundedRectRenderer(textureAtlas())
        textureRectRenderer = TextureRectRenderer(roundedRectRenderer)
    }
}
