package sweetie.evaware.luma.runtime

import sweetie.evaware.luma.wrapper.LumaBackendRouter
import sweetie.evaware.luma.wrapper.RenderApiType
import sweetie.evaware.luma.wrapper.backend.RenderHost
import sweetie.evaware.luma.wrapper.frame.FrameInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RendererSessionTest {
    @Test
    fun `open gl main framebuffer frame binds host framebuffer`() {
        val host = TestRenderHost()
        val stateGuard = TestFrameStateGuard()
        val session = RendererSession(
            backendRouter = routerFor(host, RenderApiType.OPEN_GL),
            renderHostProvider = { host },
            stateGuard = stateGuard
        )

        session.beginMainFramebufferFrame()

        assertEquals(1, stateGuard.beginManagedFrameCalls)
        assertEquals(1, stateGuard.boundFramebufferCalls)
        assertEquals(1, host.beginFrameCalls)
        assertTrue(session.isFrameActive())
    }

    @Test
    fun `open gl current framebuffer frame preserves current target`() {
        val host = TestRenderHost()
        val stateGuard = TestFrameStateGuard()
        val session = RendererSession(
            backendRouter = routerFor(host, RenderApiType.OPEN_GL),
            renderHostProvider = { host },
            stateGuard = stateGuard
        )

        session.beginCurrentFramebufferFrame()

        assertEquals(1, stateGuard.beginManagedFrameCalls)
        assertEquals(0, stateGuard.boundFramebufferCalls)
        assertEquals(0, host.beginFrameCalls)
        assertTrue(session.isFrameActive())
    }

    private fun routerFor(host: RenderHost, renderType: RenderApiType) = LumaBackendRouter(
        renderTypeResolver = { renderType },
        renderHostProvider = { host }
    )
}

private class TestFrameStateGuard : FrameStateGuard {
    var beginManagedFrameCalls = 0
    var boundFramebufferCalls = 0

    override fun beginManagedFrame(bindFramebuffer: (() -> Unit)?) {
        beginManagedFrameCalls++
        if (bindFramebuffer != null) {
            boundFramebufferCalls++
            bindFramebuffer()
        }
    }

    override fun endManagedFrame() = Unit

    override fun renderOutsideFrame(action: () -> Unit) = action()

    override fun invalidateBindings() = Unit
}

private class TestRenderHost : RenderHost {
    var beginFrameCalls = 0

    override fun frameInfo() = FrameInfo(320f, 180f, 640f, 360f, 2f)

    override fun beginFrame(renderType: RenderApiType) {
        beginFrameCalls++
    }
}
