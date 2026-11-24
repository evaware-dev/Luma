package sweetie.evaware.luma.backend.gl

import org.lwjgl.opengl.ARBTimerQuery
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL33
import sweetie.evaware.luma.api.GpuTimestampPool

internal class GlTimestampPool(
    override val capacity: Int,
    private val useArb: Boolean
) : GpuTimestampPool {
    private val queryIds = IntArray(capacity)
    private var closed = false

    override val timestampPeriodNanos: Double = 1.0

    init {
        require(capacity > 0) { "Timestamp pool capacity must be positive: $capacity" }
        GL15.glGenQueries(queryIds)
    }

    fun write(queryIndex: Int) {
        requireOpen()
        require(queryIndex in 0 until capacity) { "Query index $queryIndex out of bounds 0..${capacity - 1}" }
        val id = queryIds[queryIndex]
        if (useArb) {
            ARBTimerQuery.glQueryCounter(id, ARBTimerQuery.GL_TIMESTAMP)
        } else {
            GL33.glQueryCounter(id, GL33.GL_TIMESTAMP)
        }
    }

    override fun tryReadTimestampsNanos(count: Int, outNanos: LongArray): Boolean {
        requireOpen()
        require(count in 0..capacity) { "Query count $count out of bounds 0..$capacity" }
        require(outNanos.size >= count) { "Output array size ${outNanos.size} is smaller than count $count" }
        if (count == 0) return true

        // Fast non-blocking check on the last query first: if the frame end isn't ready, nothing is ready.
        if (GL15.glGetQueryObjecti(queryIds[count - 1], GL15.GL_QUERY_RESULT_AVAILABLE) == GL11.GL_FALSE) {
            return false
        }
        for (i in 0 until count - 1) {
            if (GL15.glGetQueryObjecti(queryIds[i], GL15.GL_QUERY_RESULT_AVAILABLE) == GL11.GL_FALSE) {
                return false
            }
        }
        for (i in 0 until count) {
            val id = queryIds[i]
            outNanos[i] = if (useArb) {
                ARBTimerQuery.glGetQueryObjectui64(id, GL15.GL_QUERY_RESULT)
            } else {
                GL33.glGetQueryObjectui64(id, GL15.GL_QUERY_RESULT)
            }
        }
        return true
    }

    override fun close() {
        if (closed) return
        closed = true
        GL15.glDeleteQueries(queryIds)
        queryIds.fill(0)
    }

    private fun requireOpen() {
        check(!closed) { "GlTimestampPool is closed" }
    }
}
