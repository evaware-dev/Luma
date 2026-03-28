package sweetie.evaware.luma.vertex

import java.nio.FloatBuffer
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.Clearable

class VertexStream : Clearable, AutoCloseable {
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
        val nextLayoutPos = layouts.layoutPos(nextLayoutIndex)
        val nextType = layouts.type(nextLayoutIndex)
        val nextCount = layouts.count(nextLayoutIndex)

        require(nextLayoutPos == layoutPos) { "Expected layout $nextLayoutPos, got $layoutPos" }
        require(nextType == type) { "Expected layout type $nextType, got $type" }
        require(nextCount == count) { "Expected layout count $nextCount, got $count" }
        require(args.size == count) { "Expected $count args, got ${args.size}" }

        ensureCapacity(floatCount + count)
        for (arg in args) {
            uploadBuffer.put(floatCount++, arg.toFloat())
        }

        advance(layouts)
    }

    fun put2(first: Float, second: Float) {
        ensureCapacity(floatCount + 2)
        uploadBuffer.put(floatCount++, first)
        uploadBuffer.put(floatCount++, second)
        completeVertex()
    }

    fun put6(first: Float, second: Float, third: Float, fourth: Float, fifth: Float, sixth: Float) {
        ensureCapacity(floatCount + 6)
        uploadBuffer.put(floatCount++, first)
        uploadBuffer.put(floatCount++, second)
        uploadBuffer.put(floatCount++, third)
        uploadBuffer.put(floatCount++, fourth)
        uploadBuffer.put(floatCount++, fifth)
        uploadBuffer.put(floatCount++, sixth)
        completeVertex()
    }

    fun put8(first: Float, second: Float, third: Float, fourth: Float, fifth: Float, sixth: Float, seventh: Float, eighth: Float) {
        ensureCapacity(floatCount + 8)
        uploadBuffer.put(floatCount++, first)
        uploadBuffer.put(floatCount++, second)
        uploadBuffer.put(floatCount++, third)
        uploadBuffer.put(floatCount++, fourth)
        uploadBuffer.put(floatCount++, fifth)
        uploadBuffer.put(floatCount++, sixth)
        uploadBuffer.put(floatCount++, seventh)
        uploadBuffer.put(floatCount++, eighth)
        completeVertex()
    }

    fun put10(
        first: Float,
        second: Float,
        third: Float,
        fourth: Float,
        fifth: Float,
        sixth: Float,
        seventh: Float,
        eighth: Float,
        ninth: Float,
        tenth: Float
    ) {
        ensureCapacity(floatCount + 10)
        uploadBuffer.put(floatCount++, first)
        uploadBuffer.put(floatCount++, second)
        uploadBuffer.put(floatCount++, third)
        uploadBuffer.put(floatCount++, fourth)
        uploadBuffer.put(floatCount++, fifth)
        uploadBuffer.put(floatCount++, sixth)
        uploadBuffer.put(floatCount++, seventh)
        uploadBuffer.put(floatCount++, eighth)
        uploadBuffer.put(floatCount++, ninth)
        uploadBuffer.put(floatCount++, tenth)
        completeVertex()
    }

    fun putAttribute2(layouts: VertexLayout, layoutPos: Int, first: Float, second: Float) {
        val nextLayoutPos = layouts.layoutPos(nextLayoutIndex)
        require(nextLayoutPos == layoutPos) { "Expected layout $nextLayoutPos, got $layoutPos" }
        ensureCapacity(floatCount + 2)
        uploadBuffer.put(floatCount++, first)
        uploadBuffer.put(floatCount++, second)
        advance(layouts)
    }

    fun putAttribute4(layouts: VertexLayout, layoutPos: Int, first: Float, second: Float, third: Float, fourth: Float) {
        val nextLayoutPos = layouts.layoutPos(nextLayoutIndex)
        require(nextLayoutPos == layoutPos) { "Expected layout $nextLayoutPos, got $layoutPos" }
        ensureCapacity(floatCount + 4)
        uploadBuffer.put(floatCount++, first)
        uploadBuffer.put(floatCount++, second)
        uploadBuffer.put(floatCount++, third)
        uploadBuffer.put(floatCount++, fourth)
        advance(layouts)
    }

    fun upload(vbo: Int, drawMode: Int): Int {
        if (!hasVertices()) return 0

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
        nextLayoutIndex = 0
        floatCount = 0
        vertexCount = 0
        uploadBuffer.clear()
    }

    private fun ensureCapacity(requiredFloats: Int) {
        if (requiredFloats <= uploadBuffer.capacity()) return
        uploadBuffer = MemoryUtil.memRealloc(uploadBuffer, nextCapacity(requiredFloats, uploadBuffer.capacity()))
        uploadBuffer.clear()
    }

    private fun ensureGpuCapacity(requiredFloats: Int) {
        if (requiredFloats <= gpuFloatCapacity) return
        gpuFloatCapacity = nextCapacity(requiredFloats, gpuFloatCapacity.coerceAtLeast(1))
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, gpuFloatCapacity.toLong() * Float.SIZE_BYTES.toLong(), GL15.GL_STREAM_DRAW)
    }

    private fun nextCapacity(required: Int, current: Int): Int {
        var capacity = current.coerceAtLeast(1)
        while (capacity < required) {
            capacity = capacity shl 1
        }
        return capacity
    }

    override fun close() {
        MemoryUtil.memFree(uploadBuffer)
        uploadBuffer = MemoryUtil.memAllocFloat(INITIAL_FLOAT_CAPACITY)
        gpuFloatCapacity = 0
        clear()
    }
}
