package sweetie.evaware.luma.backend.blaze3d

import net.minecraft.resources.Identifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import sweetie.evaware.luma.vertex.VertexLayout

class ProgramLifecycleTest {
    @Test
    fun schedulesCpuCleanupOnceAndRejectsNewDraws() {
        var scheduled: (() -> Unit)? = null
        var schedules = 0
        val program = Program(
            Identifier.fromNamespaceAndPath("test", "lifecycle"),
            "",
            "",
            VertexLayout()
        ) { action ->
            schedules++
            scheduled = action
        }

        program.close()
        program.close()

        assertEquals(1, schedules)
        assertNotNull(scheduled)
        assertThrows(IllegalStateException::class.java) { program.requireOpen() }
        scheduled?.invoke()
    }
}
