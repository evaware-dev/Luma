package sweetie.evaware.luma.backend.gl

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL33
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.uniform.*
import sweetie.evaware.luma.vertex.VertexLayout
import sweetie.evaware.luma.vertex.VertexInputLayout
import sweetie.evaware.luma.vertex.ShaderVertType

class Program(
    val programId: Int,
    val layouts: VertexInputLayout
) : ProgramHandle {
    constructor(programId: Int, layout: VertexLayout) : this(programId, VertexInputLayout().binding(layout))

    val layout = layouts.layout(0)
    val vertexBuffer = GlVertexBuffer(layout)
    val directVertexArray = GL30.glGenVertexArrays()
    private val configuredBufferIds = IntArray(layouts.size()) { -1 }
    private val configuredBufferOffsets = LongArray(layouts.size()) { -1L }
    private val uniformLocations = HashMap<String, Int>()
    private var preparedUniforms: List<PreparedGlUniform>? = null

    fun getUniformLocation(name: String): Int {
        return uniformLocations.getOrPut(name) {
            GL20.glGetUniformLocation(programId, name)
        }
    }

    fun getPreparedUniforms(uniforms: ShaderUniforms): List<PreparedGlUniform> {
        var list = preparedUniforms
        if (list == null) {
            list = uniforms.registry.entries.mapNotNull { entry ->
                val loc = getUniformLocation(entry.name)
                if (loc >= 0) {
                    val uniform = when (entry.handle) {
                        is Float1Uniform -> GlFloat1Uniform(entry.name)
                        is Float2Uniform -> GlFloat2Uniform(entry.name)
                        is Float3Uniform -> GlFloat3Uniform(entry.name)
                        is Float4Uniform -> GlFloat4Uniform(entry.name)
                        is Int1Uniform -> GlInt1Uniform(entry.name)
                        is Mat4Uniform -> GlMat4Uniform(entry.name)
                    }
                    PreparedGlUniform(uniform, loc)
                } else {
                    null
                }
            }
            preparedUniforms = list
        }
        return list
    }

    override fun close() {
        vertexBuffer.close()
        GL30.glDeleteVertexArrays(directVertexArray)
        GL20.glDeleteProgram(programId)
    }

    internal fun configureDirectBindings(buffers: Array<GlVertexBufferHandle?>, offsets: LongArray) {
        GL30.glBindVertexArray(directVertexArray)
        for (binding in 0 until layouts.size()) {
            val buffer = requireNotNull(buffers[binding]) { "Vertex binding $binding is not set" }
            buffer.requireOpen()
            if (configuredBufferIds[binding] == buffer.id && configuredBufferOffsets[binding] == offsets[binding]) {
                continue
            }
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer.id)
            val layout = layouts.layout(binding)
            var attributeOffset = offsets[binding]
            val stride = layout.strideBytes
            for (index in 0 until layout.size()) {
                val location = layout.layoutPos(index)
                GL20.glEnableVertexAttribArray(location)
                val type = layout.type(index)
                if (type.integer && !layout.normalized(index)) {
                    GL30.glVertexAttribIPointer(location, layout.count(index), type.glType, stride, attributeOffset)
                } else {
                    GL20.glVertexAttribPointer(
                        location,
                        layout.count(index),
                        type.glType,
                        layout.normalized(index),
                        stride,
                        attributeOffset
                    )
                }
                GL33.glVertexAttribDivisor(location, layouts.stepRate(binding))
                attributeOffset += layout.byteSize(index)
            }
            configuredBufferIds[binding] = buffer.id
            configuredBufferOffsets[binding] = offsets[binding]
        }
    }
}

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
