package sweetie.evaware.luma.runtime

import com.mojang.blaze3d.opengl.GlStateManager
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL15

internal class GlBindingTracker {
    private var boundArrayBufferId = -1
    private var boundTextureUnit = -1
    private var boundTextureId = -1
    private var boundProgramId = -1
    private var boundVertexArrayId = -1

    fun useProgram(programId: Int) {
        if (boundProgramId == programId) return
        GlStateManager._glUseProgram(programId)
        boundProgramId = programId
    }

    fun bindVertexArray(vertexArrayId: Int) {
        if (boundVertexArrayId == vertexArrayId) return
        GlStateManager._glBindVertexArray(vertexArrayId)
        boundVertexArrayId = vertexArrayId
    }

    fun bindArrayBuffer(bufferId: Int) {
        if (boundArrayBufferId == bufferId) return
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId)
        boundArrayBufferId = bufferId
    }

    fun bindTexture(textureId: Int, unit: Int = 0) {
        val activeUnit = GL13.GL_TEXTURE0 + unit
        if (boundTextureUnit != activeUnit) {
            GlStateManager._activeTexture(activeUnit)
            boundTextureUnit = activeUnit
            boundTextureId = -1
        }
        if (boundTextureId == textureId) return
        GlStateManager._bindTexture(textureId)
        boundTextureId = textureId
    }

    fun onTextureDeleted(textureId: Int) {
        if (boundTextureId == textureId) {
            boundTextureId = -1
        }
    }

    fun onProgramDeleted(programId: Int) {
        if (boundProgramId == programId) {
            boundProgramId = -1
        }
    }

    fun onVertexArrayDeleted(vertexArrayId: Int) {
        if (boundVertexArrayId == vertexArrayId) {
            boundVertexArrayId = -1
        }
    }

    fun onArrayBufferDeleted(bufferId: Int) {
        if (boundArrayBufferId == bufferId) {
            boundArrayBufferId = -1
        }
    }

    fun invalidate() {
        boundArrayBufferId = -1
        boundTextureUnit = -1
        boundTextureId = -1
        boundProgramId = -1
        boundVertexArrayId = -1
    }

    fun sync(snapshot: GlStateSnapshot) {
        boundArrayBufferId = snapshot.arrayBuffer
        boundTextureUnit = snapshot.activeTexture
        boundTextureId = snapshot.boundTexture2d
        boundProgramId = snapshot.program
        boundVertexArrayId = snapshot.vertexArray
    }
}
