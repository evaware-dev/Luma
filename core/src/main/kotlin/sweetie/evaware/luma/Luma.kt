package sweetie.evaware.luma

import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.*
import sweetie.evaware.luma.matrix.MatrixControl
import sweetie.evaware.luma.shader.Shader
import sweetie.evaware.luma.texture.TextureHandle
import sweetie.evaware.luma.uniform.Mat4Uniform
import sweetie.evaware.luma.uniform.ShaderUniforms
import java.nio.IntBuffer

object Luma {
    class GlStateSnapshot {
        var drawFramebuffer = 0
        var readFramebuffer = 0
        var arrayBuffer = 0
        var viewportX = 0
        var viewportY = 0
        var viewportWidth = 0
        var viewportHeight = 0
        var blendEnabled = false
        var depthEnabled = false
        var cullEnabled = false
        var blendSrcRgb = 0
        var blendDstRgb = 0
        var blendSrcAlpha = 0
        var blendDstAlpha = 0
        var blendEquationRgb = 0
        var blendEquationAlpha = 0
        var activeTexture = 0
        var boundTexture2d = 0
        var program = 0
        var vertexArray = 0
    }

    class FramebufferSnapshot {
        var drawFramebuffer = 0
        var readFramebuffer = 0
        var viewportX = 0
        var viewportY = 0
        var viewportWidth = 0
        var viewportHeight = 0
    }

    @JvmField
    var platform: RenderPlatform = DefaultRenderPlatform

    @JvmField
    var mockRenderTargetWidth: Int? = null

    @JvmField
    var mockRenderTargetHeight: Int? = null

    private val viewportBuffer: IntBuffer = BufferUtils.createIntBuffer(4)
    private val frameSnapshot = GlStateSnapshot()
    private val transientStateSnapshots = ArrayList<GlStateSnapshot>(4).apply {
        repeat(4) { add(GlStateSnapshot()) }
    }

    @PublishedApi internal var frameActive = false
    private var transientStateDepth = 0
    private var boundArrayBufferId = -1
    private var boundTextureUnit = -1
    private var boundTextureId = -1
    private var boundProgramId = -1
    private var boundVertexArrayId = -1

    var contextProvider: () -> Boolean = { GLFW.glfwGetCurrentContext() != 0L }

    fun hasContext() = contextProvider()

    @PublishedApi internal fun swapToMainFramebuffer() {
        platform.swapToMainFramebuffer(this)
    }

    fun isFrameActive() = frameActive

    fun beginMainFramebufferFrame() {
        if (frameActive) return

        captureManagedMainFramebufferState(frameSnapshot)
        swapToMainFramebuffer()
        applyGuiState()
        MatrixControl.beginGuiFrame()
        invalidateBindingCache()
        frameActive = true
    }

    fun endFrame() {
        if (!frameActive) return
        restoreState(frameSnapshot)
        frameActive = false
    }

    @PublishedApi internal fun captureState(snapshot: GlStateSnapshot): GlStateSnapshot {
        captureFramebuffer(snapshot)
        snapshot.arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
        snapshot.blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND)
        snapshot.depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        snapshot.cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE)
        snapshot.blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
        snapshot.blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        snapshot.blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
        snapshot.blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        snapshot.blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB)
        snapshot.blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA)
        snapshot.activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
        snapshot.boundTexture2d = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        snapshot.program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        snapshot.vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
        return snapshot
    }

    @PublishedApi internal fun restoreState(snapshot: GlStateSnapshot) {
        restoreFramebuffer(snapshot)
        bindArrayBuffer(snapshot.arrayBuffer)

        platform.restoreState(snapshot)

        GL20.glBlendEquationSeparate(snapshot.blendEquationRgb, snapshot.blendEquationAlpha)

        GL13.glActiveTexture(snapshot.activeTexture)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, snapshot.boundTexture2d)
        GL20.glUseProgram(snapshot.program)
        GL30.glBindVertexArray(snapshot.vertexArray)

        boundTextureUnit = snapshot.activeTexture
        boundTextureId = snapshot.boundTexture2d
        boundProgramId = snapshot.program
        boundVertexArrayId = snapshot.vertexArray
        boundArrayBufferId = snapshot.arrayBuffer
    }

    private fun captureFramebuffer(snapshot: FramebufferSnapshot): FramebufferSnapshot {
        val viewport = IntArray(4)
        if (platform.getViewport(viewport)) {
            snapshot.viewportX = viewport[0]
            snapshot.viewportY = viewport[1]
            snapshot.viewportWidth = viewport[2]
            snapshot.viewportHeight = viewport[3]
        } else {
            viewportBuffer.clear()
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewportBuffer)
            snapshot.viewportX = viewportBuffer.get(0)
            snapshot.viewportY = viewportBuffer.get(1)
            snapshot.viewportWidth = viewportBuffer.get(2)
            snapshot.viewportHeight = viewportBuffer.get(3)
        }
        snapshot.drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        snapshot.readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
        return snapshot
    }

    private fun captureFramebuffer(snapshot: GlStateSnapshot): GlStateSnapshot {
        val viewport = IntArray(4)
        if (platform.getViewport(viewport)) {
            snapshot.viewportX = viewport[0]
            snapshot.viewportY = viewport[1]
            snapshot.viewportWidth = viewport[2]
            snapshot.viewportHeight = viewport[3]
        } else {
            viewportBuffer.clear()
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewportBuffer)
            snapshot.viewportX = viewportBuffer.get(0)
            snapshot.viewportY = viewportBuffer.get(1)
            snapshot.viewportWidth = viewportBuffer.get(2)
            snapshot.viewportHeight = viewportBuffer.get(3)
        }
        snapshot.drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        snapshot.readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
        return snapshot
    }

    fun captureManagedMainFramebufferState(snapshot: GlStateSnapshot): GlStateSnapshot {
        val mockW = mockRenderTargetWidth
        val mockH = mockRenderTargetHeight
        if (mockW != null && mockH != null) {
            snapshot.viewportX = 0
            snapshot.viewportY = 0
            snapshot.viewportWidth = mockW
            snapshot.viewportHeight = mockH
        } else {
            val viewport = IntArray(4)
            if (platform.getViewport(viewport)) {
                snapshot.viewportX = viewport[0]
                snapshot.viewportY = viewport[1]
                snapshot.viewportWidth = viewport[2]
                snapshot.viewportHeight = viewport[3]
            } else {
                viewportBuffer.clear()
                GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewportBuffer)
                snapshot.viewportX = viewportBuffer.get(0)
                snapshot.viewportY = viewportBuffer.get(1)
                snapshot.viewportWidth = viewportBuffer.get(2)
                snapshot.viewportHeight = viewportBuffer.get(3)
            }
        }

        platform.captureState(snapshot)
        snapshot.arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
        snapshot.blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB)
        snapshot.blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA)
        snapshot.program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        snapshot.vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
        snapshot.activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
        snapshot.boundTexture2d = platform.getBoundTexture2d()
        return snapshot
    }

    private fun restoreFramebuffer(snapshot: FramebufferSnapshot) {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, snapshot.drawFramebuffer)
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, snapshot.readFramebuffer)
        GL11.glViewport(snapshot.viewportX, snapshot.viewportY, snapshot.viewportWidth, snapshot.viewportHeight)
    }

    private fun restoreFramebuffer(snapshot: GlStateSnapshot) {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, snapshot.drawFramebuffer)
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, snapshot.readFramebuffer)
        GL11.glViewport(snapshot.viewportX, snapshot.viewportY, snapshot.viewportWidth, snapshot.viewportHeight)
    }

    inline fun render(action: () -> Unit) {
        if (frameActive) {
            action()
            return
        }

        val snapshot = acquireTransientStateSnapshot()
        var captured = false
        try {
            captureState(snapshot)
            captured = true
            applyGuiState()
            invalidateBindingCache()
            action()
        } finally {
            if (captured) {
                restoreState(snapshot)
            }
            releaseTransientStateSnapshot()
        }
    }

    inline fun renderToMainFramebuffer(action: () -> Unit) {
        if (frameActive) {
            action()
            return
        }

        val snapshot = acquireTransientStateSnapshot()
        var captured = false
        try {
            captureManagedMainFramebufferState(snapshot)
            captured = true
            swapToMainFramebuffer()
            applyGuiState()
            invalidateBindingCache()
            action()
        } finally {
            if (captured) {
                restoreState(snapshot)
            }
            releaseTransientStateSnapshot()
        }
    }

    @PublishedApi internal fun applyGuiState() {
        GL11.glDisable(GL11.GL_DEPTH_TEST)
        GL11.glDisable(GL11.GL_CULL_FACE)
        GL11.glEnable(GL11.GL_BLEND)
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD)
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO)
    }

    internal fun useProgram(programId: Int) {
        if (boundProgramId == programId) return
        GL20.glUseProgram(programId)
        boundProgramId = programId
    }

    internal fun bindVertexArray(vertexArrayId: Int) {
        if (boundVertexArrayId == vertexArrayId) return
        GL30.glBindVertexArray(vertexArrayId)
        boundVertexArrayId = vertexArrayId
    }

    internal fun bindArrayBuffer(bufferId: Int) {
        if (boundArrayBufferId == bufferId) return
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId)
        boundArrayBufferId = bufferId
    }

    fun bindTexture(texture: TextureHandle, unit: Int = 0) = bindTexture(texture.id, unit)

    internal fun bindTexture(textureId: Int, unit: Int = 0) {
        val activeUnit = GL13.GL_TEXTURE0 + unit
        if (boundTextureUnit != activeUnit) {
            GL13.glActiveTexture(activeUnit)
            boundTextureUnit = activeUnit
            boundTextureId = -1
        }
        GL33.glBindSampler(unit, 0)
        if (boundTextureId == textureId) return
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
        boundTextureId = textureId
    }

    internal fun onTextureDeleted(textureId: Int) {
        if (boundTextureId == textureId) {
            boundTextureId = -1
        }
    }

    internal fun onProgramDeleted(programId: Int) {
        if (boundProgramId == programId) {
            boundProgramId = -1
        }
    }

    internal fun onVertexArrayDeleted(vertexArrayId: Int) {
        if (boundVertexArrayId == vertexArrayId) {
            boundVertexArrayId = -1
        }
    }

    internal fun onArrayBufferDeleted(bufferId: Int) {
        if (boundArrayBufferId == bufferId) {
            boundArrayBufferId = -1
        }
    }

    internal fun invalidateBindings() {
        invalidateBindingCache()
    }

    fun applyGameMatrix(uniforms: ShaderUniforms, uniform: Mat4Uniform) {
        uniforms.projectionMat4(uniform, MatrixControl.projection(), MatrixControl.projectionVersion())
    }

    fun drawShader(shader: Shader): Int = shader.draw()

    @PublishedApi internal fun invalidateBindingCache() {
        boundArrayBufferId = -1
        boundTextureUnit = -1
        boundTextureId = -1
        boundProgramId = -1
        boundVertexArrayId = -1
    }

    fun bindFramebuffer(framebufferId: Int, width: Int, height: Int) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferId)
        GL11.glViewport(0, 0, width, height)
    }

    @PublishedApi internal fun acquireTransientStateSnapshot(): GlStateSnapshot {
        if (transientStateDepth == transientStateSnapshots.size) {
            transientStateSnapshots.add(GlStateSnapshot())
        }
        return transientStateSnapshots[transientStateDepth++]
    }

    @PublishedApi internal fun releaseTransientStateSnapshot() {
        if (transientStateDepth > 0) {
            transientStateDepth--
        }
    }
}
