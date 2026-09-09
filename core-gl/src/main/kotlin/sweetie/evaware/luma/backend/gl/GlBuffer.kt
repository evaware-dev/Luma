package sweetie.evaware.luma.backend.gl

import java.nio.ByteBuffer
import org.lwjgl.opengl.GL15
import sweetie.evaware.luma.api.BufferUsage
import sweetie.evaware.luma.api.IndexBufferHandle
import sweetie.evaware.luma.api.IndexType
import sweetie.evaware.luma.api.VertexBufferHandle

internal sealed class GlBuffer(
    val id: Int,
    val sizeBytes: Long,
    val usage: BufferUsage
) : AutoCloseable {
    private var closed = false

    fun requireOpen() = check(!closed) { "Buffer is closed" }

    fun update(offsetBytes: Long, data: ByteBuffer) {
        requireOpen()
        require(offsetBytes >= 0L && offsetBytes + data.remaining() <= sizeBytes) { "Buffer update is out of bounds" }
        val previous = GL15.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
        try {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, id)
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, offsetBytes, data)
        } finally {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previous)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        GL15.glDeleteBuffers(id)
    }

    companion object {
        fun allocate(sizeBytes: Long, usage: BufferUsage): Int {
            require(sizeBytes > 0L) { "Buffer size must be positive" }
            val previous = GL15.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
            val id = GL15.glGenBuffers()
            try {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, id)
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, sizeBytes, usage.glUsage)
                return id
            } catch (failure: Throwable) {
                GL15.glDeleteBuffers(id)
                throw failure
            } finally {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previous)
            }
        }
    }
}

internal class GlVertexBufferHandle(sizeBytes: Long, usage: BufferUsage) :
    GlBuffer(allocate(sizeBytes, usage), sizeBytes, usage), VertexBufferHandle

internal class GlIndexBufferHandle(
    sizeBytes: Long,
    override val indexType: IndexType,
    usage: BufferUsage
) : GlBuffer(allocate(sizeBytes, usage), sizeBytes, usage), IndexBufferHandle

private val BufferUsage.glUsage: Int
    get() = when (this) {
        BufferUsage.STATIC -> GL15.GL_STATIC_DRAW
        BufferUsage.DYNAMIC -> GL15.GL_DYNAMIC_DRAW
        BufferUsage.STREAM -> GL15.GL_STREAM_DRAW
    }
