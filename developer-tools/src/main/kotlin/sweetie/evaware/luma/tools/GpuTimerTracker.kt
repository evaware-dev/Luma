package sweetie.evaware.luma.tools

import java.util.AbstractList
import java.util.Arrays
import sweetie.evaware.luma.api.GpuTimestampPool
import sweetie.evaware.luma.api.RenderBackend

class GpuZoneTiming internal constructor() {
    var name: String = ""
        internal set
    var nanos: Long = 0L
        internal set
    var depth: Int = 0
        internal set
}

internal class GpuTimerTracker(
    private val delegate: RenderBackend,
    private val maxQueriesPerFrame: Int = DEFAULT_MAX_QUERIES
) : AutoCloseable {
    private val maxZones = (maxQueriesPerFrame - 2) / 2
    private val slots: Array<FrameSlot> = run {
        val created = arrayOfNulls<FrameSlot>(RING_SIZE)
        try {
            for (i in 0 until RING_SIZE) {
                created[i] = FrameSlot(delegate.createTimestampPool(maxQueriesPerFrame), maxZones)
            }
            @Suppress("UNCHECKED_CAST")
            created as Array<FrameSlot>
        } catch (failure: Throwable) {
            for (i in created.indices) {
                try {
                    created[i]?.pool?.close()
                } catch (closeFailure: Throwable) {
                    failure.addSuppressed(closeFailure)
                }
            }
            throw failure
        }
    }
    private val scratchNanos = LongArray(maxQueriesPerFrame)
    private val openZoneStack = IntArray(maxZones)
    private val completedZones = Array(maxZones) { GpuZoneTiming() }

    private var writeCursor = 0
    private var readCursor = 0
    private var activeSlot: FrameSlot? = null
    private var openZoneDepth = 0
    private var overflowOpenDepth = 0
    private var closed = false

    var lastGpuFrameNanos: Long = 0L
        private set
    var lastGpuFrameIndex: Long = 0L
        private set
    var completedZoneCount: Int = 0
        private set

    val gpuZonesView: List<GpuZoneTiming> = object : AbstractList<GpuZoneTiming>(), RandomAccess {
        override val size: Int get() = completedZoneCount
        override fun get(index: Int): GpuZoneTiming {
            if (index !in 0 until completedZoneCount) {
                throw IndexOutOfBoundsException("Index $index out of bounds for length $completedZoneCount")
            }
            return completedZones[index]
        }
    }

    fun beginFrame(frameIndex: Long) {
        if (closed) return
        harvestCompleted()
        val slot = slots[writeCursor]
        if (slot.inFlight) {
            // Ring is full (GPU is > RING_SIZE frames behind): skip timing this frame rather than stalling.
            activeSlot = null
            return
        }
        slot.resetForFrame(frameIndex)
        slot.frameStartQuery = 0
        slot.queryCount = 1
        openZoneDepth = 0
        overflowOpenDepth = 0
        delegate.writeTimestamp(slot.pool, 0)
        activeSlot = slot
    }

    fun pushZone(label: String) {
        val slot = activeSlot ?: return
        if (slot.queryCount + 2 <= maxQueriesPerFrame && slot.zoneCount < maxZones && openZoneDepth < openZoneStack.size) {
            val zoneIdx = slot.zoneCount++
            val startQuery = slot.queryCount++
            slot.zoneNames[zoneIdx] = label
            slot.zoneStartQueries[zoneIdx] = startQuery
            slot.zoneEndQueries[zoneIdx] = -1
            slot.zoneDepths[zoneIdx] = openZoneDepth
            openZoneStack[openZoneDepth++] = zoneIdx
            delegate.writeTimestamp(slot.pool, startQuery)
        } else if (openZoneDepth < openZoneStack.size) {
            openZoneStack[openZoneDepth++] = -1
        } else {
            overflowOpenDepth++
        }
    }

    fun popZone() {
        val slot = activeSlot ?: return
        if (overflowOpenDepth > 0) {
            overflowOpenDepth--
            return
        }
        if (openZoneDepth <= 0) return
        val zoneIdx = openZoneStack[--openZoneDepth]
        if (zoneIdx >= 0 && slot.queryCount < maxQueriesPerFrame) {
            val endQuery = slot.queryCount++
            slot.zoneEndQueries[zoneIdx] = endQuery
            delegate.writeTimestamp(slot.pool, endQuery)
        }
    }

    fun beforeDelegateEndFrame() {
        val slot = activeSlot ?: return
        val endQuery = slot.queryCount++
        slot.frameEndQuery = endQuery
        delegate.writeTimestamp(slot.pool, endQuery)
    }

    fun afterDelegateEndFrame(succeeded: Boolean) {
        val slot = activeSlot ?: return
        activeSlot = null
        openZoneDepth = 0
        overflowOpenDepth = 0
        if (succeeded && slot.frameEndQuery >= 0) {
            slot.inFlight = true
            writeCursor = (writeCursor + 1) and RING_MASK
        } else {
            slot.clearReferences()
            slot.inFlight = false
        }
    }

    fun harvestCompleted(): Boolean {
        if (closed) return false
        var harvested = false
        var steps = 0
        while (steps < RING_SIZE) {
            val slot = slots[readCursor]
            if (!slot.inFlight) break
            if (!slot.pool.tryReadTimestampsNanos(slot.queryCount, scratchNanos)) break

            lastGpuFrameIndex = slot.frameIndex
            lastGpuFrameNanos = (scratchNanos[slot.frameEndQuery] - scratchNanos[slot.frameStartQuery]).coerceAtLeast(0L)

            var published = 0
            for (i in 0 until slot.zoneCount) {
                val endQ = slot.zoneEndQueries[i]
                if (endQ < 0) continue
                val startQ = slot.zoneStartQueries[i]
                val zone = completedZones[published++]
                zone.name = slot.zoneNames[i] ?: ""
                zone.nanos = (scratchNanos[endQ] - scratchNanos[startQ]).coerceAtLeast(0L)
                zone.depth = slot.zoneDepths[i]
            }
            for (i in published until completedZoneCount) {
                val stale = completedZones[i]
                stale.name = ""
                stale.nanos = 0L
                stale.depth = 0
            }
            completedZoneCount = published

            slot.clearReferences()
            slot.inFlight = false
            readCursor = (readCursor + 1) and RING_MASK
            harvested = true
            steps++
        }
        return harvested
    }

    override fun close() {
        if (closed) return
        closed = true
        activeSlot = null
        openZoneDepth = 0
        overflowOpenDepth = 0
        var failure: Throwable? = null
        for (i in slots.indices) {
            val slot = slots[i]
            slot.clearReferences()
            slot.inFlight = false
            try {
                slot.pool.close()
            } catch (throwable: Throwable) {
                if (failure == null) failure = throwable else failure.addSuppressed(throwable)
            }
        }
        for (i in 0 until completedZoneCount) {
            completedZones[i].name = ""
            completedZones[i].nanos = 0L
            completedZones[i].depth = 0
        }
        completedZoneCount = 0
        if (failure != null) throw failure
    }

    private class FrameSlot(
        val pool: GpuTimestampPool,
        maxZones: Int
    ) {
        var inFlight = false
        var frameIndex = 0L
        var queryCount = 0
        var frameStartQuery = 0
        var frameEndQuery = -1
        var zoneCount = 0
        val zoneNames = arrayOfNulls<String>(maxZones)
        val zoneStartQueries = IntArray(maxZones)
        val zoneEndQueries = IntArray(maxZones)
        val zoneDepths = IntArray(maxZones)

        fun resetForFrame(index: Long) {
            frameIndex = index
            queryCount = 0
            frameStartQuery = 0
            frameEndQuery = -1
            zoneCount = 0
        }

        fun clearReferences() {
            if (zoneCount > 0) {
                Arrays.fill(zoneNames, 0, zoneCount, null)
                zoneCount = 0
            }
        }
    }

    companion object {
        const val RING_SIZE = 4
        private const val RING_MASK = RING_SIZE - 1
        const val DEFAULT_MAX_QUERIES = 128
    }
}
