package sweetie.evaware.luma.uniform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UniformHandleTest {
    @Test
    fun `float uniform cache sets isDirty`() {
        val handle = Float1Uniform()
        assertTrue(handle.isDirty)

        handle.isDirty = false
        handle.value = 1f
        assertTrue(handle.isDirty)

        handle.isDirty = false
        handle.value = 1f
        assertFalse(handle.isDirty)

        handle.value = 2f
        assertTrue(handle.isDirty)
    }

    @Test
    fun `int uniform cache sets isDirty`() {
        val handle = Int1Uniform()
        assertTrue(handle.isDirty)

        handle.isDirty = false
        handle.value = 7
        assertTrue(handle.isDirty)

        handle.isDirty = false
        handle.value = 7
        assertFalse(handle.isDirty)

        handle.value = 8
        assertTrue(handle.isDirty)
    }

    @Test
    fun `float2 uniform sets isDirty`() {
        val handle = Float2Uniform()
        assertTrue(handle.isDirty)

        handle.isDirty = false
        handle.first = 1f
        assertTrue(handle.isDirty)

        handle.isDirty = false
        handle.second = 2f
        assertTrue(handle.isDirty)
    }

    @Test
    fun `matrix uniform tracks projectionVersion`() {
        val handle = Mat4Uniform()
        assertEquals(-1, handle.projectionVersion)

        handle.projectionVersion = 5
        assertEquals(5, handle.projectionVersion)
    }
}
