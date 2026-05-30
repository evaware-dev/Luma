package sweetie.evaware.luma

import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.*
import sweetie.evaware.luma.matrix.MatrixControl
import sweetie.evaware.luma.shader.Shader
import sweetie.evaware.luma.texture.TextureHandle
import sweetie.evaware.luma.uniform.Mat4Uniform
import sweetie.evaware.luma.uniform.ShaderUniforms

object Luma {
    interface IFramebufferState {
        var drawFramebuffer: Int
        var readFramebuffer: Int
        var viewportX: Int
        var viewportY: Int
        var viewportWidth: Int
        var viewportHeight: Int
    }

    class GlStateSnapshot : IFramebufferState {
        override var drawFramebuffer = 0
        override var readFramebuffer = 0
        var arrayBuffer = 0
        override var viewportX = 0
        override var viewportY = 0
        override var viewportWidth = 0
        override var viewportHeight = 0
        var blendEnabled = false
        var depthEnabled = false
        var cullEnabled = false
        var blendSrcRgb = 0
        var blendDstRgb = 0
        var blendSrcAlpha = 0
        var blendDstAlpha = 0
        var blendEquationRgb = 0
        var blendEquationAlpha = 0
        var program = 0
        var vertexArray = 0
        var activeTexture = 0
        val boundTextures = IntArray(2)
        val samplerBindings = IntArray(2)
    }

    class FramebufferSnapshot : IFramebufferState {
        override var drawFramebuffer = 0
        override var readFramebuffer = 0
        override var viewportX = 0
        override var viewportY = 0
        override var viewportWidth = 0
        override var viewportHeight = 0
    }

    @JvmField
    var platform: RenderPlatform = DefaultRenderPlatform

    @JvmField
    var mockRenderTargetWidth: Int? = null

    @JvmField
    var mockRenderTargetHeight: Int? = null

    private val cachedViewportArray = IntArray(4)
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
        platform.captureState(snapshot)

        snapshot.arrayBuffer = platform.getActiveArrayBuffer()
        snapshot.blendEquationRgb = platform.getBlendEquationRgb()
        snapshot.blendEquationAlpha = platform.getBlendEquationAlpha()
        snapshot.program = platform.getActiveProgram()
        snapshot.vertexArray = platform.getActiveVertexArray()
        snapshot.activeTexture = platform.getActiveTextureUnit()

        for (i in 0..1) {
            snapshot.boundTextures[i] = platform.getBoundTextureForUnit(i)
            snapshot.samplerBindings[i] = platform.getSamplerForUnit(i)
        }

        return snapshot
    }

    @PublishedApi internal fun restoreState(snapshot: GlStateSnapshot) {
        restoreFramebuffer(snapshot)

        if (boundArrayBufferId != snapshot.arrayBuffer) {
            platform.bindArrayBuffer(snapshot.arrayBuffer)
            boundArrayBufferId = snapshot.arrayBuffer
        }

        platform.restoreState(snapshot)
        GL20.glBlendEquationSeparate(snapshot.blendEquationRgb, snapshot.blendEquationAlpha)

        if (boundProgramId != snapshot.program) {
            platform.useProgram(snapshot.program)
            boundProgramId = snapshot.program
        }

        if (boundVertexArrayId != snapshot.vertexArray) {
            platform.bindVertexArray(snapshot.vertexArray)
            boundVertexArrayId = snapshot.vertexArray
        }

        for (i in 0..1) {
            platform.activeTexture(GL13.GL_TEXTURE0 + i)
            platform.bindTexture2d(snapshot.boundTextures[i])
            GL33.glBindSampler(i, snapshot.samplerBindings[i])
        }

        platform.activeTexture(snapshot.activeTexture)

        boundTextureUnit = snapshot.activeTexture
        boundTextureId = -1
    }

    private fun captureFramebuffer(snapshot: IFramebufferState): IFramebufferState {
        val viewport = cachedViewportArray
        if (platform.getViewport(viewport)) {
            snapshot.viewportX = viewport[0]; snapshot.viewportY = viewport[1]
            snapshot.viewportWidth = viewport[2]; snapshot.viewportHeight = viewport[3]
        } else {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport)
            snapshot.viewportX = viewport[0]; snapshot.viewportY = viewport[1]
            snapshot.viewportWidth = viewport[2]; snapshot.viewportHeight = viewport[3]
        }
        snapshot.drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        snapshot.readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
        return snapshot
    }

    fun captureManagedMainFramebufferState(snapshot: GlStateSnapshot): GlStateSnapshot {
        val mockW = mockRenderTargetWidth
        val mockH = mockRenderTargetHeight
        if (mockW != null && mockH != null) {
            snapshot.viewportX = 0; snapshot.viewportY = 0
            snapshot.viewportWidth = mockW; snapshot.viewportHeight = mockH
        } else {
            val viewport = cachedViewportArray
            if (platform.getViewport(viewport)) {
                snapshot.viewportX = viewport[0]; snapshot.viewportY = viewport[1]
                snapshot.viewportWidth = viewport[2]; snapshot.viewportHeight = viewport[3]
            } else {
                GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport)
                snapshot.viewportX = viewport[0]; snapshot.viewportY = viewport[1]
                snapshot.viewportWidth = viewport[2]; snapshot.viewportHeight = viewport[3]
            }
        }

        platform.captureState(snapshot)
        snapshot.arrayBuffer = platform.getActiveArrayBuffer()
        snapshot.blendEquationRgb = platform.getBlendEquationRgb()
        snapshot.blendEquationAlpha = platform.getBlendEquationAlpha()
        snapshot.program = platform.getActiveProgram()
        snapshot.vertexArray = platform.getActiveVertexArray()
        snapshot.activeTexture = platform.getActiveTextureUnit()

        for (i in 0..1) {
            snapshot.boundTextures[i] = platform.getBoundTextureForUnit(i)
            snapshot.samplerBindings[i] = platform.getSamplerForUnit(i)
        }

        return snapshot
    }

    private fun restoreFramebuffer(snapshot: IFramebufferState) {
        platform.bindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, snapshot.drawFramebuffer)
        platform.bindFramebuffer(GL30.GL_READ_FRAMEBUFFER, snapshot.readFramebuffer)
        platform.viewport(snapshot.viewportX, snapshot.viewportY, snapshot.viewportWidth, snapshot.viewportHeight)
    }

    inline fun render(action: () -> Unit) {
        if (frameActive) { action(); return }
        val snapshot = acquireTransientStateSnapshot()
        var captured = false
        try {
            captureState(snapshot)
            captured = true
            applyGuiState()
            invalidateBindingCache()
            action()
        } finally {
            if (captured) restoreState(snapshot)
            releaseTransientStateSnapshot()
        }
    }

    inline fun renderToMainFramebuffer(action: () -> Unit) {
        if (frameActive) { action(); return }
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
            if (captured) restoreState(snapshot)
            releaseTransientStateSnapshot()
        }
    }

    @PublishedApi internal fun applyGuiState() {
        platform.disableDepthTest()
        platform.disableCull()
        platform.enableBlend()
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD)
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO)
    }

    internal fun useProgram(programId: Int) {
        if (boundProgramId == programId) return
        platform.useProgram(programId)
        boundProgramId = programId
    }

    internal fun bindVertexArray(vertexArrayId: Int) {
        if (boundVertexArrayId == vertexArrayId) return
        platform.bindVertexArray(vertexArrayId)
        boundVertexArrayId = vertexArrayId
    }

    internal fun bindArrayBuffer(bufferId: Int) {
        if (boundArrayBufferId == bufferId) return
        platform.bindArrayBuffer(bufferId)
        boundArrayBufferId = bufferId
    }

    fun bindTexture(texture: TextureHandle, unit: Int = 0) = bindTexture(texture.id, unit)

    fun bindTexture(textureId: Int, unit: Int = 0) {
        val activeUnit = GL13.GL_TEXTURE0 + unit
        if (boundTextureUnit != activeUnit) {
            platform.activeTexture(activeUnit)
            boundTextureUnit = activeUnit
            boundTextureId = -1
        }
        GL33.glBindSampler(unit, 0)
        if (boundTextureId == textureId) return
        platform.bindTexture2d(textureId)
        boundTextureId = textureId
    }

    internal fun onTextureDeleted(textureId: Int) { if (boundTextureId == textureId) boundTextureId = -1 }
    internal fun onProgramDeleted(programId: Int) { if (boundProgramId == programId) boundProgramId = -1 }
    internal fun onVertexArrayDeleted(vertexArrayId: Int) { if (boundVertexArrayId == vertexArrayId) boundVertexArrayId = -1 }
    internal fun onArrayBufferDeleted(bufferId: Int) { if (boundArrayBufferId == bufferId) boundArrayBufferId = -1 }

    internal fun invalidateBindings() { invalidateBindingCache() }
    fun applyGameMatrix(uniforms: ShaderUniforms, uniform: Mat4Uniform) { uniforms.projectionMat4(uniform, MatrixControl.projection(), MatrixControl.projectionVersion()) }
    fun drawShader(shader: Shader): Int = shader.draw()

    @PublishedApi internal fun invalidateBindingCache() {
        boundArrayBufferId = -1
        boundTextureUnit = -1
        boundTextureId = -1
        boundProgramId = -1
        boundVertexArrayId = -1
    }

    fun bindFramebuffer(framebufferId: Int, width: Int, height: Int) {
        platform.bindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferId)
        platform.viewport(0, 0, width, height)
    }

    @PublishedApi internal fun acquireTransientStateSnapshot(): GlStateSnapshot {
        if (transientStateDepth == transientStateSnapshots.size) transientStateSnapshots.add(GlStateSnapshot())
        return transientStateSnapshots[transientStateDepth++]
    }

    @PublishedApi internal fun releaseTransientStateSnapshot() {
        if (transientStateDepth > 0) transientStateDepth--
    }
}