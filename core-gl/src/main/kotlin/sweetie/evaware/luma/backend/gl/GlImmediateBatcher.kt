package sweetie.evaware.luma.backend.gl

import java.nio.FloatBuffer
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL32
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.uniform.ShaderUniforms

internal class GlImmediateBatcher {
    var pendingProgram: Program? = null
        private set
    private var pendingPrimitive: PrimitiveType = PrimitiveType.TRIANGLES
    private var pendingBaseVertex = 0
    private var pendingVertexCount = 0
    private var pendingInstanced = false

    val hasPending: Boolean
        get() = pendingProgram != null

    inline fun tryAppend(
        glProgram: Program,
        primitiveType: PrimitiveType,
        uniforms: ShaderUniforms,
        vertices: FloatBuffer,
        vertexCount: Int,
        upload: (GlVertexBuffer, FloatBuffer) -> Int,
        flushCurrent: () -> Unit
    ): Boolean {
        val vb = glProgram.vertexBuffer
        val floatCount = vertices.remaining()
        if (
            pendingProgram !== glProgram ||
            pendingPrimitive != primitiveType ||
            glProgram.hasPendingUniformUpload(uniforms) ||
            floatCount != vertexCount * vb.layout.strideFloats ||
            (primitiveType == PrimitiveType.QUADS && ((vertexCount and 3) != 0 || (pendingVertexCount and 3) != 0)) ||
            !vb.canAppendContiguously(floatCount)
        ) {
            return false
        }

        val baseVertex = upload(vb, vertices)
        if (baseVertex == pendingBaseVertex + pendingVertexCount) {
            pendingVertexCount += vertexCount
            return true
        }
        // Defensive fallback if offset was not contiguous
        flushCurrent()
        recordPending(glProgram, primitiveType, baseVertex, vertexCount, vb.layout.instanced)
        return true
    }

    fun recordPending(
        glProgram: Program,
        primitiveType: PrimitiveType,
        baseVertex: Int,
        vertexCount: Int,
        instanced: Boolean
    ) {
        pendingProgram = glProgram
        pendingPrimitive = primitiveType
        pendingBaseVertex = baseVertex
        pendingVertexCount = vertexCount
        pendingInstanced = instanced
    }

    inline fun flush(bindProgramAndVao: (Program) -> Unit) {
        val glProgram = pendingProgram ?: return
        val primitive = pendingPrimitive
        val baseVertex = pendingBaseVertex
        val vertexCount = pendingVertexCount
        val instanced = pendingInstanced
        pendingProgram = null
        pendingVertexCount = 0
        if (vertexCount <= 0) return
        bindProgramAndVao(glProgram)
        executeDraw(glProgram, primitive, baseVertex, vertexCount, instanced)
    }

    fun clear() {
        pendingProgram = null
        pendingVertexCount = 0
    }

    fun executeDraw(
        glProgram: Program,
        primitiveType: PrimitiveType,
        baseVertex: Int,
        vertexCount: Int,
        instanced: Boolean
    ) {
        val vb = glProgram.vertexBuffer
        when {
            instanced -> {
                vb.drawInstancedBaseInstance(vb.layout.baseVertexCount, vertexCount, baseVertex)
            }
            primitiveType == PrimitiveType.QUADS -> {
                val indexCount = vb.bindQuadIndices(vertexCount)
                if (baseVertex == 0) {
                    GL11.glDrawElements(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, 0L)
                } else {
                    GL32.glDrawElementsBaseVertex(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, 0L, baseVertex)
                }
            }
            primitiveType == PrimitiveType.LINES -> GL11.glDrawArrays(GL11.GL_LINES, baseVertex, vertexCount)
            else -> GL11.glDrawArrays(GL11.GL_TRIANGLES, baseVertex, vertexCount)
        }
    }
}
