package sweetie.evaware.luma.wrapper.vertex

@Suppress("unused")
enum class ShaderVertType(
    val byteSize: Int,
    val integer: Boolean,
    val signed: Boolean,
    val floating: Boolean
) {
    FLOAT(Float.SIZE_BYTES, integer = false, signed = true, floating = true),
    BYTE(Byte.SIZE_BYTES, integer = true, signed = true, floating = false),
    UNSIGNED_BYTE(Byte.SIZE_BYTES, integer = true, signed = false, floating = false),
    SHORT(Short.SIZE_BYTES, integer = true, signed = true, floating = false),
    UNSIGNED_SHORT(Short.SIZE_BYTES, integer = true, signed = false, floating = false),
    INT(Int.SIZE_BYTES, integer = true, signed = true, floating = false),
    UNSIGNED_INT(Int.SIZE_BYTES, integer = true, signed = false, floating = false)
}
