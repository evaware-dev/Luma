package sweetie.evaware.luma.vertex

class PreparedVertices internal constructor(
    internal val layout: VertexLayout,
    initialVertexCapacity: Int
) : AutoCloseable {
    internal val stream = VertexStream()
    private val writer = VertexWriter(stream)

    @Volatile
    private var sealed = false
    private var closed = false

    val vertexCount get() = stream.vertexCount
    val isSealed get() = sealed

    init {
        require(initialVertexCapacity >= 0) { "Initial vertex capacity must not be negative" }
        if (initialVertexCapacity > 0) stream.reserveVertices(layout, initialVertexCapacity)
    }

    fun vec1(value: Float) = apply {
        requireWritable()
        writer.vec1(layout.strideFloats, value)
    }

    fun vec2(first: Float, second: Float) = apply {
        requireWritable()
        writer.vec2(layout.strideFloats, first, second)
    }

    fun vec3(first: Float, second: Float, third: Float) = apply {
        requireWritable()
        writer.vec3(layout.strideFloats, first, second, third)
    }

    fun vec4(first: Float, second: Float, third: Float, fourth: Float) = apply {
        requireWritable()
        writer.vec4(layout.strideFloats, first, second, third, fourth)
    }

    fun attribute2(layoutPos: Int, first: Float, second: Float) = apply {
        requireWritable()
        writer.requireIdle()
        stream.putAttribute2(layout, layoutPos, first, second)
    }

    fun attribute3(layoutPos: Int, first: Float, second: Float, third: Float) = apply {
        requireWritable()
        writer.requireIdle()
        stream.putAttribute3(layout, layoutPos, first, second, third)
    }

    fun attribute4(layoutPos: Int, first: Float, second: Float, third: Float, fourth: Float) = apply {
        requireWritable()
        writer.requireIdle()
        stream.putAttribute4(layout, layoutPos, first, second, third, fourth)
    }

    fun reserveVertices(count: Int) = apply {
        requireWritable()
        require(count >= 0) { "Vertex capacity must not be negative" }
        stream.reserveVertices(layout, count)
    }

    fun seal() = apply {
        requireOpen()
        writer.requireIdle()
        stream.requireVertexBoundary()
        sealed = true
    }

    fun clear() = apply {
        requireOpen()
        sealed = false
        writer.reset()
        stream.clear()
    }

    internal fun requireDrawable(expectedLayout: VertexLayout) {
        requireOpen()
        check(sealed) { "Prepared vertices must be sealed before drawing" }
        require(layout.isCompatibleWith(expectedLayout)) { "Prepared vertex layout does not match the shader layout" }
    }

    private fun requireWritable() {
        requireOpen()
        check(!sealed) { "Prepared vertices are sealed; clear them before writing again" }
    }

    private fun requireOpen() {
        check(!closed) { "Prepared vertices are closed" }
    }

    override fun close() {
        if (closed) return
        closed = true
        sealed = false
        writer.reset()
        stream.close()
    }
}
