package sweetie.evaware.luma.vertex

class VertexInputLayout {
    companion object {
        private const val INITIAL_CAPACITY = 2
    }

    private var layouts = arrayOfNulls<VertexLayout>(INITIAL_CAPACITY)
    private var stepRates = IntArray(INITIAL_CAPACITY)
    private var size = 0

    fun binding(layout: VertexLayout, stepRate: Int = 0) = apply {
        require(stepRate >= 0) { "Vertex step rate must not be negative" }
        ensureCapacity(size + 1)
        layouts[size] = layout.snapshot()
        stepRates[size] = stepRate
        size++
    }

    fun layout(binding: Int): VertexLayout {
        require(binding in 0 until size) { "Unknown vertex binding $binding" }
        return layouts[binding]!!
    }

    fun stepRate(binding: Int): Int {
        require(binding in 0 until size) { "Unknown vertex binding $binding" }
        return stepRates[binding]
    }

    fun size(): Int = size

    internal fun snapshot() = VertexInputLayout().also { copy ->
        for (binding in 0 until size) copy.binding(layout(binding), stepRate(binding))
    }

    private fun ensureCapacity(required: Int) {
        if (required <= layouts.size) return
        val capacity = maxOf(required, layouts.size shl 1)
        layouts = layouts.copyOf(capacity)
        stepRates = stepRates.copyOf(capacity)
    }
}
