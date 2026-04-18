package sweetie.evaware.luma.vertex

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.Clearable
import sweetie.evaware.luma.api.CloseableResourceBase
import java.nio.FloatBuffer

class VertexStream : CloseableResourceBase(), Clearable {
    companion object {
        private const val INITIAL_FLOAT_CAPACITY = 1024
    }

    private var nextLayoutIndex = 0
    private var floatCount = 0
    private var vertexCount = 0
    private var gpuFloatCapacity = 0
    private var uploadBuffer: FloatBuffer = MemoryUtil.memAllocFloat(INITIAL_FLOAT_CAPACITY)

    fun hasVertices() = vertexCount > 0

    fun put(layouts: VertexLayout, layoutPos: Int, type: ShaderVertType, count: Int, args: Array<out Number>) {
        requireNextLayout(layouts, layoutPos, type, count)
        require(args.size == count) { "Expected $count args, got ${args.size}" }

        ensureCapacity(floatCount + count)
        for (arg in args) {
            uploadBuffer.put(floatCount++, arg.toFloat())
        }

        advance(layouts)
    }

    fun putAttribute2(layouts: VertexLayout, layoutPos: Int, first: Float, second: Float) {
        requireNextLayout(layouts, layoutPos, ShaderVertType.FLOAT, 2)
        ensureCapacity(floatCount + 2)
        uploadBuffer.put(floatCount++, first)
        uploadBuffer.put(floatCount++, second)
        advance(layouts)
    }

    fun putAttribute3(layouts: VertexLayout, layoutPos: Int, first: Float, second: Float, third: Float) {
        requireNextLayout(layouts, layoutPos, ShaderVertType.FLOAT, 3)
        ensureCapacity(floatCount + 3)
        uploadBuffer.put(floatCount++, first)
        uploadBuffer.put(floatCount++, second)
        uploadBuffer.put(floatCount++, third)
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
        uploadBuffer.put(floatCount++, first)
        uploadBuffer.put(floatCount++, second)
        uploadBuffer.put(floatCount++, third)
        uploadBuffer.put(floatCount++, fourth)
        advance(layouts)
    }

    fun upload(vbo: Int, drawMode: Int): Int {
        if (!hasVertices()) return 0
        requireOpen()

        uploadBuffer.limit(floatCount)
        uploadBuffer.position(0)
        Luma.bindArrayBuffer(vbo)
        ensureGpuCapacity(floatCount)
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, uploadBuffer)
        GL11.glDrawArrays(drawMode, 0, vertexCount)
        val uploadedVertices = vertexCount
        clear()
        return uploadedVertices
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

    private fun ensureGpuCapacity(requiredFloats: Int) {
        if (requiredFloats <= gpuFloatCapacity) return
        gpuFloatCapacity = nextCapacity(requiredFloats, gpuFloatCapacity.coerceAtLeast(1))
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, gpuFloatCapacity.toLong() * Float.SIZE_BYTES.toLong(), GL15.GL_STREAM_DRAW)
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

    override fun close() {
        if (!markClosed()) return
        MemoryUtil.memFree(uploadBuffer)
        gpuFloatCapacity = 0
        floatCount = 0
        vertexCount = 0
        nextLayoutIndex = 0
    }
}
