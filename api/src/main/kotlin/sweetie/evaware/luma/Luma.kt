package sweetie.evaware.luma

import sweetie.evaware.luma.api.RenderBackend
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.matrix.MatrixControl
import sweetie.evaware.luma.shader.translator.ShaderTranslator

object Luma {
    @JvmField
    var mockRenderTargetWidth: Int? = null

    @JvmField
    var mockRenderTargetHeight: Int? = null

    @JvmField
    var frameActive = false

    lateinit var backend: RenderBackend
    lateinit var shaderTranslator: ShaderTranslator

    @JvmField
    var platform: RenderPlatform = DefaultRenderPlatform

    fun hasContext(): Boolean = backend.hasContext()

    fun beginMainFramebufferFrame() {
        if (frameActive) return
        backend.beginFrame()
        MatrixControl.beginGuiFrame()
        frameActive = true
    }

    fun endFrame() {
        if (!frameActive) return
        backend.endFrame()
        frameActive = false
    }

    inline fun render(action: () -> Unit) {
        if (frameActive) {
            action()
            return
        }
        backend.beginFrame()
        try {
            action()
        } finally {
            backend.endFrame()
        }
    }

    inline fun renderToMainFramebuffer(action: () -> Unit) = render(action)

    fun bindTexture(texture: TextureHandle, unit: Int = 0) {
        backend.bindTexture(texture, unit)
    }
}