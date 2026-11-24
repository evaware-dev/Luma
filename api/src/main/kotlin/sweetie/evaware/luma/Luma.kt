package sweetie.evaware.luma

import sweetie.evaware.luma.api.BlendFunction
import sweetie.evaware.luma.api.DepthCompare
import sweetie.evaware.luma.api.RenderBackend
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.SamplerState
import sweetie.evaware.luma.api.StencilCompare
import sweetie.evaware.luma.api.StencilOp
import sweetie.evaware.luma.api.TextureFilter
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.list.DrawList
import sweetie.evaware.luma.matrix.MatrixControl
import sweetie.evaware.luma.shader.translator.RawShaderTranslator
import sweetie.evaware.luma.shader.translator.ShaderTranslator
import sweetie.evaware.luma.texture.TextureAtlasManager

object Luma {
    @JvmField
    var frameActive = false

    lateinit var backend: RenderBackend
    val isBackendInitialized: Boolean
        get() = ::backend.isInitialized

    var shaderTranslator: ShaderTranslator = RawShaderTranslator

    @JvmField
    var platform: RenderPlatform = DefaultRenderPlatform

    fun hasContext(): Boolean = backend.hasContext()

    fun beginFrame() {
        if (frameActive) return
        backend.beginFrame()
        frameActive = true
    }

    fun beginMainFramebufferFrame() {
        if (frameActive) return
        backend.beginFrame()
        frameActive = true
        try {
            TextureAtlasManager.processPending()
            MatrixControl.beginGuiFrame()
        } catch (failure: Throwable) {
            endFrameHandlingFailure(failure)
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

    inline fun render(action: () -> Unit) = executeFrame(processPendingAtlases = true, action = action)

    inline fun frame(action: () -> Unit) = executeFrame(processPendingAtlases = false, action = action)

    @PublishedApi
    internal inline fun executeFrame(processPendingAtlases: Boolean, action: () -> Unit) {
        if (frameActive) {
            action()
            return
        }
        backend.beginFrame()
        frameActive = true
        var actionFailure: Throwable? = null
        try {
            if (processPendingAtlases) TextureAtlasManager.processPending()
            action()
        } catch (failure: Throwable) {
            actionFailure = failure
            throw failure
        } finally {
            endFrameHandlingFailure(actionFailure)
        }
    }

    @PublishedApi
    internal fun endFrameHandlingFailure(actionFailure: Throwable?) {
        try {
            backend.endFrame()
        } catch (endFailure: Throwable) {
            if (actionFailure == null) throw endFailure
            actionFailure.addSuppressed(endFailure)
        } finally {
            frameActive = false
        }
    }

    internal fun replaceBackendIfSame(expected: RenderBackend, replacement: RenderBackend) {
        if (::backend.isInitialized && backend === expected) {
            backend = replacement
        }
    }

    inline fun renderToMainFramebuffer(action: () -> Unit) = render(action)

    fun bindTexture(texture: TextureHandle, unit: Int = 0) = backend.bindTexture(texture, unit)
    fun setTextureSampler(texture: TextureHandle, sampler: SamplerState) = backend.setTextureSampler(texture, sampler)
    fun generateMipmaps(texture: TextureHandle) = backend.generateMipmaps(texture)

    fun enableBlend() = backend.blend(true)
    fun disableBlend() = backend.blend(false)
    fun blendFunction(function: BlendFunction) = backend.blendFunction(function)
    fun colorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) =
        backend.colorMask(red, green, blue, alpha)

    fun enableDepthTest() = backend.depthTest(true)
    fun disableDepthTest() = backend.depthTest(false)
    fun depthWrite(enabled: Boolean) = backend.depthWrite(enabled)
    fun depthCompare(compare: DepthCompare) = backend.depthCompare(compare)

    fun enableStencilTest() = backend.stencilTest(true)
    fun disableStencilTest() = backend.stencilTest(false)
    fun stencilFunc(compare: StencilCompare, ref: Int, readMask: Int = 0xFF) =
        backend.stencilFunc(compare, ref, readMask)
    fun stencilOp(stencilFail: StencilOp, depthFail: StencilOp, pass: StencilOp) =
        backend.stencilOp(stencilFail, depthFail, pass)
    fun stencilMask(writeMask: Int) = backend.stencilMask(writeMask)

    fun enableCull() = backend.cull(true)
    fun disableCull() = backend.cull(false)

    fun clearColor(red: Float, green: Float, blue: Float, alpha: Float) = backend.clearColor(red, green, blue, alpha)
    fun clearDepth(depth: Double) = backend.clearDepth(depth)
    fun clearStencil(value: Int = 0) = backend.clearStencil(value)

    fun blitRenderTarget(
        source: RenderTargetHandle,
        destination: RenderTargetHandle?,
        srcX: Int = 0,
        srcY: Int = 0,
        srcWidth: Int = source.width,
        srcHeight: Int = source.height,
        dstX: Int = 0,
        dstY: Int = 0,
        dstWidth: Int = destination?.width ?: srcWidth,
        dstHeight: Int = destination?.height ?: srcHeight,
        filter: TextureFilter = TextureFilter.LINEAR,
        copyColor: Boolean = true,
        copyDepth: Boolean = false
    ) = backend.blitRenderTarget(
        source, destination, srcX, srcY, srcWidth, srcHeight, dstX, dstY, dstWidth, dstHeight, filter, copyColor, copyDepth
    )

    fun pushDebugGroup(label: String) = backend.pushDebugGroup(label)
    fun popDebugGroup() = backend.popDebugGroup()

    inline fun <T> debugGroup(label: String, action: () -> T): T {
        backend.pushDebugGroup(label)
        return try {
            action()
        } finally {
            backend.popDebugGroup()
        }
    }

    fun invalidatePipelineCache() {
        if (::backend.isInitialized) backend.invalidatePipelineCache()
    }

    inline fun record(list: DrawList, action: () -> Unit) = list.record(backend, action)

    fun replay(list: DrawList) = list.replay(backend)
}
