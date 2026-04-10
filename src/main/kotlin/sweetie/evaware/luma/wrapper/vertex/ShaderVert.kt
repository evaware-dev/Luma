package sweetie.evaware.luma.wrapper.vertex

data class ShaderVert(
    val type: ShaderVertType,
    val count: Int,
    val layoutPos: Int,
    val normalized: Boolean = false
) {
    val floatSize get() = count
    val byteSize get() = count * type.byteSize
}
