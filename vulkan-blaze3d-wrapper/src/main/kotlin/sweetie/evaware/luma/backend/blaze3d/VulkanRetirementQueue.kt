package sweetie.evaware.luma.backend.blaze3d

import sweetie.evaware.luma.backend.blaze3d.preset.PresetCommandEncoder
import sweetie.evaware.luma.backend.blaze3d.preset.PresetFence

internal class VulkanRetirementQueue {
    internal class Submission {
        var fence: PresetFence? = null
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
        pending.add(resource)
    }

    fun hasPending(): Boolean = pending.isNotEmpty()

    fun collectCompleted() {
        collect(wait = false)
    }

    fun attachTo(encoder: PresetCommandEncoder): Submission? {
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

    fun closeAll(createEncoder: () -> PresetCommandEncoder) {
        var failure: Throwable? = null
        try {
            collect(wait = true)
        } catch (throwable: Throwable) {
            failure = throwable
        }
        if (pending.isNotEmpty()) {
            try {
                val encoder = createEncoder()
                val retirement = requireNotNull(attachTo(encoder))
                encoder.submit()
                submitted(retirement)
                collect(wait = true)
            } catch (throwable: Throwable) {
                if (failure == null) failure = throwable else failure.addSuppressed(throwable)
            }
        }
        if (failure != null) throw failure
    }

    private fun collect(wait: Boolean) {
        var firstFailure: Throwable? = null
        var index = 0
        while (index < submissions.size) {
            val submission = submissions[index]
            val fence = requireNotNull(submission.fence)
            if (!fence.awaitCompletion(if (wait) Long.MAX_VALUE else 0L)) {
                index++
                continue
            }
            try {
                fence.close()
            } catch (throwable: Throwable) {
                if (firstFailure == null) firstFailure = throwable else firstFailure.addSuppressed(throwable)
            }
            for (resourceIndex in submission.resources.indices) {
                try {
                    submission.resources[resourceIndex].close()
                } catch (throwable: Throwable) {
                    if (firstFailure == null) firstFailure = throwable else firstFailure.addSuppressed(throwable)
                }
            }
            submissions.removeAt(index)
            submission.reset()
            recycledSubmissions.addLast(submission)
        }
        if (firstFailure != null) throw firstFailure
    }
}
