package sweetie.evaware.luma.resource

import kotlin.test.Test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

class GlResourcesTest {
    @BeforeTest
    @AfterTest
    fun clearResources() {
        GlResources.closeAll()
    }

    @Test
    fun `closeAll closes tracked resources in reverse order`() {
        val closed = ArrayList<String>(3)

        GlResources.track(TestResource("first", closed))
        GlResources.track(TestResource("second", closed))
        GlResources.track(TestResource("third", closed))

        GlResources.closeAll()

        assertEquals(listOf("third", "second", "first"), closed)
    }

    @Test
    fun `untracked resources are not closed`() {
        val closed = ArrayList<String>(2)
        val resource = GlResources.track(TestResource("tracked", closed))

        GlResources.untrack(resource)
        GlResources.closeAll()

        assertEquals(emptyList(), closed)
    }

    @Test
    fun `closeAll continues after resource failure`() {
        val closed = ArrayList<String>(2)

        GlResources.track(TestResource("first", closed))
        GlResources.track(FailingResource)
        GlResources.track(TestResource("last", closed))

        GlResources.closeAll()

        assertEquals(listOf("last", "first"), closed)
    }

    private class TestResource(
        private val name: String,
        private val closed: MutableList<String>
    ) : AutoCloseable {
        override fun close() {
            closed += name
        }
    }

    private object FailingResource : AutoCloseable {
        override fun close() {
            error("boom")
        }
    }
}
