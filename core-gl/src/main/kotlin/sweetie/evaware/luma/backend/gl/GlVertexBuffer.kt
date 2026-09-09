package sweetie.evaware.luma.backend.gl

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL33
import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.vertex.ShaderVertType
import sweetie.evaware.luma.vertex.VertexLayout
import java.nio.FloatBuffer

private val ShaderVertType.glType: Int
    get() = when (this) {
        ShaderVertType.FLOAT32 -> GL11.GL_FLOAT
        ShaderVertType.FLOAT16 -> GL30.GL_HALF_FLOAT
        ShaderVertType.INT8 -> GL11.GL_BYTE
        ShaderVertType.UINT8 -> GL11.GL_UNSIGNED_BYTE
        ShaderVertType.INT16 -> GL11.GL_SHORT
        ShaderVertType.UINT16 -> GL11.GL_UNSIGNED_SHORT
        ShaderVertType.INT32 -> GL11.GL_INT
        ShaderVertType.UINT32 -> GL11.GL_UNSIGNED_INT
    }

class GlVertexBuffer(val layout: VertexLayout) : AutoCloseable {
    val vao = GL30.glGenVertexArrays()
    val vbo = GL15.glGenBuffers()
    var capacityFloats = 0
        private set

    private var quadIndexBuffer = 0
    private var quadIndexCapacity = 0

    init {
        val previousVertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
        val previousArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
        try {
            GL30.glBindVertexArray(vao)
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo)
            setupVertexAttributes(layout)
        } finally {
            GL30.glBindVertexArray(previousVertexArray)
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousArrayBuffer)
        }
    }

    fun bind() {
        GL30.glBindVertexArray(vao)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo)
    }

    fun upload(vertices: FloatBuffer) {
        vertices.position(0)
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STREAM_DRAW)
        capacityFloats = vertices.limit()
    }

    fun bindQuadIndices(vertexCount: Int): Int {
        val quadCount = vertexCount / 4
        val requiredIndices = quadCount * 6

        if (quadIndexBuffer == 0) {
            quadIndexBuffer = GL15.glGenBuffers()
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, quadIndexBuffer)
        }

        if (requiredIndices > quadIndexCapacity) {
            quadIndexCapacity = nextIndexCapacity(requiredIndices)
            val quads = quadIndexCapacity / 6
            val indices = MemoryUtil.memAllocInt(quads * 6)
            try {
                for (quad in 0 until quads) {
                    val base = quad * 4
                    indices.put(base).put(base + 1).put(base + 2)
                    indices.put(base + 2).put(base + 3).put(base)
                }
                indices.flip()
                GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices, GL15.GL_STATIC_DRAW)
            } finally {
                MemoryUtil.memFree(indices)
            }
        }

        return requiredIndices
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
            if (layout.instanced) {
                GL33.glVertexAttribDivisor(layoutPos, 1)
            }
            offset += layout.byteSize(index).toLong()
        }
    }

    private fun nextIndexCapacity(required: Int): Int {
        var cap = quadIndexCapacity.coerceAtLeast(6 * 256)
        while (cap < required) {
            cap = cap shl 1
        }
        return cap
    }

    override fun close() {
        GL15.glDeleteBuffers(vbo)
        if (quadIndexBuffer != 0) {
            GL15.glDeleteBuffers(quadIndexBuffer)
        }
        GL30.glDeleteVertexArrays(vao)
    }
}
