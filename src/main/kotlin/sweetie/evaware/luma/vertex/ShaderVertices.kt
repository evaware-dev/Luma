package sweetie.evaware.luma.vertex

import org.lwjgl.opengl.GL20

class ShaderVertices : AutoCloseable {
    private val layout = VertexLayout()
    private val stream = VertexStream()

    fun layout(layout: ShaderVert) = apply {
        this.layout.add(layout)
    }

    fun float(count: Int, layoutPos: Int, normalized: Boolean = false) = apply {
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
        message = "Avoid boxing and vararg allocation in hot paths; prefer vertex(...) or attribute2/attribute4 fast paths"
    )
    fun vert(layoutPos: Int, type: ShaderVertType, count: Int, vararg args: Number) {
        stream.put(layout, layoutPos, type, count, args)
    }

    fun vertex(first: Float, second: Float) {
        stream.put2(first, second)
    }

    fun vertex(first: Float, second: Float, third: Float, fourth: Float, fifth: Float, sixth: Float) {
        stream.put6(first, second, third, fourth, fifth, sixth)
    }

    fun vertex(
        first: Float,
        second: Float,
        third: Float,
        fourth: Float,
        fifth: Float,
        sixth: Float,
        seventh: Float,
        eighth: Float
    ) {
        stream.put8(first, second, third, fourth, fifth, sixth, seventh, eighth)
    }

    fun vertex(
        first: Float,
        second: Float,
        third: Float,
        fourth: Float,
        fifth: Float,
        sixth: Float,
        seventh: Float,
        eighth: Float,
        ninth: Float,
        tenth: Float
    ) {
        stream.put10(first, second, third, fourth, fifth, sixth, seventh, eighth, ninth, tenth)
    }

    fun attribute2(layoutPos: Int, first: Float, second: Float) {
        stream.putAttribute2(layout, layoutPos, first, second)
    }

    fun attribute4(layoutPos: Int, first: Float, second: Float, third: Float, fourth: Float) {
        stream.putAttribute4(layout, layoutPos, first, second, third, fourth)
    }

    internal fun upload(vbo: Int, drawMode: Int) = stream.upload(vbo, drawMode)

    override fun close() {
        stream.close()
    }
}
