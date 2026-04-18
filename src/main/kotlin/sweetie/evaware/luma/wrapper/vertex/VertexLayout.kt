package sweetie.evaware.luma.wrapper.vertex

import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.opengl.OpenGlMappings

class VertexLayout {
    companion object {
        private const val INITIAL_CAPACITY = 8
    }

    private var size = 0
    private var types = arrayOfNulls<ShaderVertType>(INITIAL_CAPACITY)
    private var counts = IntArray(INITIAL_CAPACITY)
    private var layoutPositions = IntArray(INITIAL_CAPACITY)
    private var normalizedFlags = BooleanArray(INITIAL_CAPACITY)
    private var byteSizes = IntArray(INITIAL_CAPACITY)

    var strideBytes = 0
        private set

    @Deprecated("Use strideBytes", ReplaceWith("strideBytes"))
    val strideFloats get() = strideBytes / Float.SIZE_BYTES

    fun add(layout: ShaderVert) {
        add(layout.type, layout.count, layout.layoutPos, layout.normalized)
    }

    fun add(type: ShaderVertType, count: Int, layoutPos: Int, normalized: Boolean = false) {
        ensureCapacity(size + 1)
        types[size] = type
        counts[size] = count
        layoutPositions[size] = layoutPos
        normalizedFlags[size] = normalized
        byteSizes[size] = count * type.byteSize
        strideBytes += byteSizes[size]
        size++
    }

    fun bind(vao: Int, vbo: Int) {
        Luma.bindVertexArray(vao)
        Luma.bindArrayBuffer(vbo)

        var offset = 0L
        val resolvedStrideBytes = strideBytes
        for (index in 0 until size) {
            val layoutPos = layoutPositions[index]
            GL20.glEnableVertexAttribArray(layoutPos)
            val type = types[index]!!
            if (type.integer && !normalizedFlags[index]) {
                GL30.glVertexAttribIPointer(
                    layoutPos,
                    counts[index],
                    OpenGlMappings.vertexType(type),
                    resolvedStrideBytes,
                    offset
                )
            } else {
                GL20.glVertexAttribPointer(
                    layoutPos,
                    counts[index],
                    OpenGlMappings.vertexType(type),
                    normalizedFlags[index],
                    resolvedStrideBytes,
                    offset
                )
            }
            offset += byteSizes[index].toLong()
        }

        Luma.bindVertexArray(0)
    }

    fun type(index: Int) = types[index]!!

    fun count(index: Int) = counts[index]

    fun layoutPos(index: Int) = layoutPositions[index]

    fun size() = size

    private fun ensureCapacity(required: Int) {
        if (required <= counts.size) return

        val nextSize = nextCapacity(required, counts.size)
        types = types.copyOf(nextSize)
        counts = counts.copyOf(nextSize)
        layoutPositions = layoutPositions.copyOf(nextSize)
        normalizedFlags = normalizedFlags.copyOf(nextSize)
        byteSizes = byteSizes.copyOf(nextSize)
    }

    private fun nextCapacity(required: Int, current: Int): Int {
        var capacity = current.coerceAtLeast(1)
        while (capacity < required) {
            capacity = capacity shl 1
        }
        return capacity
    }
}
