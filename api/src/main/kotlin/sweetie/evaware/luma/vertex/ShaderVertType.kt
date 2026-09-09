package sweetie.evaware.luma.vertex

enum class ShaderVertType(val byteSize: Int, val integer: Boolean) {
    FLOAT32(Float.SIZE_BYTES, false),
    FLOAT16(Short.SIZE_BYTES, false),
    INT8(Byte.SIZE_BYTES, true),
    UINT8(Byte.SIZE_BYTES, true),
    INT16(Short.SIZE_BYTES, true),
    UINT16(Short.SIZE_BYTES, true),
    INT32(Int.SIZE_BYTES, true),
    UINT32(Int.SIZE_BYTES, true);

    companion object {
        val FLOAT = FLOAT32
    }
}
