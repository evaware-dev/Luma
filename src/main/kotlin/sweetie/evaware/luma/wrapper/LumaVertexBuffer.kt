package sweetie.evaware.luma.wrapper

import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.wrapper.api.Clearable
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import sweetie.evaware.luma.wrapper.vertex.ShaderVertType

class LumaVertexBuffer private constructor(
    private val vertexStrideBytes: Int,
    initialByteCapacity: Int,
    @Suppress("UNUSED_PARAMETER") marker: Boolean
) : Clearable, AutoCloseable {
    constructor(floatsPerVertex: Int, initialFloatCapacity: Int = 1024) : this(
        vertexStrideBytes = floatsPerVertex * Float.SIZE_BYTES,
        initialByteCapacity = initialFloatCapacity * Float.SIZE_BYTES,
        marker = true
    )

    companion object {
        fun bytes(vertexStrideBytes: Int, initialByteCapacity: Int = 1024): LumaVertexBuffer {
            require(vertexStrideBytes > 0) { "Vertex stride must be positive" }
            return LumaVertexBuffer(vertexStrideBytes, initialByteCapacity, true)
        }
    }

    private var byteBuffer: ByteBuffer = MemoryUtil.memAlloc(initialByteCapacity)
    private var floatBuffer: FloatBuffer = byteBuffer.asFloatBuffer()
    private var byteCount = 0
    private var vertexCount = 0
    private var closed = false

    fun hasVertices() = vertexCount > 0

    fun vertexCount() = vertexCount

    fun byteCount() = byteCount

    fun floatCount() = byteCount / Float.SIZE_BYTES

    fun putFloat(value: Float) {
        ensureCapacity(byteCount + Float.SIZE_BYTES)
        floatBuffer.put(byteCount / Float.SIZE_BYTES, value)
        byteCount += Float.SIZE_BYTES
    }

    fun putByte(value: Byte) {
        ensureCapacity(byteCount + Byte.SIZE_BYTES)
        byteBuffer.put(byteCount, value)
        byteCount += Byte.SIZE_BYTES
    }

    fun putUByte(value: Int) {
        require(value in 0..0xFF) { "Unsigned byte value out of range: $value" }
        putByte(value.toByte())
    }

    fun putShort(value: Short) {
        ensureCapacity(byteCount + Short.SIZE_BYTES)
        byteBuffer.putShort(byteCount, value)
        byteCount += Short.SIZE_BYTES
    }

    fun putUShort(value: Int) {
        require(value in 0..0xFFFF) { "Unsigned short value out of range: $value" }
        putShort(value.toShort())
    }

    fun putInt(value: Int) {
        ensureCapacity(byteCount + Int.SIZE_BYTES)
        byteBuffer.putInt(byteCount, value)
        byteCount += Int.SIZE_BYTES
    }

    fun putUInt(value: Long) {
        require(value in 0L..0xFFFF_FFFFL) { "Unsigned int value out of range: $value" }
        putInt(value.toInt())
    }

    fun put(type: ShaderVertType, value: Number) {
        when (type) {
            ShaderVertType.FLOAT -> putFloat(value.toFloat())
            ShaderVertType.BYTE -> putByte(value.toByte())
            ShaderVertType.UNSIGNED_BYTE -> putUByte(value.toInt())
            ShaderVertType.SHORT -> putShort(value.toShort())
            ShaderVertType.UNSIGNED_SHORT -> putUShort(value.toInt())
            ShaderVertType.INT -> putInt(value.toInt())
            ShaderVertType.UNSIGNED_INT -> putUInt(value.toLong())
        }
    }

    fun put2(first: Float, second: Float) {
        ensureCapacity(byteCount + 2 * Float.SIZE_BYTES)
        val index = byteCount / Float.SIZE_BYTES
        floatBuffer.put(index, first)
        floatBuffer.put(index + 1, second)
        byteCount += 2 * Float.SIZE_BYTES
    }

    fun put4(first: Float, second: Float, third: Float, fourth: Float) {
        ensureCapacity(byteCount + 4 * Float.SIZE_BYTES)
        val index = byteCount / Float.SIZE_BYTES
        floatBuffer.put(index, first)
        floatBuffer.put(index + 1, second)
        floatBuffer.put(index + 2, third)
        floatBuffer.put(index + 3, fourth)
        byteCount += 4 * Float.SIZE_BYTES
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
        ensureCapacity(byteCount + 14 * Float.SIZE_BYTES)
        val index = byteCount / Float.SIZE_BYTES
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
        byteCount += 14 * Float.SIZE_BYTES
        vertexCount++
    }

    fun completeVertex() {
        require(byteCount % vertexStrideBytes == 0) {
            "Vertex buffer is misaligned: $byteCount bytes for $vertexStrideBytes-byte vertices"
        }
        vertexCount++
    }

    fun byteView(): ByteBuffer {
        byteBuffer.position(0)
        byteBuffer.limit(byteCount)
        return byteBuffer
    }

    override fun clear() {
        byteCount = 0
        vertexCount = 0
        byteBuffer.clear()
        floatBuffer.clear()
    }

    private fun ensureCapacity(requiredBytes: Int) {
        if (requiredBytes <= byteBuffer.capacity()) return
        val nextCapacity = nextCapacity(requiredBytes, byteBuffer.capacity().coerceAtLeast(1))
        byteBuffer = MemoryUtil.memRealloc(byteBuffer, nextCapacity)
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
        byteCount = 0
        vertexCount = 0
    }
}
