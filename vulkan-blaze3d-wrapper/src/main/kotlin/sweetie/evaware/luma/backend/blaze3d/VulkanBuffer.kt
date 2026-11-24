package sweetie.evaware.luma.backend.blaze3d

import java.nio.ByteBuffer
import java.util.Arrays
import sweetie.evaware.luma.backend.blaze3d.preset.PresetCommandEncoder
import sweetie.evaware.luma.backend.blaze3d.preset.PresetGpuBuffer
import sweetie.evaware.luma.backend.blaze3d.preset.PresetGpuBufferSlice
import sweetie.evaware.luma.backend.blaze3d.preset.VulkanPreset
import sweetie.evaware.luma.backend.blaze3d.preset.VulkanPresets

class VulkanBuffer internal constructor(
    private val label: () -> String,
    private val usage: Int,
    initialCapacity: Int,
    private val preset: VulkanPreset,
    private val retirement: VulkanRetirementQueue?,
    initialSliceCapacity: Int,
    maxSliceCapacity: Int,
    private val lowWaterFramesThreshold: Int
) : AutoCloseable {
    constructor(
        label: () -> String,
        usage: Int,
        initialCapacity: Int,
        preset: VulkanPreset = VulkanPresets.active
    ) : this(
        label,
        usage,
        initialCapacity,
        preset,
        null,
        DEFAULT_INITIAL_SLICE_CAPACITY,
        DEFAULT_MAX_SLICE_CAPACITY,
        DEFAULT_LOW_WATER_FRAMES
    )

    internal constructor(
        label: () -> String,
        usage: Int,
        initialCapacity: Int,
        preset: VulkanPreset,
        retirement: VulkanRetirementQueue
    ) : this(
        label,
        usage,
        initialCapacity,
        preset,
        retirement,
        DEFAULT_INITIAL_SLICE_CAPACITY,
        DEFAULT_MAX_SLICE_CAPACITY,
        DEFAULT_LOW_WATER_FRAMES
    )

    private val initialTableCapacity = nextPowerOfTwoUnbounded(initialSliceCapacity.coerceAtLeast(1))
    private val maxTableCapacity = nextPowerOfTwoUnbounded(maxSliceCapacity.coerceAtLeast(initialTableCapacity))

    var gpuBuffer: PresetGpuBuffer? = null
        private set
    var capacityBytes = initialCapacity
        private set

    // Open-addressed (offset, length) -> PresetGpuBufferSlice table resilient to dynamic offset drift
    private var slices = arrayOfNulls<PresetGpuBufferSlice>(initialTableCapacity)
    private var sliceOffsets = LongArray(initialTableCapacity)
    private var sliceLengths = LongArray(initialTableCapacity) { EMPTY_LENGTH }
    private var entryCount = 0
    private var accessesThisFrame = 0
    private var lowWaterFrames = 0
    private var peakSinceTrim = 0
    private var cachedFullSlice: PresetGpuBufferSlice? = null

    internal val sliceCacheCapacity: Int
        get() = slices.size

    fun beginFrame() {
        val used = accessesThisFrame
        accessesThisFrame = 0

        val cacheSize = slices.size
        if (cacheSize > initialTableCapacity) {
            if (used > peakSinceTrim) peakSinceTrim = used
            if (used <= (cacheSize ushr 2)) {
                lowWaterFrames++
                if (lowWaterFrames >= lowWaterFramesThreshold) {
                    val desired = maxOf(initialTableCapacity, nextPowerOfTwo(peakSinceTrim shl 1))
                        .coerceAtMost(cacheSize)
                    if (desired < cacheSize) {
                        resetTable(desired)
                    }
                    lowWaterFrames = 0
                    peakSinceTrim = 0
                }
            } else {
                lowWaterFrames = 0
                peakSinceTrim = 0
            }
        } else {
            lowWaterFrames = 0
            peakSinceTrim = 0
        }
    }

    fun ensureCapacity(bytes: Int): PresetGpuBuffer {
        val existing = gpuBuffer
        if (existing != null && bytes <= capacityBytes) return existing
        if (existing != null) retireBuffer(existing)
        var cap = capacityBytes.coerceAtLeast(256)
        while (cap < bytes) cap = Math.multiplyExact(cap, 2)
        val created = preset.createBuffer(
            label,
            usage,
            cap.toLong()
        )
        gpuBuffer = created
        capacityBytes = cap
        clearTableEntries()
        accessesThisFrame = 0
        cachedFullSlice = null
        return created
    }

    fun write(encoder: PresetCommandEncoder, data: ByteBuffer) {
        val bytes = data.remaining()
        if (bytes == 0) return
        val buffer = ensureCapacity(bytes)
        encoder.writeToBuffer(slice(buffer, 0L, bytes.toLong()), data)
    }

    fun slice(offset: Long, bytes: Long): PresetGpuBufferSlice {
        val buffer = gpuBuffer ?: error("Buffer not allocated")
        return slice(buffer, offset, bytes)
    }

    fun fullSlice(): PresetGpuBufferSlice {
        cachedFullSlice?.let { return it }
        return (gpuBuffer ?: error("Buffer not allocated")).slice().also { cachedFullSlice = it }
    }

    private fun slice(buffer: PresetGpuBuffer, offset: Long, bytes: Long): PresetGpuBufferSlice {
        accessesThisFrame++
        val tableSlices = slices
        val tableOffsets = sliceOffsets
        val tableLengths = sliceLengths
        val mask = tableSlices.size - 1
        val primary = hashKey(offset, bytes) and mask

        var slot = primary
        for (step in 0 until MAX_PROBE) {
            val len = tableLengths[slot]
            if (len == bytes && tableOffsets[slot] == offset) {
                return tableSlices[slot]!!
            }
            if (len == EMPTY_LENGTH) {
                if ((entryCount + 1) * 4 > tableSlices.size * 3 && tableSlices.size < maxTableCapacity) {
                    rehash(tableSlices.size shl 1)
                    val created = buffer.slice(offset, bytes)
                    insertAfterRehash(created, offset, bytes)
                    return created
                }
                val created = buffer.slice(offset, bytes)
                tableOffsets[slot] = offset
                tableLengths[slot] = bytes
                tableSlices[slot] = created
                entryCount++
                return created
            }
            slot = (slot + 1) and mask
        }

        if (tableSlices.size < maxTableCapacity) {
            rehash(tableSlices.size shl 1)
            val created = buffer.slice(offset, bytes)
            insertAfterRehash(created, offset, bytes)
            return created
        }

        // Bounded at maxTableCapacity: overwrite primary slot to maintain O(1) probe bound
        val created = buffer.slice(offset, bytes)
        tableOffsets[primary] = offset
        tableLengths[primary] = bytes
        tableSlices[primary] = created
        return created
    }

    private fun rehash(newCapacity: Int) {
        val oldSlices = slices
        val oldOffsets = sliceOffsets
        val oldLengths = sliceLengths
        resetTable(newCapacity)
        for (i in oldSlices.indices) {
            val len = oldLengths[i]
            if (len != EMPTY_LENGTH) {
                insertAfterRehash(oldSlices[i]!!, oldOffsets[i], len)
            }
        }
    }

    private fun insertAfterRehash(slice: PresetGpuBufferSlice, offset: Long, bytes: Long) {
        val mask = slices.size - 1
        val primary = hashKey(offset, bytes) and mask
        var slot = primary
        for (step in 0 until MAX_PROBE) {
            if (sliceLengths[slot] == EMPTY_LENGTH) {
                sliceOffsets[slot] = offset
                sliceLengths[slot] = bytes
                slices[slot] = slice
                entryCount++
                return
            }
            slot = (slot + 1) and mask
        }
        sliceOffsets[primary] = offset
        sliceLengths[primary] = bytes
        slices[primary] = slice
    }

    private fun resetTable(capacity: Int) {
        slices = arrayOfNulls(capacity)
        sliceOffsets = LongArray(capacity)
        sliceLengths = LongArray(capacity) { EMPTY_LENGTH }
        entryCount = 0
    }

    private fun clearTableEntries() {
        Arrays.fill(slices, null)
        Arrays.fill(sliceLengths, EMPTY_LENGTH)
        entryCount = 0
    }

    override fun close() {
        val existing = gpuBuffer
        gpuBuffer = null
        clearTableEntries()
        accessesThisFrame = 0
        lowWaterFrames = 0
        peakSinceTrim = 0
        cachedFullSlice = null
        if (existing != null) retireBuffer(existing)
    }

    private fun retireBuffer(buffer: PresetGpuBuffer) {
        val queue = retirement
        if (queue != null) queue.defer(buffer) else buffer.close()
    }

    private fun nextPowerOfTwo(value: Int): Int {
        var cap = 1
        while (cap < value && cap < maxTableCapacity) cap = cap shl 1
        return cap
    }

    private companion object {
        const val DEFAULT_INITIAL_SLICE_CAPACITY = 2048
        const val DEFAULT_MAX_SLICE_CAPACITY = 16384
        const val DEFAULT_LOW_WATER_FRAMES = 60
        const val EMPTY_LENGTH = -1L
        const val MAX_PROBE = 8

        fun nextPowerOfTwoUnbounded(value: Int): Int {
            var cap = 1
            while (cap < value) cap = cap shl 1
            return cap
        }

        fun hashKey(offset: Long, bytes: Long): Int {
            var x = offset xor (bytes * -7046029254386353131L)
            x = (x xor (x ushr 30)) * -4658895280553007687L
            x = (x xor (x ushr 27)) * -7723592293110705685L
            return (x xor (x ushr 31)).toInt()
        }
    }
}
