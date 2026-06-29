package sweetie.evaware.luma.vertex

import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.api.Clearable
import sweetie.evaware.luma.api.CloseableResourceBase
import java.nio.FloatBuffer

class VertexStream : CloseableResourceBase(), Clearable {
    companion object {
        private const val INITIAL_FLOAT_CAPACITY = 1024
    }

    private var nextLayoutIndex = 0
    
    var floatCount = 0
        internal set
        
    var vertexCount = 0
        internal set

    var uploadBuffer: FloatBuffer = MemoryUtil.memAllocFloat(INITIAL_FLOAT_CAPACITY)
        internal set

    fun hasVertices() = vertexCount > 0

    fun flipForUpload(): FloatBuffer {
        uploadBuffer.limit(floatCount)
        uploadBuffer.position(0)
        return uploadBuffer
    }

    fun putAttribute2(layouts: VertexLayout, layoutPos: Int, first: Float, second: Float) {
        requireNextLayout(layouts, layoutPos, ShaderVertType.FLOAT, 2)
        ensureCapacity(floatCount + 2)
        val buffer = uploadBuffer
        var offset = floatCount
        buffer.put(offset++, first)
        buffer.put(offset++, second)
        floatCount = offset
        advance(layouts)
    }

    fun putAttribute3(layouts: VertexLayout, layoutPos: Int, first: Float, second: Float, third: Float) {
        requireNextLayout(layouts, layoutPos, ShaderVertType.FLOAT, 3)
        ensureCapacity(floatCount + 3)
        val buffer = uploadBuffer
        var offset = floatCount
        buffer.put(offset++, first)
        buffer.put(offset++, second)
        buffer.put(offset++, third)
        floatCount = offset
        advance(layouts)
    }

    internal fun requireVertexBoundary() {
        check(nextLayoutIndex == 0) { "Cannot switch vertex write modes in the middle of a vertex" }
    }

    internal fun putRaw1(value: Float) {
        ensureCapacity(floatCount + 1)
        uploadBuffer.put(floatCount++, value)
    }

    internal fun putRaw2(first: Float, second: Float) {
        ensureCapacity(floatCount + 2)
        val buffer = uploadBuffer
        var offset = floatCount
        buffer.put(offset++, first)
        buffer.put(offset++, second)
        floatCount = offset
    }

    internal fun putRaw3(first: Float, second: Float, third: Float) {
        ensureCapacity(floatCount + 3)
        val buffer = uploadBuffer
        var offset = floatCount
        buffer.put(offset++, first)
        buffer.put(offset++, second)
        buffer.put(offset++, third)
        floatCount = offset
    }

    internal fun putRaw4(first: Float, second: Float, third: Float, fourth: Float) {
        ensureCapacity(floatCount + 4)
        val buffer = uploadBuffer
        var offset = floatCount
        buffer.put(offset++, first)
        buffer.put(offset++, second)
        buffer.put(offset++, third)
        buffer.put(offset++, fourth)
        floatCount = offset
    }

    internal fun completeRawVertex() {
        completeVertex()
    }

    fun putAttribute4(layouts: VertexLayout, layoutPos: Int, first: Float, second: Float, third: Float, fourth: Float) {
        requireNextLayout(layouts, layoutPos, ShaderVertType.FLOAT, 4)
        ensureCapacity(floatCount + 4)
        val buffer = uploadBuffer
        var offset = floatCount
        buffer.put(offset++, first)
        buffer.put(offset++, second)
        buffer.put(offset++, third)
        buffer.put(offset++, fourth)
        floatCount = offset
        advance(layouts)
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
        floatCount = 0
        vertexCount = 0
        uploadBuffer.clear()
    }

    private fun ensureCapacity(requiredFloats: Int) {
        requireOpen()
        if (requiredFloats <= uploadBuffer.capacity()) return
        uploadBuffer = MemoryUtil.memRealloc(uploadBuffer, nextCapacity(requiredFloats, uploadBuffer.capacity()))
        uploadBuffer.clear()
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
        ensureCapacity(count * layouts.strideFloats)
    }

    override fun close() {
        if (!markClosed()) return
        MemoryUtil.memFree(uploadBuffer)
        floatCount = 0
        vertexCount = 0
        nextLayoutIndex = 0
    }
}
