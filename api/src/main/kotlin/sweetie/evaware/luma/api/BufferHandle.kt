package sweetie.evaware.luma.api

enum class BufferUsage {
    STATIC,
    DYNAMIC,
    STREAM
}

enum class IndexType(val byteSize: Int) {
    UINT16(Short.SIZE_BYTES),
    UINT32(Int.SIZE_BYTES)
}

interface VertexBufferHandle : AutoCloseable {
    val sizeBytes: Long
}

interface IndexBufferHandle : AutoCloseable {
    val sizeBytes: Long
    val indexType: IndexType
}
