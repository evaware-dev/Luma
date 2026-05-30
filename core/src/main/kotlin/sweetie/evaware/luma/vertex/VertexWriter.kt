package sweetie.evaware.luma.vertex

internal class VertexWriter(private val stream: VertexStream) {
    private var writtenFloats = 0

    fun vec1(strideFloats: Int, value: Float) = apply {
        requireVertexSpace(strideFloats, 1)
        stream.putRaw1(value)
        advance(strideFloats, 1)
    }

    fun vec2(strideFloats: Int, first: Float, second: Float) = apply {
        requireVertexSpace(strideFloats, 2)
        stream.putRaw2(first, second)
        advance(strideFloats, 2)
    }

    fun vec3(strideFloats: Int, first: Float, second: Float, third: Float) = apply {
        requireVertexSpace(strideFloats, 3)
        stream.putRaw3(first, second, third)
        advance(strideFloats, 3)
    }

    fun vec4(strideFloats: Int, first: Float, second: Float, third: Float, fourth: Float) = apply {
        requireVertexSpace(strideFloats, 4)
        stream.putRaw4(first, second, third, fourth)
        advance(strideFloats, 4)
    }

    fun requireIdle() {
        check(writtenFloats == 0) { "Cannot switch vertex write modes in the middle of a vertex" }
    }

    fun reset() {
        writtenFloats = 0
    }

    private fun requireVertexSpace(strideFloats: Int, count: Int) {
        if (writtenFloats == 0) {
            require(strideFloats > 0) { "Shader requires at least one vertex layout" }
            stream.requireVertexBoundary()
        }
        if (writtenFloats + count > strideFloats) {
            throwStrideOverflow(writtenFloats, count, strideFloats)
        }
    }

    private fun throwStrideOverflow(written: Int, count: Int, stride: Int): Nothing {
        error("Vertex stride overflow: ${written + count} floats written, stride is $stride")
    }

    private fun advance(strideFloats: Int, count: Int) {
        writtenFloats += count
        if (writtenFloats == strideFloats) {
            stream.completeRawVertex()
            writtenFloats = 0
        }
    }
}
