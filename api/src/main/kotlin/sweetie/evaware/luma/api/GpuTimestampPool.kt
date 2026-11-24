package sweetie.evaware.luma.api

interface GpuTimestampPool : AutoCloseable {
    val capacity: Int
    val timestampPeriodNanos: Double

    /**
     * Non-blocking read of timestamps `[0, count)` in nanoseconds into [outNanos].
     * Returns `true` only when all [count] timestamps are available on the GPU, and `false` otherwise.
     * Never blocks waiting for pending GPU work.
     */
    fun tryReadTimestampsNanos(count: Int, outNanos: LongArray): Boolean
}
