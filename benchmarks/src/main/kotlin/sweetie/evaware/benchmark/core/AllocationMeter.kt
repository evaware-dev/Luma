package sweetie.evaware.benchmark.core

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory

internal class AllocationMeter private constructor(
    private val bean: ThreadMXBean?
) {
    val available: Boolean
        get() = bean != null

    fun currentThreadBytes(): Long {
        val allocationBean = bean ?: return UNAVAILABLE
        return allocationBean.getThreadAllocatedBytes(Thread.currentThread().threadId())
    }

    companion object {
        const val UNAVAILABLE = -1L

        fun create(): AllocationMeter {
            val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
            if (bean == null || !bean.isThreadAllocatedMemorySupported) {
                return AllocationMeter(null)
            }
            if (!bean.isThreadAllocatedMemoryEnabled) {
                bean.isThreadAllocatedMemoryEnabled = true
            }
            return AllocationMeter(bean)
        }
    }
}
