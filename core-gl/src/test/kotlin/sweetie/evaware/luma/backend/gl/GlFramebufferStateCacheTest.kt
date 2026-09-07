package sweetie.evaware.luma.backend.gl

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlFramebufferStateCacheTest {
    @Test
    fun storesAndInvalidatesFramebufferState() {
        val cache = GlFramebufferStateCache()
        val state = IntArray(6)

        assertFalse(cache.saveTo(state))
        cache.seed(1, 2, 3, 4, 5, 6)
        assertTrue(cache.saveTo(state))
        assertContentEquals(intArrayOf(1, 2, 3, 4, 5, 6), state)

        cache.invalidate()
        assertFalse(cache.saveTo(state))
    }

    @Test
    fun restoresSavedState() {
        val cache = GlFramebufferStateCache()
        val state = intArrayOf(7, 8, 9, 10, 11, 12)

        cache.restoreFrom(state)

        val restored = IntArray(6)
        assertTrue(cache.saveTo(restored))
        assertContentEquals(state, restored)
    }
}
