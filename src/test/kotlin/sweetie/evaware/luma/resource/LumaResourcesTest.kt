package sweetie.evaware.luma.resource

import kotlin.test.Test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

class LumaResourcesTest {
    @BeforeTest
    @AfterTest
    fun clearResources() {
        LumaResources.closeAll()
    }

    @Test
    fun `closeAll closes tracked resources in reverse order`() {
        val closed = ArrayList<String>(3)

        LumaResources.track(TestResource("first", closed))
        LumaResources.track(TestResource("second", closed))
        LumaResources.track(TestResource("third", closed))

        LumaResources.closeAll()

        assertEquals(listOf("third", "second", "first"), closed)
    }

    @Test
    fun `untracked resources are not closed`() {
        val closed = ArrayList<String>(2)
        val resource = LumaResources.track(TestResource("tracked", closed))

        LumaResources.untrack(resource)
        LumaResources.closeAll()

        assertEquals(emptyList(), closed)
    }

    @Test
    fun `closeAll continues after resource failure`() {
        val closed = ArrayList<String>(2)

        LumaResources.track(TestResource("first", closed))
        LumaResources.track(FailingResource)
        LumaResources.track(TestResource("last", closed))

        LumaResources.closeAll()

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
