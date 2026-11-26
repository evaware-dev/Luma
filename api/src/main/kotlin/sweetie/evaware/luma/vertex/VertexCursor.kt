package sweetie.evaware.luma.vertex

import java.lang.foreign.MemorySegment
import org.lwjgl.system.MemoryUtil

/**
 * Reusable zero-allocation direct native memory cursor for writing vertices.
 *
 * Capacity is validated once upfront when the write begins; each [vec1]/[vec2]/[vec3]/[vec4]
 * performs a single register bounds check and writes directly to mapped GPU or staging memory
 * without intermediate copies.
 */
class VertexCursor {
    @JvmField
    @PublishedApi
    internal var address: Long = 0L

    @JvmField
    @PublishedApi
    internal var baseAddress: Long = 0L

    @JvmField
    @PublishedApi
    internal var endAddress: Long = 0L

    @JvmField
    @PublishedApi
    internal var strideBytes: Int = 0

    @JvmField
    var token: Long = 0L

    @JvmField
    @PublishedApi
    internal var segment: MemorySegment? = null

    @JvmField
    @PublishedApi
    internal var segmentBaseAddress: Long = 0L

    val writtenBytes: Long
        get() = address - baseAddress

    val remainingBytes: Long
        get() = endAddress - address

    val writtenVertices: Int
        get() = if (strideBytes > 0) ((address - baseAddress) / strideBytes).toInt() else 0

    fun init(
        baseAddress: Long,
        byteLength: Long,
        strideBytes: Int,
        token: Long = 0L,
        segment: MemorySegment? = null,
        segmentBaseAddress: Long = 0L
    ) {
        require(baseAddress != 0L) { "Vertex cursor base address must be non-null" }
        require(byteLength >= 0L) { "Vertex cursor byte length must not be negative: $byteLength" }
        require(strideBytes > 0) { "Vertex cursor stride must be positive: $strideBytes" }
        this.baseAddress = baseAddress
        this.address = baseAddress
        this.endAddress = baseAddress + byteLength
        this.strideBytes = strideBytes
        this.token = token
        this.segment = segment
        this.segmentBaseAddress = segmentBaseAddress
    }

    fun float1(value: Float): VertexCursor = vec1(value)

    fun vec1(value: Float): VertexCursor {
        val a = address
        val next = a + 4L
        if (next > endAddress) throwOverflow(4)
        MemoryUtil.memPutFloat(a, value)
        address = next
        return this
    }

    fun vec2(first: Float, second: Float): VertexCursor {
        val a = address
        val next = a + 8L
        if (next > endAddress) throwOverflow(8)
        MemoryUtil.memPutFloat(a, first)
        MemoryUtil.memPutFloat(a + 4L, second)
        address = next
        return this
    }

    fun vec3(first: Float, second: Float, third: Float): VertexCursor {
        val a = address
        val next = a + 12L
        if (next > endAddress) throwOverflow(12)
        MemoryUtil.memPutFloat(a, first)
        MemoryUtil.memPutFloat(a + 4L, second)
        MemoryUtil.memPutFloat(a + 8L, third)
        address = next
        return this
    }

    fun vec4(first: Float, second: Float, third: Float, fourth: Float): VertexCursor {
        val a = address
        val next = a + 16L
        if (next > endAddress) throwOverflow(16)
        MemoryUtil.memPutFloat(a, first)
        MemoryUtil.memPutFloat(a + 4L, second)
        MemoryUtil.memPutFloat(a + 8L, third)
        MemoryUtil.memPutFloat(a + 12L, fourth)
        address = next
        return this
    }

    @PublishedApi
    internal fun finishVertices(): Int {
        val written = address - baseAddress
        val capacity = endAddress - baseAddress
        check(written in 0L..capacity) {
            "Vertex cursor out of bounds: written=$written bytes, capacity=$capacity bytes"
        }
        val stride = strideBytes
        check(stride > 0 && written % stride == 0L) {
            "Incomplete vertex written: $written bytes is not a multiple of stride $stride bytes"
        }
        return (written / stride).toInt()
    }

    internal fun copyFrom(srcSegment: MemorySegment, srcOffsetBytes: Long, srcBaseAddress: Long, byteLength: Long) {
        val a = address
        val next = a + byteLength
        if (next > endAddress) throwOverflow(byteLength.toInt())
        val dstSeg = segment
        if (dstSeg != null) {
            MemorySegment.copy(srcSegment, srcOffsetBytes, dstSeg, a - segmentBaseAddress, byteLength)
        } else {
            var src = srcBaseAddress + srcOffsetBytes
            var dst = a
            val end = a + byteLength
            while (dst + 8L <= end) {
                MemoryUtil.memPutLong(dst, MemoryUtil.memGetLong(src))
                src += 8L
                dst += 8L
            }
            while (dst + 4L <= end) {
                MemoryUtil.memPutInt(dst, MemoryUtil.memGetInt(src))
                src += 4L
                dst += 4L
            }
            while (dst < end) {
                MemoryUtil.memPutByte(dst++, MemoryUtil.memGetByte(src++))
            }
        }
        address = next
    }

    @PublishedApi
    internal fun reset() {
        address = 0L
        baseAddress = 0L
        endAddress = 0L
        strideBytes = 0
        token = 0L
        segment = null
        segmentBaseAddress = 0L
    }

    @PublishedApi
    internal fun throwOverflow(bytes: Int): Nothing {
        error(
            "Vertex cursor overflow: attempted to write $bytes bytes with ${endAddress - address} bytes remaining " +
                "(capacity=${endAddress - baseAddress} bytes)"
        )
    }
}
