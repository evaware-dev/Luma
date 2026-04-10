package sweetie.evaware.luma.runtime

import com.mojang.blaze3d.opengl.GlStateManager
import org.lwjgl.BufferUtils
import org.lwjgl.BufferUtils.createByteBuffer
import org.lwjgl.opengl.*
import java.nio.IntBuffer

internal class GlStateSnapshot {
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
    var scissorEnabled = false
    var scissorX = 0
    var scissorY = 0
    var scissorWidth = 0
    var scissorHeight = 0
    var depthMask = true
    var colorMaskRed = true
    var colorMaskGreen = true
    var colorMaskBlue = true
    var colorMaskAlpha = true
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

internal class GlStateGuard(
    private val bindingTracker: GlBindingTracker,
    private val updateFrameState: () -> Unit
) : FrameStateGuard {
    private val viewportBuffer: IntBuffer = BufferUtils.createIntBuffer(4)
    private val scissorBuffer: IntBuffer = BufferUtils.createIntBuffer(4)
    private val colorMaskBuffer = createByteBuffer(4)
    private val depthMaskBuffer = createByteBuffer(1)
    private val frameSnapshot = GlStateSnapshot()
    private val transientStateSnapshots = MutableList(4) { GlStateSnapshot() }
    private var transientStateDepth = 0
    private var frameStateCaptured = false

    override fun beginManagedFrame(bindFramebuffer: (() -> Unit)?) {
        captureManagedState(frameSnapshot)
        frameStateCaptured = true
        bindFramebuffer?.invoke()
        applyGuiState()
        bindingTracker.invalidate()
    }

    override fun endManagedFrame() {
        if (!frameStateCaptured) return
        restoreState(frameSnapshot)
        frameStateCaptured = false
    }

    override fun renderOutsideFrame(action: () -> Unit) {
        val snapshot = acquireTransientStateSnapshot()
        var captured = false
        try {
            captureState(snapshot)
            captured = true
            updateFrameState()
            applyGuiState()
            bindingTracker.invalidate()
            action()
        } finally {
            if (captured) {
                restoreState(snapshot)
            }
            releaseTransientStateSnapshot()
        }
    }

    override fun invalidateBindings() {
        bindingTracker.invalidate()
    }

    private fun captureState(snapshot: GlStateSnapshot) {
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
        captureAdditionalState(snapshot)
    }

    private fun captureManagedState(snapshot: GlStateSnapshot) {
        captureFramebuffer(snapshot)
        ManagedStateTracker.capture(snapshot)
        snapshot.arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
        snapshot.blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB)
        snapshot.blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA)
        snapshot.program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        snapshot.vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
        captureAdditionalState(snapshot)
    }

    private fun restoreState(snapshot: GlStateSnapshot) {
        restoreFramebuffer(snapshot)
        bindingTracker.bindArrayBuffer(snapshot.arrayBuffer)

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

        if (snapshot.scissorEnabled) {
            GlStateManager._enableScissorTest()
            GlStateManager._scissorBox(snapshot.scissorX, snapshot.scissorY, snapshot.scissorWidth, snapshot.scissorHeight)
        } else {
            GlStateManager._disableScissorTest()
        }
        GlStateManager._depthMask(snapshot.depthMask)
        GL11.glColorMask(
            snapshot.colorMaskRed,
            snapshot.colorMaskGreen,
            snapshot.colorMaskBlue,
            snapshot.colorMaskAlpha
        )
        GlStateManager._activeTexture(snapshot.activeTexture)
        GlStateManager._bindTexture(snapshot.boundTexture2d)
        GlStateManager._glUseProgram(snapshot.program)
        GlStateManager._glBindVertexArray(snapshot.vertexArray)
        bindingTracker.sync(snapshot)
    }

    private fun captureFramebuffer(snapshot: GlStateSnapshot) {
        viewportBuffer.clear()
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewportBuffer)
        snapshot.drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        snapshot.readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
        snapshot.viewportX = viewportBuffer.get(0)
        snapshot.viewportY = viewportBuffer.get(1)
        snapshot.viewportWidth = viewportBuffer.get(2)
        snapshot.viewportHeight = viewportBuffer.get(3)
    }

    private fun restoreFramebuffer(snapshot: GlStateSnapshot) {
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, snapshot.drawFramebuffer)
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, snapshot.readFramebuffer)
        GL11.glViewport(snapshot.viewportX, snapshot.viewportY, snapshot.viewportWidth, snapshot.viewportHeight)
    }

    private fun applyGuiState() {
        GlStateManager._disableDepthTest()
        GlStateManager._disableCull()
        GlStateManager._disableScissorTest()
        GlStateManager._depthMask(false)
        GlStateManager._enableBlend()
        GL11.glColorMask(true, true, true, true)
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD)
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO)
    }

    private fun captureAdditionalState(snapshot: GlStateSnapshot) {
        snapshot.scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
        scissorBuffer.clear()
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBuffer)
        snapshot.scissorX = scissorBuffer.get(0)
        snapshot.scissorY = scissorBuffer.get(1)
        snapshot.scissorWidth = scissorBuffer.get(2)
        snapshot.scissorHeight = scissorBuffer.get(3)

        depthMaskBuffer.clear()
        GL11.glGetBooleanv(GL11.GL_DEPTH_WRITEMASK, depthMaskBuffer)
        snapshot.depthMask = depthMaskBuffer.get(0).toInt() != 0

        colorMaskBuffer.clear()
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMaskBuffer)
        snapshot.colorMaskRed = colorMaskBuffer.get(0).toInt() != 0
        snapshot.colorMaskGreen = colorMaskBuffer.get(1).toInt() != 0
        snapshot.colorMaskBlue = colorMaskBuffer.get(2).toInt() != 0
        snapshot.colorMaskAlpha = colorMaskBuffer.get(3).toInt() != 0
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
