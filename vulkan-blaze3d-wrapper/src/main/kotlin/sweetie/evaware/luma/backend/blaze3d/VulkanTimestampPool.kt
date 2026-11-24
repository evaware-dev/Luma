package sweetie.evaware.luma.backend.blaze3d

import sweetie.evaware.luma.api.GpuTimestampPool
import sweetie.evaware.luma.backend.blaze3d.preset.PresetTimestampQueryPool

internal class VulkanTimestampPool(
    val presetPool: PresetTimestampQueryPool,
    override val timestampPeriodNanos: Double,
    private val retirement: VulkanRetirementQueue,
    private val isBackendClosed: () -> Boolean
) : GpuTimestampPool {
    private var closed = false

    override val capacity: Int
        get() = presetPool.size

    fun requireOpen() {
        check(!closed) { "VulkanTimestampPool is closed" }
    }

    override fun tryReadTimestampsNanos(count: Int, outNanos: LongArray): Boolean {
        requireOpen()
        require(count in 0..capacity) { "Query count $count out of bounds 0..$capacity" }
        require(outNanos.size >= count) { "Output array size ${outNanos.size} is smaller than count $count" }
        if (count == 0) return true
        if (!presetPool.tryReadValues(count, outNanos)) return false
        val period = timestampPeriodNanos
        if (period != 1.0) {
            val base = outNanos[0]
            outNanos[0] = 0L
            for (i in 1 until count) {
                outNanos[i] = Math.round((outNanos[i] - base).toDouble() * period)
            }
        }
        return true
    }

    override fun close() {
        if (closed) return
        closed = true
        if (isBackendClosed()) {
            presetPool.close()
        } else {
            retirement.defer(presetPool)
        }
    }
}
