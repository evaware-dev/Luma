package sweetie.evaware.luma.uniform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UniformHandleTest {
    @Test
    fun `float uniform cache skips repeated value`() {
        val handle = Float1Uniform()

        assertTrue(handle.shouldUpload(1f))
        assertFalse(handle.shouldUpload(1f))
        assertTrue(handle.shouldUpload(2f))
    }

    @Test
    fun `int uniform cache skips repeated value`() {
        val handle = Int1Uniform()

        assertTrue(handle.shouldUpload(7))
        assertFalse(handle.shouldUpload(7))
        assertTrue(handle.shouldUpload(8))
    }

    @Test
    fun `resolve invalidates scalar cache`() {
        val handle = Float2Uniform()

        assertTrue(handle.shouldUpload(1f, 2f))
        assertFalse(handle.shouldUpload(1f, 2f))

        handle.resolve(4)

        assertTrue(handle.shouldUpload(1f, 2f))
    }

    @Test
    fun `projection version cache skips repeated matrix uploads`() {
        val handle = Mat4Uniform()

        assertTrue(handle.shouldUploadProjectionVersion(1))
        assertFalse(handle.shouldUploadProjectionVersion(1))
        assertTrue(handle.shouldUploadProjectionVersion(2))

        handle.invalidateProjectionVersion()

        assertTrue(handle.shouldUploadProjectionVersion(2))
    }
}
