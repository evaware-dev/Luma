package sweetie.evaware.luma.api

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CloseableResourceTest {
    @Test
    fun `markClosed transitions once`() {
        val resource = TestCloseableResource()

        assertFalse(resource.isClosed)
        assertTrue(resource.closeOnce())
        assertTrue(resource.isClosed)
        assertFalse(resource.closeOnce())
    }

    @Test
    fun `requireOpen fails after close mark`() {
        val resource = TestCloseableResource()

        resource.requireOpen()
        resource.closeOnce()

        assertFailsWith<IllegalStateException> {
            resource.requireOpen()
        }
    }

    private class TestCloseableResource : CloseableResourceBase() {
        override fun close() {
            markClosed()
        }

        fun closeOnce() = markClosed()
    }
}
