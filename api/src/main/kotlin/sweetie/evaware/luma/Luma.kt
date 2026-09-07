package sweetie.evaware.luma

import sweetie.evaware.luma.api.BlendFunction
import sweetie.evaware.luma.api.DepthCompare
import sweetie.evaware.luma.api.RenderBackend
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.matrix.MatrixControl
import sweetie.evaware.luma.shader.translator.RawShaderTranslator
import sweetie.evaware.luma.shader.translator.ShaderTranslator
import sweetie.evaware.luma.texture.TextureAtlasManager

object Luma {
    @JvmField
    var mockRenderTargetWidth: Int? = null

    @JvmField
    var mockRenderTargetHeight: Int? = null

    @JvmField
    var frameActive = false

    lateinit var backend: RenderBackend
    var shaderTranslator: ShaderTranslator = RawShaderTranslator

    @JvmField
    var platform: RenderPlatform = DefaultRenderPlatform

    fun hasContext(): Boolean = backend.hasContext()

    fun beginMainFramebufferFrame() {
        if (frameActive) return
        backend.beginFrame()
        frameActive = true
        try {
            TextureAtlasManager.processPending()
            MatrixControl.beginGuiFrame()
        } catch (failure: Throwable) {
            try {
                backend.endFrame()
            } catch (endFailure: Throwable) {
                failure.addSuppressed(endFailure)
            } finally {
                frameActive = false
            }
            throw failure
        }
    }

    fun endFrame() {
        if (!frameActive) return
        try {
            backend.endFrame()
        } finally {
            frameActive = false
        }
    }

    inline fun render(action: () -> Unit) {
        if (frameActive) {
            action()
            return
        }
        backend.beginFrame()
        frameActive = true
        var actionFailure: Throwable? = null
        try {
            TextureAtlasManager.processPending()
            action()
        } catch (failure: Throwable) {
            actionFailure = failure
            throw failure
        } finally {
            try {
                backend.endFrame()
            } catch (endFailure: Throwable) {
                if (actionFailure == null) throw endFailure
                actionFailure.addSuppressed(endFailure)
            } finally {
                frameActive = false
            }
        }
    }

    inline fun renderToMainFramebuffer(action: () -> Unit) = render(action)

    fun bindTexture(texture: TextureHandle, unit: Int = 0) {
        backend.bindTexture(texture, unit)
    }

    fun enableBlend() = backend.blend(true)
    fun disableBlend() = backend.blend(false)
    fun blendFunction(function: BlendFunction) = backend.blendFunction(function)
    fun enableDepthTest() {
        backend.depthTest(true)
    }

    fun disableDepthTest() {
        backend.depthTest(false)
    }

    fun depthWrite(enabled: Boolean) = backend.depthWrite(enabled)

    fun depthCompare(compare: DepthCompare) = backend.depthCompare(compare)

    fun enableCull() {
        backend.cull(true)
    }

    fun disableCull() {
        backend.cull(false)
    }

    fun invalidatePipelineCache() {
        if (::backend.isInitialized) backend.invalidatePipelineCache()
    }
}
