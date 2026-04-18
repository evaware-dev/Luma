package sweetie.evaware.luma.scissor

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScissorControlTest {
    @AfterTest
    fun resetScissor() {
        ScissorControl.clear()
    }

    @Test
    fun `push intersects with current scissor`() {
        ScissorControl.clear()

        ScissorControl.push(0f, -100f, 100f, 100f)
        ScissorControl.push(25f, -75f, 100f, 50f)

        assertEquals(25f, ScissorControl.minX())
        assertEquals(25f, ScissorControl.minY())
        assertEquals(100f, ScissorControl.maxX())
        assertEquals(75f, ScissorControl.maxY())
    }

    @Test
    fun `pop restores previous scissor and empty state`() {
        ScissorControl.clear()

        ScissorControl.push(0f, -100f, 100f, 100f)
        val rootVersion = ScissorControl.version()
        ScissorControl.push(25f, -75f, 50f, 50f)

        ScissorControl.pop()

        assertTrue(ScissorControl.version() > rootVersion)
        assertEquals(0f, ScissorControl.minX())
        assertEquals(0f, ScissorControl.minY())
        assertEquals(100f, ScissorControl.maxX())
        assertEquals(100f, ScissorControl.maxY())

        ScissorControl.pop()

        assertEquals(0f, ScissorControl.minX())
        assertEquals(0f, ScissorControl.minY())
        assertEquals(0f, ScissorControl.maxX())
        assertEquals(0f, ScissorControl.maxY())
    }

    @Test
    fun `pop without push fails`() {
        ScissorControl.clear()

        assertFailsWith<IllegalArgumentException> {
            ScissorControl.pop()
        }
    }

    @Test
    fun `push pop stays inside cpu budget`() {
        ScissorControl.clear()
        val iterations = 120_000

        repeat(5_000) {
            ScissorControl.push(0f, -64f, 64f, 64f)
            ScissorControl.pop()
        }

        val started = System.nanoTime()
        repeat(iterations) {
            ScissorControl.push(0f, -64f, 64f, 64f)
            ScissorControl.pop()
        }
        val elapsedNanos = System.nanoTime() - started
        val averageNanos = elapsedNanos / iterations

        assertTrue(
            averageNanos <= 1_200L,
            "Scissor push/pop regression: $averageNanos ns per push/pop pair"
        )
    }
}
