package sweetie.evaware.luma.backend.gl

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextureBindingCacheTest {
    @Test
    fun growsWithoutTreatingNewSlotsAsTextureZero() {
        val cache = TextureBindingCache(1)
        val texture = GlTexture(0, 1, 1)

        assertFalse(cache.isBound(8, texture))
        cache.bind(8, texture)

        assertTrue(cache.isBound(8, texture))
    }

    @Test
    fun distinguishesObjectsWithReusedGlIds() {
        val cache = TextureBindingCache(2)
        val first = GlTexture(42, 1, 1)
        val reused = GlTexture(42, 1, 1)
        cache.bind(0, first)

        assertTrue(cache.isBound(0, first))
        assertFalse(cache.isBound(0, reused))
    }

    @Test
    fun invalidatesAllBindings() {
        val cache = TextureBindingCache(2)
        val first = GlTexture(1, 1, 1)
        val second = GlTexture(2, 1, 1)
        cache.bind(0, first)
        cache.bind(1, second)

        cache.invalidateAll()

        assertFalse(cache.isBound(0, first))
        assertFalse(cache.isBound(1, second))
    }
}
