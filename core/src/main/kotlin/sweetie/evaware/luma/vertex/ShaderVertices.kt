package sweetie.evaware.luma.vertex

import org.lwjgl.opengl.GL20
import sweetie.evaware.luma.shader.ShaderInputApi

class ShaderVertices : ShaderInputApi<ShaderVertices>(), AutoCloseable {
    private val layout = VertexLayout()
    private val stream = VertexStream()
    private val writer = VertexWriter(stream)

    fun layout(layout: ShaderVert) = apply {
        this.layout.add(layout)
    }

    override fun onDefineFloat(count: Int, layoutPos: Int, normalized: Boolean) {
        layout.add(ShaderVertType.FLOAT, count, layoutPos, normalized)
    }

    fun requireConfigured() {
        require(layout.size() > 0) { "Shader requires at least one vertex layout" }
    }

    internal fun bindLocations(programId: Int) {
        for (index in 0 until layout.size()) {
            val layoutPos = layout.layoutPos(index)
            GL20.glBindAttribLocation(programId, layoutPos, "a$layoutPos")
        }
    }

    internal fun bind(vao: Int, vbo: Int) {
        layout.bind(vao, vbo)
    }

    fun hasVertices() = stream.hasVertices()

    @Deprecated(
        message = "Avoid boxing and vararg allocation in hot paths; prefer vec2/vec4 or attribute2/attribute4 fast paths"
    )
    fun vert(layoutPos: Int, type: ShaderVertType, count: Int, vararg args: Number) {
        writer.requireIdle()
        stream.put(layout, layoutPos, type, count, args)
    }

    override fun onWriteFloat(count: Int, first: Float, second: Float, third: Float, fourth: Float) {
        when (count) {
            1 -> writer.vec1(layout.strideFloats, first)
            2 -> writer.vec2(layout.strideFloats, first, second)
            3 -> writer.vec3(layout.strideFloats, first, second, third)
            4 -> writer.vec4(layout.strideFloats, first, second, third, fourth)
            else -> error("Unsupported float vertex payload size: $count")
        }
    }

    fun attribute2(layoutPos: Int, first: Float, second: Float) = apply {
        writer.requireIdle()
        stream.putAttribute2(layout, layoutPos, first, second)
    }

    fun attribute3(layoutPos: Int, first: Float, second: Float, third: Float) = apply {
        writer.requireIdle()
        stream.putAttribute3(layout, layoutPos, first, second, third)
    }

    fun attribute4(layoutPos: Int, first: Float, second: Float, third: Float, fourth: Float) = apply {
        writer.requireIdle()
        stream.putAttribute4(layout, layoutPos, first, second, third, fourth)
    }

    internal fun upload(vbo: Int, drawMode: Int) = stream.upload(vbo, drawMode)

    override fun close() {
        writer.reset()
        stream.close()
    }
}
