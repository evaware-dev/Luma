package sweetie.evaware.luma.backend.gl

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import sweetie.evaware.luma.vertex.ShaderVertType
import sweetie.evaware.luma.vertex.VertexLayout
import java.nio.FloatBuffer

private val ShaderVertType.glType: Int
    get() = when (this) {
        ShaderVertType.FLOAT -> GL11.GL_FLOAT
    }

class GlVertexBuffer(val layout: VertexLayout) : AutoCloseable {
    val vao = GL30.glGenVertexArrays()
    val vbo = GL15.glGenBuffers()
    var capacityFloats = 0
        private set

    init {
        GL30.glBindVertexArray(vao)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo)
        setupVertexAttributes(layout)
        GL30.glBindVertexArray(0)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0)
    }

    fun bind() {
        GL30.glBindVertexArray(vao)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo)
    }

    fun upload(vertices: FloatBuffer) {
        val floatCount = vertices.limit()
        vertices.position(0)

        if (floatCount > capacityFloats) {
            capacityFloats = nextCapacity(floatCount, capacityFloats)
            GL15.glBufferData(
                GL15.GL_ARRAY_BUFFER,
                capacityFloats.toLong() * Float.SIZE_BYTES.toLong(),
                GL15.GL_STREAM_DRAW
            )
        }

        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, vertices)
    }

    private fun setupVertexAttributes(layout: VertexLayout) {
        var offset = 0L
        val strideBytes = layout.strideFloats * Float.SIZE_BYTES
        for (index in 0 until layout.size()) {
            val layoutPos = layout.layoutPos(index)
            GL20.glEnableVertexAttribArray(layoutPos)
            GL20.glVertexAttribPointer(
                layoutPos,
                layout.count(index),
                layout.type(index).glType,
                layout.normalized(index),
                strideBytes,
                offset
            )
            offset += layout.byteSize(index).toLong()
        }
    }

    private fun nextCapacity(required: Int, current: Int): Int {
        var cap = current.coerceAtLeast(1024)
        while (cap < required) {
            cap = cap shl 1
        }
        return cap
    }

    override fun close() {
        GL15.glDeleteBuffers(vbo)
        GL30.glDeleteVertexArrays(vao)
    }
}
