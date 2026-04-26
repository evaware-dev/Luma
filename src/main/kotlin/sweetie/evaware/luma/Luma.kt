package sweetie.evaware.luma

import com.mojang.blaze3d.opengl.GlConst
import com.mojang.blaze3d.opengl.GlStateManager
import net.minecraft.client.Minecraft
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.*
import sweetie.evaware.luma.framebuffer.FramebufferHandle
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

    private object ManagedStateTracker {
        fun capture(snapshot: GlStateSnapshot) {
            snapshot.drawFramebuffer = GlStateManager.getFrameBuffer(GL30.GL_DRAW_FRAMEBUFFER)
            snapshot.readFramebuffer = GlStateManager.getFrameBuffer(GL30.GL_READ_FRAMEBUFFER)
            snapshot.blendEnabled = GlStateManager.BLEND.mode.enabled
            snapshot.depthEnabled = GlStateManager.DEPTH.mode.enabled
            snapshot.cullEnabled = GlStateManager.CULL.enable.enabled
            snapshot.blendSrcRgb = GlStateManager.BLEND.srcRgb
            snapshot.blendDstRgb = GlStateManager.BLEND.dstRgb
            snapshot.blendSrcAlpha = GlStateManager.BLEND.srcAlpha
            snapshot.blendDstAlpha = GlStateManager.BLEND.dstAlpha
            snapshot.activeTexture = GL13.GL_TEXTURE0 + GlStateManager.activeTexture
            snapshot.boundTexture2d = boundTextureFor(snapshot.activeTexture)
        }

        private fun boundTextureFor(activeTexture: Int): Int {
            val textureIndex = activeTexture - GL13.GL_TEXTURE0
            if (textureIndex !in GlStateManager.TEXTURES.indices) return 0
            return GlStateManager.TEXTURES[textureIndex].binding
        }
    }

    private val viewportBuffer: IntBuffer = BufferUtils.createIntBuffer(4)
    private val frameSnapshot = GlStateSnapshot()
    private val transientStateSnapshots = ArrayList<GlStateSnapshot>(4).apply {
        repeat(4) { add(GlStateSnapshot()) }
    }

    private var frameActive = false
    private var transientStateDepth = 0
    private var boundArrayBufferId = -1
    private var boundTextureUnit = -1
    private var boundTextureId = -1
    private var boundProgramId = -1
    private var boundVertexArrayId = -1

    internal var contextProvider: () -> Boolean = { GLFW.glfwGetCurrentContext() != 0L }

    fun hasContext() = contextProvider()

    private fun swapToMainFramebuffer() {
        val target = Minecraft.getInstance().mainRenderTarget
        val colorTexture = target.colorTextureView ?: return
        bindFramebuffer(
            FramebufferHandle.resolve(colorTexture, target.depthTextureView),
            colorTexture.getWidth(0),
            colorTexture.getHeight(0)
        )
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

    private fun captureState(snapshot: GlStateSnapshot): GlStateSnapshot {
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

    private fun restoreState(snapshot: GlStateSnapshot) {
        restoreFramebuffer(snapshot)
        bindArrayBuffer(snapshot.arrayBuffer)

        if (snapshot.blendEnabled) GlStateManager._enableBlend() else GlStateManager._disableBlend()
        if (snapshot.depthEnabled) GlStateManager._enableDepthTest() else GlStateManager._disableDepthTest()
        if (snapshot.cullEnabled) GlStateManager._enableCull() else GlStateManager._disableCull()

        GL20.glBlendEquationSeparate(snapshot.blendEquationRgb, snapshot.blendEquationAlpha)
        GlStateManager._blendFuncSeparate(
            snapshot.blendSrcRgb,
            snapshot.blendDstRgb,
            snapshot.blendSrcAlpha,
            snapshot.blendDstAlpha
        )

        GlStateManager._activeTexture(snapshot.activeTexture)
        GlStateManager._bindTexture(snapshot.boundTexture2d)
        GlStateManager._glUseProgram(snapshot.program)
        GlStateManager._glBindVertexArray(snapshot.vertexArray)

        boundTextureUnit = snapshot.activeTexture
        boundTextureId = snapshot.boundTexture2d
        boundProgramId = snapshot.program
        boundVertexArrayId = snapshot.vertexArray
        boundArrayBufferId = snapshot.arrayBuffer
    }

    private fun captureFramebuffer(snapshot: FramebufferSnapshot): FramebufferSnapshot {
        viewportBuffer.clear()
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewportBuffer)
        snapshot.drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        snapshot.readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
        snapshot.viewportX = viewportBuffer.get(0)
        snapshot.viewportY = viewportBuffer.get(1)
        snapshot.viewportWidth = viewportBuffer.get(2)
        snapshot.viewportHeight = viewportBuffer.get(3)
        return snapshot
    }

    private fun captureFramebuffer(snapshot: GlStateSnapshot): GlStateSnapshot {
        viewportBuffer.clear()
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewportBuffer)
        snapshot.drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        snapshot.readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
        snapshot.viewportX = viewportBuffer.get(0)
        snapshot.viewportY = viewportBuffer.get(1)
        snapshot.viewportWidth = viewportBuffer.get(2)
        snapshot.viewportHeight = viewportBuffer.get(3)
        return snapshot
    }

    private fun captureManagedMainFramebufferState(snapshot: GlStateSnapshot): GlStateSnapshot {
        viewportBuffer.clear()
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewportBuffer)
        snapshot.viewportX = viewportBuffer.get(0)
        snapshot.viewportY = viewportBuffer.get(1)
        snapshot.viewportWidth = viewportBuffer.get(2)
        snapshot.viewportHeight = viewportBuffer.get(3)

        ManagedStateTracker.capture(snapshot)
        snapshot.arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
        snapshot.blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB)
        snapshot.blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA)
        snapshot.program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        snapshot.vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
        return snapshot
    }

    private fun restoreFramebuffer(snapshot: FramebufferSnapshot) {
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, snapshot.drawFramebuffer)
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, snapshot.readFramebuffer)
        GL11.glViewport(snapshot.viewportX, snapshot.viewportY, snapshot.viewportWidth, snapshot.viewportHeight)
    }

    private fun restoreFramebuffer(snapshot: GlStateSnapshot) {
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, snapshot.drawFramebuffer)
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, snapshot.readFramebuffer)
        GL11.glViewport(snapshot.viewportX, snapshot.viewportY, snapshot.viewportWidth, snapshot.viewportHeight)
    }

    internal inline fun render(action: () -> Unit) {
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

    internal inline fun renderToMainFramebuffer(action: () -> Unit) {
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

    private fun applyGuiState() {
        GlStateManager._disableDepthTest()
        GlStateManager._disableCull()
        GlStateManager._enableBlend()
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD)
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO)
    }

    internal fun useProgram(programId: Int) {
        if (boundProgramId == programId) return
        GlStateManager._glUseProgram(programId)
        boundProgramId = programId
    }

    internal fun bindVertexArray(vertexArrayId: Int) {
        if (boundVertexArrayId == vertexArrayId) return
        GlStateManager._glBindVertexArray(vertexArrayId)
        boundVertexArrayId = vertexArrayId
    }

    internal fun bindArrayBuffer(bufferId: Int) {
        if (boundArrayBufferId == bufferId) return
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId)
        boundArrayBufferId = bufferId
    }

    fun bindTexture(texture: TextureHandle, unit: Int = 0) = bindTexture(texture.id, unit)

    internal fun bindTexture(textureId: Int, unit: Int = 0) {
        val activeUnit = GL13.GL_TEXTURE0 + unit
        if (boundTextureUnit != activeUnit) {
            GlStateManager._activeTexture(activeUnit)
            boundTextureUnit = activeUnit
            boundTextureId = -1
        }
        GL33C.glBindSampler(unit, 0)
        if (boundTextureId == textureId) return
        GlStateManager._bindTexture(textureId)
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

    private fun invalidateBindingCache() {
        boundArrayBufferId = -1
        boundTextureUnit = -1
        boundTextureId = -1
        boundProgramId = -1
        boundVertexArrayId = -1
    }

    private fun bindFramebuffer(framebufferId: Int, width: Int, height: Int) {
        GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, framebufferId)
        GlStateManager._viewport(0, 0, width, height)
    }

    private fun acquireTransientStateSnapshot(): GlStateSnapshot {
        if (transientStateDepth == transientStateSnapshots.size) {
            transientStateSnapshots.add(GlStateSnapshot())
        }
        return transientStateSnapshots[transientStateDepth++]
    }

    private fun releaseTransientStateSnapshot() {
        if (transientStateDepth > 0) {
            transientStateDepth--
        }
    }
}
