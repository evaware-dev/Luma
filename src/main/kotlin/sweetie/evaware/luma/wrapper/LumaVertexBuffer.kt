package sweetie.evaware.luma.wrapper

import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.wrapper.api.Clearable
import java.nio.ByteBuffer
import java.nio.FloatBuffer

class LumaVertexBuffer(
    private val floatsPerVertex: Int,
    initialFloatCapacity: Int = 1024
) : Clearable, AutoCloseable {
    private var byteBuffer: ByteBuffer = MemoryUtil.memAlloc(initialFloatCapacity * Float.SIZE_BYTES)
    private var floatBuffer: FloatBuffer = byteBuffer.asFloatBuffer()
    private var floatCount = 0
    private var vertexCount = 0
    private var closed = false

    fun hasVertices() = vertexCount > 0

    fun vertexCount() = vertexCount

    fun floatCount() = floatCount

    fun put2(first: Float, second: Float) {
        ensureCapacity(floatCount + 2)
        floatBuffer.put(floatCount++, first)
        floatBuffer.put(floatCount++, second)
    }

    fun put4(first: Float, second: Float, third: Float, fourth: Float) {
        ensureCapacity(floatCount + 4)
        floatBuffer.put(floatCount++, first)
        floatBuffer.put(floatCount++, second)
        floatBuffer.put(floatCount++, third)
        floatBuffer.put(floatCount++, fourth)
    }

    internal fun putVertex14(
        first: Float,
        second: Float,
        third: Float,
        fourth: Float,
        fifth: Float,
        sixth: Float,
        seventh: Float,
        eighth: Float,
        ninth: Float,
        tenth: Float,
        eleventh: Float,
        twelfth: Float,
        thirteenth: Float,
        fourteenth: Float
    ) {
        ensureCapacity(floatCount + 14)
        val index = floatCount
        floatBuffer.put(index, first)
        floatBuffer.put(index + 1, second)
        floatBuffer.put(index + 2, third)
        floatBuffer.put(index + 3, fourth)
        floatBuffer.put(index + 4, fifth)
        floatBuffer.put(index + 5, sixth)
        floatBuffer.put(index + 6, seventh)
        floatBuffer.put(index + 7, eighth)
        floatBuffer.put(index + 8, ninth)
        floatBuffer.put(index + 9, tenth)
        floatBuffer.put(index + 10, eleventh)
        floatBuffer.put(index + 11, twelfth)
        floatBuffer.put(index + 12, thirteenth)
        floatBuffer.put(index + 13, fourteenth)
        floatCount += 14
        vertexCount++
    }

    fun completeVertex() {
        require(floatCount % floatsPerVertex == 0) {
            "Vertex buffer is misaligned: $floatCount floats for $floatsPerVertex-float vertices"
        }
        vertexCount++
    }

    fun byteView(): ByteBuffer {
        byteBuffer.position(0)
        byteBuffer.limit(floatCount * Float.SIZE_BYTES)
        return byteBuffer
    }

    override fun clear() {
        floatCount = 0
        vertexCount = 0
        floatBuffer.clear()
    }

    private fun ensureCapacity(requiredFloats: Int) {
        if (requiredFloats <= floatBuffer.capacity()) return
        val nextCapacity = nextCapacity(requiredFloats, floatBuffer.capacity().coerceAtLeast(1))
        byteBuffer = MemoryUtil.memRealloc(byteBuffer, nextCapacity * Float.SIZE_BYTES)
        floatBuffer = byteBuffer.asFloatBuffer()
    }

    private fun nextCapacity(required: Int, current: Int): Int {
        var capacity = current
        while (capacity < required) {
            capacity = capacity shl 1
        }
        return capacity
    }

    override fun close() {
        if (closed) return
        MemoryUtil.memFree(byteBuffer)
        closed = true
        floatCount = 0
        vertexCount = 0
    }
}
