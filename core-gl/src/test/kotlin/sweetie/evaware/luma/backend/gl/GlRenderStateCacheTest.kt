package sweetie.evaware.luma.backend.gl

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import sweetie.evaware.luma.api.BlendFunction
import sweetie.evaware.luma.api.DepthCompare

class GlRenderStateCacheTest {
    @Test
    fun `gui reset filters redundant state and invalidation accepts it again`() {
        val cache = GlRenderStateCache()
        cache.resetGui()

        assertFalse(cache.blend(true))
        assertFalse(cache.blendFunction(BlendFunction.TRANSLUCENT))
        assertFalse(cache.depthTest(false))
        assertFalse(cache.depthWrite(false))
        assertFalse(cache.depthCompare(DepthCompare.ALWAYS))
        assertFalse(cache.cull(false))

        assertTrue(cache.blend(false))
        assertFalse(cache.blend(false))
        cache.invalidate()
        assertTrue(cache.blend(false))
    }
}
