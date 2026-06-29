package sweetie.evaware.luma.vertex

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

    var strideFloats = 0
        private set

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
        strideFloats += count
        size++
    }

    fun type(index: Int) = types[index]!!

    fun count(index: Int) = counts[index]

    fun layoutPos(index: Int) = layoutPositions[index]

    fun normalized(index: Int) = normalizedFlags[index]

    fun byteSize(index: Int) = byteSizes[index]

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
