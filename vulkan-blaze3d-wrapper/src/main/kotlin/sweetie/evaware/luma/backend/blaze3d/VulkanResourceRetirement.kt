package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.buffers.GpuFence
import com.mojang.blaze3d.systems.CommandEncoder

internal object VulkanResourceRetirement {
    internal class Submission {
        var fence: GpuFence? = null
        val resources = ArrayList<AutoCloseable>()

        fun reset() {
            fence = null
            resources.clear()
        }
    }

    private val pending = ArrayList<AutoCloseable>()
    private val submissions = ArrayList<Submission>()
    private val recycledSubmissions = ArrayDeque<Submission>()

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

    fun attachTo(encoder: CommandEncoder): Submission? {
        if (pending.isEmpty()) return null
        val submission = if (recycledSubmissions.isEmpty()) Submission() else recycledSubmissions.removeFirst()
        submission.fence = encoder.createFence()
        submission.resources.ensureCapacity(pending.size)
        submission.resources.addAll(pending)
        pending.clear()
        return submission
    }

    fun submitted(retirement: Submission?) {
        if (retirement == null) return
        submissions.add(retirement)
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
            val fence = requireNotNull(submission.fence)
            if (!fence.awaitCompletion(if (wait) Long.MAX_VALUE else 0L)) {
                index++
                continue
            }
            var failure: Throwable? = null
            try {
                fence.close()
            } catch (throwable: Throwable) {
                failure = throwable
            }
            for (resourceIndex in submission.resources.indices) {
                try {
                    submission.resources[resourceIndex].close()
                } catch (throwable: Throwable) {
                    if (failure == null) failure = throwable else failure.addSuppressed(throwable)
                }
            }
            submissions.removeAt(index)
            submission.reset()
            recycledSubmissions.addLast(submission)
            if (failure != null) throw failure
        }
    }
}
