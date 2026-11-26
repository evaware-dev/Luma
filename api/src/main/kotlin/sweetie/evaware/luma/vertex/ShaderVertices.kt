package sweetie.evaware.luma.vertex

import sweetie.evaware.luma.shader.ShaderInputApi

class ShaderVertices : ShaderInputApi<ShaderVertices>(), AutoCloseable {
    val layout = VertexLayout()
    val stream = VertexStream()

    fun layout(layout: ShaderVert) = apply {
        this.layout.add(layout)
    }

    fun instanced(baseVertexCount: Int = 6) = apply {
        layout.markInstanced(baseVertexCount)
    }

    override fun onDefineFloat(count: Int, layoutPos: Int, normalized: Boolean) {
        layout.add(ShaderVertType.FLOAT, count, layoutPos, normalized)
    }

    fun requireConfigured() {
        require(layout.size() > 0) { "Shader requires at least one vertex layout" }
    }

    fun hasVertices() = stream.hasVertices()

    override fun float1(value: Float): ShaderVertices {
        stream.writeVec1(layout.strideFloats, value)
        return this
    }

    override fun vec1(value: Float): ShaderVertices {
        stream.writeVec1(layout.strideFloats, value)
        return this
    }

    override fun vec2(first: Float, second: Float): ShaderVertices {
        stream.writeVec2(layout.strideFloats, first, second)
        return this
    }

    override fun vec3(first: Float, second: Float, third: Float): ShaderVertices {
        stream.writeVec3(layout.strideFloats, first, second, third)
        return this
    }

    override fun vec4(first: Float, second: Float, third: Float, fourth: Float): ShaderVertices {
        stream.writeVec4(layout.strideFloats, first, second, third, fourth)
        return this
    }

    override fun onWriteFloat(count: Int, first: Float, second: Float, third: Float, fourth: Float) {
        when (count) {
            1 -> stream.writeVec1(layout.strideFloats, first)
            2 -> stream.writeVec2(layout.strideFloats, first, second)
            3 -> stream.writeVec3(layout.strideFloats, first, second, third)
            4 -> stream.writeVec4(layout.strideFloats, first, second, third, fourth)
            else -> error("Unsupported float vertex payload size: $count")
        }
    }

    fun attribute2(layoutPos: Int, first: Float, second: Float) = apply {
        stream.requireRawIdle()
        stream.putAttribute2(layout, layoutPos, first, second)
    }

    fun attribute3(layoutPos: Int, first: Float, second: Float, third: Float) = apply {
        stream.requireRawIdle()
        stream.putAttribute3(layout, layoutPos, first, second, third)
    }

    fun attribute4(layoutPos: Int, first: Float, second: Float, third: Float, fourth: Float) = apply {
        stream.requireRawIdle()
        stream.putAttribute4(layout, layoutPos, first, second, third, fourth)
    }

    fun reserveVertices(count: Int) = apply {
        stream.reserveVertices(layout, count)
    }

    override fun close() {
        stream.close()
    }
}
