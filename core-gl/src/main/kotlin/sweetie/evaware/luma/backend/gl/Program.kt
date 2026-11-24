package sweetie.evaware.luma.backend.gl

import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL33
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.VertexInputLayout
import sweetie.evaware.luma.vertex.VertexLayout

class Program(
    val programId: Int,
    val layouts: VertexInputLayout
) : ProgramHandle {
    constructor(programId: Int, layout: VertexLayout) : this(programId, VertexInputLayout().binding(layout))

    val layout = layouts.layout(0)
    val vertexBuffer = GlVertexBuffer(layout)
    val directVertexArray = GL30.glGenVertexArrays()
    internal val configuredBuffers = arrayOfNulls<GlVertexBufferHandle>(layouts.size())
    internal val configuredBufferOffsets = LongArray(layouts.size()) { -1L }
    internal val singleBinding = layouts.size() == 1
    internal var configuredBuffer0: GlVertexBufferHandle? = null
    internal var configuredOffset0: Long = -1L
    internal var configuredElementBuffer: GlIndexBufferHandle? = null
    private val uniformLocations = HashMap<String, Int>()
    private val uniformCache = GlUniformCache(::getUniformLocation)

    fun getUniformLocation(name: String): Int {
        return uniformLocations.getOrPut(name) {
            GL20.glGetUniformLocation(programId, name)
        }
    }

    fun getPreparedUniforms(uniforms: ShaderUniforms): List<PreparedGlUniform> =
        uniformCache.getPreparedUniforms(uniforms)

    internal fun hasPendingUniformUpload(uniforms: ShaderUniforms): Boolean =
        !uniformCache.isClean(uniforms)

    internal fun uploadUniforms(uniforms: ShaderUniforms) {
        uniformCache.upload(uniforms)
    }

    override fun close() {
        configuredBuffers.fill(null)
        configuredBuffer0 = null
        configuredOffset0 = -1L
        configuredElementBuffer = null
        vertexBuffer.close()
        GL30.glDeleteVertexArrays(directVertexArray)
        GL20.glDeleteProgram(programId)
    }

    internal inline fun configureDirectBindings(
        buffers: Array<GlVertexBufferHandle?>,
        offsets: LongArray,
        onBeforeBindArrayBuffer: () -> Unit
    ): Boolean {
        if (singleBinding) {
            val buffer = requireNotNull(buffers[0]) { "Vertex binding 0 is not set" }
            buffer.requireOpen()
            val offset = offsets[0]
            if (configuredBuffer0 === buffer && configuredOffset0 == offset) return false
            onBeforeBindArrayBuffer()
            bindDirectSlot(0, buffer, offset)
            configuredBuffer0 = buffer
            configuredOffset0 = offset
            return true
        }
        var arrayBufferBound = false
        for (binding in 0 until layouts.size()) {
            val buffer = requireNotNull(buffers[binding]) { "Vertex binding $binding is not set" }
            buffer.requireOpen()
            val offset = offsets[binding]
            if (configuredBuffers[binding] === buffer && configuredBufferOffsets[binding] == offset) {
                continue
            }
            if (!arrayBufferBound) {
                onBeforeBindArrayBuffer()
                arrayBufferBound = true
            }
            bindDirectSlot(binding, buffer, offset)
            configuredBuffers[binding] = buffer
            configuredBufferOffsets[binding] = offset
        }
        return arrayBufferBound
    }

    internal fun bindDirectSlot(binding: Int, buffer: GlVertexBufferHandle, offset: Long) {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer.id)
        val layout = layouts.layout(binding)
        var attributeOffset = offset
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
    }
}
