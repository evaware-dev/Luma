package sweetie.evaware.luma.vertex

class ShaderVertices {
    val layout = VertexLayout()

    fun layout(layout: ShaderVert) = apply {
        this.layout.add(layout)
    }

    fun instanced(baseVertexCount: Int = 6) = apply {
        layout.markInstanced(baseVertexCount)
    }

    fun float(count: Int, layoutPos: Int, normalized: Boolean = false) = apply {
        layout.add(ShaderVertType.FLOAT, count, layoutPos, normalized)
    }

    fun float1(layoutPos: Int, normalized: Boolean = false) = float(1, layoutPos, normalized)
    fun float2(layoutPos: Int, normalized: Boolean = false) = float(2, layoutPos, normalized)
    fun float3(layoutPos: Int, normalized: Boolean = false) = float(3, layoutPos, normalized)
    fun float4(layoutPos: Int, normalized: Boolean = false) = float(4, layoutPos, normalized)

    fun vec1(layoutPos: Int, normalized: Boolean = false) = float1(layoutPos, normalized)
    fun vec2(layoutPos: Int, normalized: Boolean = false) = float2(layoutPos, normalized)
    fun vec3(layoutPos: Int, normalized: Boolean = false) = float3(layoutPos, normalized)
    fun vec4(layoutPos: Int, normalized: Boolean = false) = float4(layoutPos, normalized)

    fun requireConfigured() {
        require(layout.size() > 0) { "Shader requires at least one vertex layout" }
    }
}
