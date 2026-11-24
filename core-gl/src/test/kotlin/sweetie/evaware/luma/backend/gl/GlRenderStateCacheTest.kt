package sweetie.evaware.luma.backend.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import sweetie.evaware.luma.api.BlendFunction
import sweetie.evaware.luma.api.DepthCompare
import sweetie.evaware.luma.api.StencilCompare
import sweetie.evaware.luma.api.StencilOp

class GlRenderStateCacheTest {
    @Test
    fun `gui reset filters redundant state and invalidation accepts it again`() {
        val cache = GlRenderStateCache()
        cache.resetGui()

        assertFalse(cache.blend(true))
        assertFalse(cache.blendFunction(BlendFunction.TRANSLUCENT))
        assertFalse(cache.colorMask(true, true, true, true))
        assertFalse(cache.depthTest(false))
        assertFalse(cache.depthWrite(false))
        assertFalse(cache.depthCompare(DepthCompare.ALWAYS))
        assertFalse(cache.stencilTest(false))
        assertFalse(cache.cull(false))

        assertTrue(cache.blend(false))
        assertFalse(cache.blend(false))
        assertTrue(cache.colorMask(true, false, true, false))
        assertFalse(cache.colorMask(true, false, true, false))
        assertTrue(cache.colorMaskRed)
        assertFalse(cache.colorMaskGreen)
        assertTrue(cache.colorMaskBlue)
        assertFalse(cache.colorMaskAlpha)

        assertTrue(cache.stencilTest(true))
        assertFalse(cache.stencilTest(true))
        assertTrue(cache.stencilFunc(StencilCompare.EQUAL, 1, 0xFF))
        assertFalse(cache.stencilFunc(StencilCompare.EQUAL, 1, 0xFF))
        assertTrue(cache.stencilFunc(StencilCompare.NOT_EQUAL, 1, 0xFF))
        assertTrue(cache.stencilOp(StencilOp.KEEP, StencilOp.KEEP, StencilOp.REPLACE))
        assertFalse(cache.stencilOp(StencilOp.KEEP, StencilOp.KEEP, StencilOp.REPLACE))
        assertTrue(cache.stencilMask(0x0F))
        assertFalse(cache.stencilMask(0x0F))
        assertEquals(0x0F, cache.currentStencilWriteMask)

        cache.invalidate()
        assertTrue(cache.blend(false))
        assertTrue(cache.colorMask(true, false, true, false))
        assertTrue(cache.stencilTest(true))
        assertTrue(cache.stencilMask(0x0F))
    }
}
