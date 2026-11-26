package sweetie.evaware.luma.vertex

import java.nio.FloatBuffer
import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.api.Clearable
import sweetie.evaware.luma.api.CloseableResourceBase

class VertexStream : CloseableResourceBase(), Clearable {
    companion object {
        private const val INITIAL_FLOAT_CAPACITY = 1024
    }

    private var nextLayoutIndex = 0
    private var rawWrittenFloats = 0

    var floatCount = 0
        internal set

    var vertexCount = 0
        internal set

    var uploadBuffer: FloatBuffer = MemoryUtil.memAllocFloat(INITIAL_FLOAT_CAPACITY)
        internal set

    private var baseAddress: Long = MemoryUtil.memAddress(uploadBuffer)
    private var capacityFloats = INITIAL_FLOAT_CAPACITY

    fun hasVertices() = vertexCount > 0

    fun flipForUpload(): FloatBuffer {
        val buf = uploadBuffer
        buf.limit(floatCount)
        buf.position(0)
        return buf
    }

    internal fun writeVec1(strideFloats: Int, value: Float) {
        val written = rawWrittenFloats
        if (written == 0 && (strideFloats <= 0 || nextLayoutIndex != 0)) {
            validateRawStart(strideFloats)
        }
        val nextWritten = written + 1
        if (nextWritten > strideFloats) throwStrideOverflow(written, 1, strideFloats)
        val offset = floatCount
        val nextOffset = offset + 1
        if (nextOffset > capacityFloats) growOrFail(nextOffset)
        MemoryUtil.memPutFloat(baseAddress + (offset.toLong() shl 2), value)
        floatCount = nextOffset
        if (nextWritten == strideFloats) {
            vertexCount++
            rawWrittenFloats = 0
        } else {
            rawWrittenFloats = nextWritten
        }
    }

    internal fun writeVec2(strideFloats: Int, first: Float, second: Float) {
        val written = rawWrittenFloats
        if (written == 0 && (strideFloats <= 0 || nextLayoutIndex != 0)) {
            validateRawStart(strideFloats)
        }
        val nextWritten = written + 2
        if (nextWritten > strideFloats) throwStrideOverflow(written, 2, strideFloats)
        val offset = floatCount
        val nextOffset = offset + 2
        if (nextOffset > capacityFloats) growOrFail(nextOffset)
        val addr = baseAddress + (offset.toLong() shl 2)
        MemoryUtil.memPutFloat(addr, first)
        MemoryUtil.memPutFloat(addr + 4L, second)
        floatCount = nextOffset
        if (nextWritten == strideFloats) {
            vertexCount++
            rawWrittenFloats = 0
        } else {
            rawWrittenFloats = nextWritten
        }
    }

    internal fun writeVec3(strideFloats: Int, first: Float, second: Float, third: Float) {
        val written = rawWrittenFloats
        if (written == 0 && (strideFloats <= 0 || nextLayoutIndex != 0)) {
            validateRawStart(strideFloats)
        }
        val nextWritten = written + 3
        if (nextWritten > strideFloats) throwStrideOverflow(written, 3, strideFloats)
        val offset = floatCount
        val nextOffset = offset + 3
        if (nextOffset > capacityFloats) growOrFail(nextOffset)
        val addr = baseAddress + (offset.toLong() shl 2)
        MemoryUtil.memPutFloat(addr, first)
        MemoryUtil.memPutFloat(addr + 4L, second)
        MemoryUtil.memPutFloat(addr + 8L, third)
        floatCount = nextOffset
        if (nextWritten == strideFloats) {
            vertexCount++
            rawWrittenFloats = 0
        } else {
            rawWrittenFloats = nextWritten
        }
    }

    internal fun writeVec4(strideFloats: Int, first: Float, second: Float, third: Float, fourth: Float) {
        val written = rawWrittenFloats
        if (written == 0 && (strideFloats <= 0 || nextLayoutIndex != 0)) {
            validateRawStart(strideFloats)
        }
        val nextWritten = written + 4
        if (nextWritten > strideFloats) throwStrideOverflow(written, 4, strideFloats)
        val offset = floatCount
        val nextOffset = offset + 4
        if (nextOffset > capacityFloats) growOrFail(nextOffset)
        val addr = baseAddress + (offset.toLong() shl 2)
        MemoryUtil.memPutFloat(addr, first)
        MemoryUtil.memPutFloat(addr + 4L, second)
        MemoryUtil.memPutFloat(addr + 8L, third)
        MemoryUtil.memPutFloat(addr + 12L, fourth)
        floatCount = nextOffset
        if (nextWritten == strideFloats) {
            vertexCount++
            rawWrittenFloats = 0
        } else {
            rawWrittenFloats = nextWritten
        }
    }

    internal fun requireRawIdle() {
        check(rawWrittenFloats == 0) { "Cannot switch vertex write modes in the middle of a vertex" }
    }

    internal fun resetRawWriter() {
        rawWrittenFloats = 0
    }

    fun putAttribute2(layouts: VertexLayout, layoutPos: Int, first: Float, second: Float) {
        requireNextLayout(layouts, layoutPos, ShaderVertType.FLOAT, 2)
        val offset = floatCount
        val nextOffset = offset + 2
        if (nextOffset > capacityFloats) growOrFail(nextOffset)
        val addr = baseAddress + (offset.toLong() shl 2)
        MemoryUtil.memPutFloat(addr, first)
        MemoryUtil.memPutFloat(addr + 4L, second)
        floatCount = nextOffset
        advance(layouts)
    }

    fun putAttribute3(layouts: VertexLayout, layoutPos: Int, first: Float, second: Float, third: Float) {
        requireNextLayout(layouts, layoutPos, ShaderVertType.FLOAT, 3)
        val offset = floatCount
        val nextOffset = offset + 3
        if (nextOffset > capacityFloats) growOrFail(nextOffset)
        val addr = baseAddress + (offset.toLong() shl 2)
        MemoryUtil.memPutFloat(addr, first)
        MemoryUtil.memPutFloat(addr + 4L, second)
        MemoryUtil.memPutFloat(addr + 8L, third)
        floatCount = nextOffset
        advance(layouts)
    }

    fun putAttribute4(layouts: VertexLayout, layoutPos: Int, first: Float, second: Float, third: Float, fourth: Float) {
        requireNextLayout(layouts, layoutPos, ShaderVertType.FLOAT, 4)
        val offset = floatCount
        val nextOffset = offset + 4
        if (nextOffset > capacityFloats) growOrFail(nextOffset)
        val addr = baseAddress + (offset.toLong() shl 2)
        MemoryUtil.memPutFloat(addr, first)
        MemoryUtil.memPutFloat(addr + 4L, second)
        MemoryUtil.memPutFloat(addr + 8L, third)
        MemoryUtil.memPutFloat(addr + 12L, fourth)
        floatCount = nextOffset
        advance(layouts)
    }

    internal fun requireVertexBoundary() {
        check(nextLayoutIndex == 0) { "Cannot switch vertex write modes in the middle of a vertex" }
    }

    internal fun putRaw1(value: Float) {
        val offset = floatCount
        val nextOffset = offset + 1
        if (nextOffset > capacityFloats) growOrFail(nextOffset)
        MemoryUtil.memPutFloat(baseAddress + (offset.toLong() shl 2), value)
        floatCount = nextOffset
    }

    internal fun putRaw2(first: Float, second: Float) {
        val offset = floatCount
        val nextOffset = offset + 2
        if (nextOffset > capacityFloats) growOrFail(nextOffset)
        val addr = baseAddress + (offset.toLong() shl 2)
        MemoryUtil.memPutFloat(addr, first)
        MemoryUtil.memPutFloat(addr + 4L, second)
        floatCount = nextOffset
    }

    internal fun putRaw3(first: Float, second: Float, third: Float) {
        val offset = floatCount
        val nextOffset = offset + 3
        if (nextOffset > capacityFloats) growOrFail(nextOffset)
        val addr = baseAddress + (offset.toLong() shl 2)
        MemoryUtil.memPutFloat(addr, first)
        MemoryUtil.memPutFloat(addr + 4L, second)
        MemoryUtil.memPutFloat(addr + 8L, third)
        floatCount = nextOffset
    }

    internal fun putRaw4(first: Float, second: Float, third: Float, fourth: Float) {
        val offset = floatCount
        val nextOffset = offset + 4
        if (nextOffset > capacityFloats) growOrFail(nextOffset)
        val addr = baseAddress + (offset.toLong() shl 2)
        MemoryUtil.memPutFloat(addr, first)
        MemoryUtil.memPutFloat(addr + 4L, second)
        MemoryUtil.memPutFloat(addr + 8L, third)
        MemoryUtil.memPutFloat(addr + 12L, fourth)
        floatCount = nextOffset
    }

    internal fun completeRawVertex() {
        completeVertex()
    }

    private fun validateRawStart(strideFloats: Int) {
        require(strideFloats > 0) { "Shader requires at least one vertex layout" }
        requireVertexBoundary()
    }

    private fun throwStrideOverflow(written: Int, count: Int, stride: Int): Nothing {
        error("Vertex stride overflow: ${written + count} floats written, stride is $stride")
    }

    private fun advance(layouts: VertexLayout) {
        nextLayoutIndex++
        if (nextLayoutIndex == layouts.size()) {
            completeVertex()
        }
    }

    private fun completeVertex() {
        vertexCount++
        nextLayoutIndex = 0
    }

    override fun clear() {
        if (isClosed) return
        nextLayoutIndex = 0
        rawWrittenFloats = 0
        floatCount = 0
        vertexCount = 0
        uploadBuffer.clear()
    }

    private fun growOrFail(requiredFloats: Int) {
        requireOpen()
        if (requiredFloats <= capacityFloats) return
        val grown = MemoryUtil.memRealloc(uploadBuffer, nextCapacity(requiredFloats, capacityFloats))
        grown.clear()
        uploadBuffer = grown
        baseAddress = MemoryUtil.memAddress(grown)
        capacityFloats = grown.capacity()
    }

    private fun requireNextLayout(layouts: VertexLayout, layoutPos: Int, type: ShaderVertType, count: Int) {
        val nextLayoutPos = layouts.layoutPos(nextLayoutIndex)
        val nextType = layouts.type(nextLayoutIndex)
        val nextCount = layouts.count(nextLayoutIndex)

        require(nextLayoutPos == layoutPos) { "Expected layout $nextLayoutPos, got $layoutPos" }
        require(nextType == type) { "Expected layout type $nextType, got $type" }
        require(nextCount == count) { "Expected layout count $nextCount, got $count" }
    }

    private fun nextCapacity(required: Int, current: Int): Int {
        var capacity = current.coerceAtLeast(1)
        while (capacity < required) {
            capacity = capacity shl 1
        }
        return capacity
    }

    fun reserveVertices(layouts: VertexLayout, count: Int) {
        val required = count * layouts.strideFloats
        if (required > capacityFloats) growOrFail(required) else requireOpen()
    }

    override fun close() {
        if (!markClosed()) return
        capacityFloats = -1
        baseAddress = 0L
        MemoryUtil.memFree(uploadBuffer)
        floatCount = 0
        vertexCount = 0
        nextLayoutIndex = 0
        rawWrittenFloats = 0
    }
}
