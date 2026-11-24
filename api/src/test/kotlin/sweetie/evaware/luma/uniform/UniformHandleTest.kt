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

    @Test
    fun `registry revision bumps only on actual mutations`() {
        val registry = UniformRegistry()
        val initialRevision = registry.revision

        val f1 = registry.registerFloat1("uFloat")
        val mat = registry.registerMat4("uMatrix")
        assertTrue(registry.revision > initialRevision)

        f1.isDirty = false
        mat.isDirty = false
        val cleanRevision = registry.revision

        // No-op writes must not bump revision
        registry.setFloat1(f1, 0f)
        registry.setMatrix4(mat, org.joml.Matrix4f())
        assertEquals(cleanRevision, registry.revision)
        assertFalse(f1.isDirty)
        assertFalse(mat.isDirty)

        // Mutating value bumps revision
        registry.setFloat1(f1, 2.5f)
        val afterFloat = registry.revision
        assertTrue(afterFloat > cleanRevision)
        assertTrue(f1.isDirty)

        // Clearing dirty flag does not bump revision
        f1.isDirty = false
        assertEquals(afterFloat, registry.revision)

        // Direct handle mutation also bumps revision
        f1.value = 3.5f
        val afterDirect = registry.revision
        assertTrue(afterDirect > afterFloat)

        // Matrix projection version update bumps only when version changes
        mat.isDirty = false
        val beforeProj = registry.revision
        registry.setProjectionMatrix4(mat, org.joml.Matrix4f().scale(2f), 1)
        val afterProj1 = registry.revision
        assertTrue(afterProj1 > beforeProj)
        mat.isDirty = false
        registry.setProjectionMatrix4(mat, org.joml.Matrix4f().scale(3f), 1)
        assertEquals(afterProj1, registry.revision)
    }
}
