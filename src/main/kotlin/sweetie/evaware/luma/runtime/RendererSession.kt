package sweetie.evaware.luma.runtime

import sweetie.evaware.luma.wrapper.LumaBackend
import sweetie.evaware.luma.wrapper.LumaBackendRouter
import sweetie.evaware.luma.wrapper.LumaVertexBuffer
import sweetie.evaware.luma.wrapper.backend.LumaPipeline
import sweetie.evaware.luma.wrapper.backend.RenderHost
import sweetie.evaware.luma.wrapper.frame.FrameState
import sweetie.evaware.luma.wrapper.matrix.MatrixControl
import sweetie.evaware.luma.wrapper.texture.TextureHandle

internal class RendererSession(
    private val backendRouter: LumaBackendRouter,
    private val renderHostProvider: () -> RenderHost,
    private val stateGuard: FrameStateGuard
) {
    private var frameActive = false
    private var activeBackend: LumaBackend? = null

    fun isFrameActive() = frameActive

    fun beginMainFramebufferFrame() {
        beginFrame(bindMainFramebuffer = true)
    }

    fun beginCurrentFramebufferFrame() {
        beginFrame(bindMainFramebuffer = false)
    }

    private fun beginFrame(bindMainFramebuffer: Boolean) {
        if (frameActive) return
        val host = renderHostProvider()
        val backend = backendRouter.resolve()
        val frameInfo = host.frameInfo()
        FrameState.update(frameInfo)
        if (backend.requiresManagedOpenGlFrame) {
            val bindFramebuffer = if (bindMainFramebuffer) {
                { host.beginFrame(backend.type) }
            } else {
                null
            }
            stateGuard.beginManagedFrame(bindFramebuffer)
        } else {
            host.beginFrame(backend.type)
        }
        backend.beginFrame(frameInfo, host)
        activeBackend = backend
        MatrixControl.beginGuiFrame()
        frameActive = true
    }

    fun endFrame() {
        if (!frameActive) return
        val backend = activeBackend
        backend?.endFrame()
        if (backend?.requiresManagedOpenGlFrame == true) {
            stateGuard.endManagedFrame()
        }
        if (backend != null) {
            renderHostProvider().endFrame(backend.type)
        }
        activeBackend = null
        frameActive = false
    }

    fun render(action: () -> Unit) {
        if (frameActive) {
            action()
            return
        }

        stateGuard.renderOutsideFrame {
            action()
        }
    }

    fun renderToMainFramebuffer(action: () -> Unit) {
        if (frameActive) {
            action()
            return
        }

        try {
            beginMainFramebufferFrame()
            action()
        } finally {
            endFrame()
        }
    }

    fun draw(pipeline: LumaPipeline, vertices: LumaVertexBuffer, texture: TextureHandle?) {
        val backend = activeBackend ?: backendRouter.resolve()
        backend.draw(pipeline, vertices, texture)
    }

    fun closeBackends() {
        activeBackend = null
        backendRouter.close()
    }
}
