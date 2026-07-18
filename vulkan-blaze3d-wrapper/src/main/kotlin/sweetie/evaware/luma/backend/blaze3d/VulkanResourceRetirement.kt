package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.buffers.GpuFence
import com.mojang.blaze3d.systems.CommandEncoder

internal object VulkanResourceRetirement {
    private class Submission(
        val fence: GpuFence,
        val resources: List<AutoCloseable>
    )

    private val pending = ArrayList<AutoCloseable>()
    private val submissions = ArrayList<Submission>()

    fun defer(resource: AutoCloseable) {
        pending += resource
    }

    fun defer(action: () -> Unit) {
        defer(AutoCloseable(action))
    }

    fun hasPending(): Boolean = pending.isNotEmpty()

    fun collectCompleted() {
        collect(wait = false)
    }

    fun attachTo(encoder: CommandEncoder): Pair<GpuFence, List<AutoCloseable>>? {
        if (pending.isEmpty()) return null
        val resources = ArrayList(pending)
        pending.clear()
        return encoder.createFence() to resources
    }

    fun submitted(retirement: Pair<GpuFence, List<AutoCloseable>>?) {
        if (retirement == null) return
        submissions += Submission(retirement.first, retirement.second)
    }

    fun closeAll(createEncoder: () -> CommandEncoder) {
        collect(wait = true)
        if (pending.isEmpty()) return

        val encoder = createEncoder()
        val retirement = requireNotNull(attachTo(encoder))
        encoder.submit()
        submitted(retirement)
        collect(wait = true)
    }

    private fun collect(wait: Boolean) {
        var index = 0
        while (index < submissions.size) {
            val submission = submissions[index]
            if (!submission.fence.awaitCompletion(if (wait) Long.MAX_VALUE else 0L)) {
                index++
                continue
            }
            submission.fence.close()
            submission.resources.forEach(AutoCloseable::close)
            submissions.removeAt(index)
        }
    }
}
