package sweetie.evaware.luma.backend.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlStateSnapshotTest {
    @Test
    fun storesOnlyTouchedTextureUnitsAndGrows() {
        val snapshot = GlStateSnapshot()

        repeat(9) { index ->
            snapshot.addTextureUnit(index * 2, index + 10, index + 20)
        }

        assertEquals(9, snapshot.textureUnitCount)
        assertTrue(snapshot.hasTextureUnit(16))
        assertFalse(snapshot.hasTextureUnit(3))
        assertEquals(16, snapshot.textureUnit(8))
        assertEquals(18, snapshot.boundTexture(8))
        assertEquals(28, snapshot.samplerBinding(8))

        snapshot.clearTextureUnits()
        assertEquals(0, snapshot.textureUnitCount)
        assertFalse(snapshot.hasTextureUnit(16))
    }
}
