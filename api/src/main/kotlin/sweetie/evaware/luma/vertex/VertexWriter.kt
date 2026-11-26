package sweetie.evaware.luma.vertex

internal class VertexWriter(private val stream: VertexStream) {
    fun vec1(strideFloats: Int, value: Float) = apply {
        stream.writeVec1(strideFloats, value)
    }

    fun vec2(strideFloats: Int, first: Float, second: Float) = apply {
        stream.writeVec2(strideFloats, first, second)
    }

    fun vec3(strideFloats: Int, first: Float, second: Float, third: Float) = apply {
        stream.writeVec3(strideFloats, first, second, third)
    }

    fun vec4(strideFloats: Int, first: Float, second: Float, third: Float, fourth: Float) = apply {
        stream.writeVec4(strideFloats, first, second, third, fourth)
    }

    fun requireIdle() {
        stream.requireRawIdle()
    }

    fun reset() {
        stream.resetRawWriter()
    }
}
