package sweetie.evaware.luma

import sweetie.evaware.luma.api.RenderBackend
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.matrix.MatrixControl
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
    lateinit var shaderTranslator: ShaderTranslator

    @JvmField
    var platform: RenderPlatform = DefaultRenderPlatform

    fun hasContext(): Boolean = backend.hasContext()

    fun beginMainFramebufferFrame() {
        if (frameActive) return
        TextureAtlasManager.processPending()
        backend.beginFrame()
        MatrixControl.beginGuiFrame()
        frameActive = true
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
        TextureAtlasManager.processPending()
        backend.beginFrame()
        frameActive = true
        try {
            action()
        } finally {
            try {
                backend.endFrame()
            } finally {
                frameActive = false
            }
        }
    }

    inline fun renderToMainFramebuffer(action: () -> Unit) = render(action)

    fun bindTexture(texture: TextureHandle, unit: Int = 0) {
        backend.bindTexture(texture, unit)
    }

    fun enableBlend() = platform.enableBlend()
    fun disableBlend() = platform.disableBlend()
    fun enableDepthTest() {
        platform.enableDepthTest()
        backend.depthTest(true)
    }

    fun disableDepthTest() {
        platform.disableDepthTest()
        backend.depthTest(false)
    }

    fun enableCull() {
        platform.enableCull()
        backend.cull(true)
    }

    fun disableCull() {
        platform.disableCull()
        backend.cull(false)
    }
}
