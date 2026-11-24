package sweetie.evaware.luma.backend.gl

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL33

internal class GlStateGuard(
    val preserveState: Boolean
) {
    val snapshot = GlStateSnapshot()
    val viewportBuffer = IntArray(4)
    private val scissorBuffer = IntArray(4)
    private val colorMaskBuffer = IntArray(4)
    private val scalarStateBuffer = BufferUtils.createIntBuffer(1)

    var dirtyRestoreMask = 0
        private set
    var preserveGuiDefaultsApplied = false
    var ownedFixedStateKnown = false

    fun beginFrame() {
        dirtyRestoreMask = 0
        preserveGuiDefaultsApplied = false
        snapshot.clearTextureUnits()
    }

    fun resetAfterFrame() {
        dirtyRestoreMask = 0
        preserveGuiDefaultsApplied = false
    }

    fun isDirty(bit: Int): Boolean = (dirtyRestoreMask and bit) != 0

    fun markDirty(bit: Int) {
        dirtyRestoreMask = dirtyRestoreMask or bit
    }

    fun getInteger(parameter: Int): Int {
        GL11.glGetIntegerv(parameter, scalarStateBuffer)
        return scalarStateBuffer.get(0)
    }

    fun saveProgramAndMatchesHost(frameActive: Boolean, programId: Int): Boolean {
        if (!preserveState || !frameActive || (dirtyRestoreMask and DIRTY_PROGRAM) != 0) return false
        val hostProgram = getInteger(GL20.GL_CURRENT_PROGRAM)
        snapshot.program = hostProgram
        dirtyRestoreMask = dirtyRestoreMask or DIRTY_PROGRAM
        return hostProgram == programId
    }

    fun saveVaoAndMatchesHost(frameActive: Boolean, vertexArrayId: Int): Boolean {
        if (!preserveState || !frameActive || (dirtyRestoreMask and DIRTY_VAO) != 0) return false
        val hostVao = getInteger(GL30.GL_VERTEX_ARRAY_BINDING)
        snapshot.vertexArray = hostVao
        dirtyRestoreMask = dirtyRestoreMask or DIRTY_VAO
        return hostVao == vertexArrayId
    }

    fun saveArrayBufferIfNeeded(frameActive: Boolean) {
        if (preserveState && frameActive && (dirtyRestoreMask and DIRTY_ARRAY_BUFFER) == 0) {
            snapshot.arrayBuffer = getInteger(GL15.GL_ARRAY_BUFFER_BINDING)
            dirtyRestoreMask = dirtyRestoreMask or DIRTY_ARRAY_BUFFER
        }
    }

    fun saveActiveTextureAndMatchesHost(frameActive: Boolean, unit: Int): Boolean {
        if (!preserveState || !frameActive || (dirtyRestoreMask and DIRTY_ACTIVE_TEXTURE) != 0) return false
        val hostActive = getInteger(GL13.GL_ACTIVE_TEXTURE)
        snapshot.activeTexture = hostActive
        dirtyRestoreMask = dirtyRestoreMask or DIRTY_ACTIVE_TEXTURE
        return (hostActive - GL13.GL_TEXTURE0) == unit
    }

    fun saveBlendEnableAndMatchesHost(frameActive: Boolean, enabled: Boolean): Boolean {
        if (!preserveState || !frameActive || (dirtyRestoreMask and DIRTY_BLEND_ENABLE) != 0) return false
        val host = GL11.glIsEnabled(GL11.GL_BLEND)
        snapshot.blendEnabled = host
        dirtyRestoreMask = dirtyRestoreMask or DIRTY_BLEND_ENABLE
        return host == enabled
    }

    fun saveBlendFuncAndApplyIfFirst(
        frameActive: Boolean,
        srcRgb: Int,
        dstRgb: Int,
        srcAlpha: Int,
        dstAlpha: Int,
        eqRgb: Int,
        eqAlpha: Int
    ): Boolean {
        if (!preserveState || !frameActive || (dirtyRestoreMask and DIRTY_BLEND_FUNC) != 0) return false
        snapshot.blendSrcRgb = getInteger(GL14.GL_BLEND_SRC_RGB)
        snapshot.blendDstRgb = getInteger(GL14.GL_BLEND_DST_RGB)
        snapshot.blendSrcAlpha = getInteger(GL14.GL_BLEND_SRC_ALPHA)
        snapshot.blendDstAlpha = getInteger(GL14.GL_BLEND_DST_ALPHA)
        snapshot.blendEquationRgb = getInteger(GL20.GL_BLEND_EQUATION_RGB)
        snapshot.blendEquationAlpha = getInteger(GL20.GL_BLEND_EQUATION_ALPHA)
        dirtyRestoreMask = dirtyRestoreMask or DIRTY_BLEND_FUNC
        if (
            snapshot.blendSrcRgb != srcRgb ||
            snapshot.blendDstRgb != dstRgb ||
            snapshot.blendSrcAlpha != srcAlpha ||
            snapshot.blendDstAlpha != dstAlpha
        ) {
            GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha)
        }
        if (snapshot.blendEquationRgb != eqRgb || snapshot.blendEquationAlpha != eqAlpha) {
            GL20.glBlendEquationSeparate(eqRgb, eqAlpha)
        }
        return true
    }

    fun saveColorMaskAndMatchesHost(
        frameActive: Boolean,
        red: Boolean,
        green: Boolean,
        blue: Boolean,
        alpha: Boolean
    ): Boolean {
        if (!preserveState || !frameActive || (dirtyRestoreMask and DIRTY_COLOR_MASK) != 0) return false
        captureColorMask()
        return snapshot.colorMaskRed == red &&
            snapshot.colorMaskGreen == green &&
            snapshot.colorMaskBlue == blue &&
            snapshot.colorMaskAlpha == alpha
    }

    fun ensureColorMaskWritable(frameActive: Boolean) {
        if (!preserveState || !frameActive || (dirtyRestoreMask and DIRTY_COLOR_MASK) != 0) return
        captureColorMask()
        if (!snapshot.colorMaskRed || !snapshot.colorMaskGreen || !snapshot.colorMaskBlue || !snapshot.colorMaskAlpha) {
            GL11.glColorMask(true, true, true, true)
        }
    }

    fun saveDepthEnableAndMatchesHost(frameActive: Boolean, enabled: Boolean): Boolean {
        if (!preserveState || !frameActive || (dirtyRestoreMask and DIRTY_DEPTH_ENABLE) != 0) return false
        val host = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        snapshot.depthEnabled = host
        dirtyRestoreMask = dirtyRestoreMask or DIRTY_DEPTH_ENABLE
        return host == enabled
    }

    fun saveDepthWriteIfNeeded(frameActive: Boolean) {
        if (preserveState && frameActive && (dirtyRestoreMask and DIRTY_DEPTH_WRITE) == 0) {
            snapshot.depthWrite = getInteger(GL11.GL_DEPTH_WRITEMASK) != 0
            dirtyRestoreMask = dirtyRestoreMask or DIRTY_DEPTH_WRITE
        }
    }

    fun saveDepthWriteAndMatchesHost(frameActive: Boolean, enabled: Boolean): Boolean {
        if (!preserveState || !frameActive || (dirtyRestoreMask and DIRTY_DEPTH_WRITE) != 0) return false
        val host = getInteger(GL11.GL_DEPTH_WRITEMASK) != 0
        snapshot.depthWrite = host
        dirtyRestoreMask = dirtyRestoreMask or DIRTY_DEPTH_WRITE
        return host == enabled
    }

    fun saveDepthFuncAndMatchesHost(frameActive: Boolean, glFunc: Int): Boolean {
        if (!preserveState || !frameActive || (dirtyRestoreMask and DIRTY_DEPTH_FUNC) != 0) return false
        val host = getInteger(GL11.GL_DEPTH_FUNC)
        snapshot.depthFunc = host
        dirtyRestoreMask = dirtyRestoreMask or DIRTY_DEPTH_FUNC
        return host == glFunc
    }

    fun saveStencilEnableAndMatchesHost(frameActive: Boolean, enabled: Boolean): Boolean {
        if (!preserveState || !frameActive || (dirtyRestoreMask and DIRTY_STENCIL_ENABLE) != 0) return false
        val host = GL11.glIsEnabled(GL11.GL_STENCIL_TEST)
        snapshot.stencilEnabled = host
        dirtyRestoreMask = dirtyRestoreMask or DIRTY_STENCIL_ENABLE
        return host == enabled
    }

    fun saveStencilFuncIfNeeded(frameActive: Boolean) {
        if (preserveState && frameActive && (dirtyRestoreMask and DIRTY_STENCIL_FUNC) == 0) {
            snapshot.stencilFrontFunc = getInteger(GL11.GL_STENCIL_FUNC)
            snapshot.stencilFrontRef = getInteger(GL11.GL_STENCIL_REF)
            snapshot.stencilFrontValueMask = getInteger(GL11.GL_STENCIL_VALUE_MASK)
            snapshot.stencilBackFunc = getInteger(GL20.GL_STENCIL_BACK_FUNC)
            snapshot.stencilBackRef = getInteger(GL20.GL_STENCIL_BACK_REF)
            snapshot.stencilBackValueMask = getInteger(GL20.GL_STENCIL_BACK_VALUE_MASK)
            dirtyRestoreMask = dirtyRestoreMask or DIRTY_STENCIL_FUNC
        }
    }

    fun saveStencilOpIfNeeded(frameActive: Boolean) {
        if (preserveState && frameActive && (dirtyRestoreMask and DIRTY_STENCIL_OP) == 0) {
            snapshot.stencilFrontFail = getInteger(GL11.GL_STENCIL_FAIL)
            snapshot.stencilFrontDepthFail = getInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL)
            snapshot.stencilFrontPass = getInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS)
            snapshot.stencilBackFail = getInteger(GL20.GL_STENCIL_BACK_FAIL)
            snapshot.stencilBackDepthFail = getInteger(GL20.GL_STENCIL_BACK_PASS_DEPTH_FAIL)
            snapshot.stencilBackPass = getInteger(GL20.GL_STENCIL_BACK_PASS_DEPTH_PASS)
            dirtyRestoreMask = dirtyRestoreMask or DIRTY_STENCIL_OP
        }
    }

    fun saveStencilMaskIfNeeded(frameActive: Boolean) {
        if (preserveState && frameActive && (dirtyRestoreMask and DIRTY_STENCIL_MASK) == 0) {
            snapshot.stencilFrontWriteMask = getInteger(GL11.GL_STENCIL_WRITEMASK)
            snapshot.stencilBackWriteMask = getInteger(GL20.GL_STENCIL_BACK_WRITEMASK)
            dirtyRestoreMask = dirtyRestoreMask or DIRTY_STENCIL_MASK
        }
    }

    fun saveCullAndMatchesHost(frameActive: Boolean, enabled: Boolean): Boolean {
        if (!preserveState || !frameActive || (dirtyRestoreMask and DIRTY_CULL) != 0) return false
        val host = GL11.glIsEnabled(GL11.GL_CULL_FACE)
        snapshot.cullEnabled = host
        dirtyRestoreMask = dirtyRestoreMask or DIRTY_CULL
        return host == enabled
    }

    fun saveScissorEnableIfNeeded(frameActive: Boolean): Boolean {
        if (!preserveState || !frameActive || (dirtyRestoreMask and DIRTY_SCISSOR_ENABLE) != 0) return false
        snapshot.scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
        dirtyRestoreMask = dirtyRestoreMask or DIRTY_SCISSOR_ENABLE
        return true
    }

    fun saveScissorBoxIfNeeded(frameActive: Boolean) {
        if (preserveState && frameActive && (dirtyRestoreMask and DIRTY_SCISSOR_BOX) == 0) {
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBuffer)
            snapshot.scissorX = scissorBuffer[0]
            snapshot.scissorY = scissorBuffer[1]
            snapshot.scissorWidth = scissorBuffer[2]
            snapshot.scissorHeight = scissorBuffer[3]
            dirtyRestoreMask = dirtyRestoreMask or DIRTY_SCISSOR_BOX
        }
    }

    fun ensureHostScissorDisabled(frameActive: Boolean, scissorEnabled: Boolean) {
        if (!scissorEnabled && saveScissorEnableIfNeeded(frameActive) && snapshot.scissorEnabled) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST)
        }
    }

    fun captureAllUnsavedState(ensureHostViewport: () -> Unit, markHostFramebufferCaptured: () -> Unit) {
        val mask = dirtyRestoreMask
        if ((mask and DIRTY_FRAMEBUFFER) == 0) {
            ensureHostViewport()
            snapshot.drawFramebuffer = getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
            snapshot.readFramebuffer = getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
            markHostFramebufferCaptured()
        }
        if ((mask and DIRTY_BLEND_ENABLE) == 0) snapshot.blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND)
        if ((mask and DIRTY_DEPTH_ENABLE) == 0) snapshot.depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        if ((mask and DIRTY_DEPTH_WRITE) == 0) snapshot.depthWrite = getInteger(GL11.GL_DEPTH_WRITEMASK) != 0
        if ((mask and DIRTY_DEPTH_FUNC) == 0) snapshot.depthFunc = getInteger(GL11.GL_DEPTH_FUNC)
        if ((mask and DIRTY_CULL) == 0) snapshot.cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE)
        if ((mask and DIRTY_SCISSOR_ENABLE) == 0) snapshot.scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
        if ((mask and DIRTY_SCISSOR_BOX) == 0) {
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBuffer)
            snapshot.scissorX = scissorBuffer[0]
            snapshot.scissorY = scissorBuffer[1]
            snapshot.scissorWidth = scissorBuffer[2]
            snapshot.scissorHeight = scissorBuffer[3]
        }
        if ((mask and DIRTY_COLOR_MASK) == 0) captureColorMask()
        if ((mask and DIRTY_BLEND_FUNC) == 0) {
            snapshot.blendSrcRgb = getInteger(GL14.GL_BLEND_SRC_RGB)
            snapshot.blendDstRgb = getInteger(GL14.GL_BLEND_DST_RGB)
            snapshot.blendSrcAlpha = getInteger(GL14.GL_BLEND_SRC_ALPHA)
            snapshot.blendDstAlpha = getInteger(GL14.GL_BLEND_DST_ALPHA)
            snapshot.blendEquationRgb = getInteger(GL20.GL_BLEND_EQUATION_RGB)
            snapshot.blendEquationAlpha = getInteger(GL20.GL_BLEND_EQUATION_ALPHA)
        }
        if ((mask and DIRTY_PROGRAM) == 0) snapshot.program = getInteger(GL20.GL_CURRENT_PROGRAM)
        if ((mask and DIRTY_VAO) == 0) snapshot.vertexArray = getInteger(GL30.GL_VERTEX_ARRAY_BINDING)
        if ((mask and DIRTY_ARRAY_BUFFER) == 0) snapshot.arrayBuffer = getInteger(GL15.GL_ARRAY_BUFFER_BINDING)
        if ((mask and DIRTY_ACTIVE_TEXTURE) == 0) snapshot.activeTexture = getInteger(GL13.GL_ACTIVE_TEXTURE)
        if ((mask and DIRTY_STENCIL_ENABLE) == 0) snapshot.stencilEnabled = GL11.glIsEnabled(GL11.GL_STENCIL_TEST)
        if ((mask and DIRTY_STENCIL_FUNC) == 0) {
            snapshot.stencilFrontFunc = getInteger(GL11.GL_STENCIL_FUNC)
            snapshot.stencilFrontRef = getInteger(GL11.GL_STENCIL_REF)
            snapshot.stencilFrontValueMask = getInteger(GL11.GL_STENCIL_VALUE_MASK)
            snapshot.stencilBackFunc = getInteger(GL20.GL_STENCIL_BACK_FUNC)
            snapshot.stencilBackRef = getInteger(GL20.GL_STENCIL_BACK_REF)
            snapshot.stencilBackValueMask = getInteger(GL20.GL_STENCIL_BACK_VALUE_MASK)
        }
        if ((mask and DIRTY_STENCIL_OP) == 0) {
            snapshot.stencilFrontFail = getInteger(GL11.GL_STENCIL_FAIL)
            snapshot.stencilFrontDepthFail = getInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL)
            snapshot.stencilFrontPass = getInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS)
            snapshot.stencilBackFail = getInteger(GL20.GL_STENCIL_BACK_FAIL)
            snapshot.stencilBackDepthFail = getInteger(GL20.GL_STENCIL_BACK_PASS_DEPTH_FAIL)
            snapshot.stencilBackPass = getInteger(GL20.GL_STENCIL_BACK_PASS_DEPTH_PASS)
        }
        if ((mask and DIRTY_STENCIL_MASK) == 0) {
            snapshot.stencilFrontWriteMask = getInteger(GL11.GL_STENCIL_WRITEMASK)
            snapshot.stencilBackWriteMask = getInteger(GL20.GL_STENCIL_BACK_WRITEMASK)
        }
        dirtyRestoreMask = ALL_DIRTY_MASK
    }

    fun restoreState() {
        val mask = dirtyRestoreMask
        val texCount = snapshot.textureUnitCount
        if (mask == 0 && texCount == 0) return

        if ((mask and DIRTY_FRAMEBUFFER) != 0) {
            if (snapshot.drawFramebuffer == snapshot.readFramebuffer) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, snapshot.drawFramebuffer)
            } else {
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, snapshot.drawFramebuffer)
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, snapshot.readFramebuffer)
            }
            GL11.glViewport(snapshot.viewportX, snapshot.viewportY, snapshot.viewportWidth, snapshot.viewportHeight)
        }
        if ((mask and DIRTY_BLEND_ENABLE) != 0) {
            if (snapshot.blendEnabled) GL11.glEnable(GL11.GL_BLEND) else GL11.glDisable(GL11.GL_BLEND)
        }
        if ((mask and DIRTY_DEPTH_ENABLE) != 0) {
            if (snapshot.depthEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST) else GL11.glDisable(GL11.GL_DEPTH_TEST)
        }
        if ((mask and DIRTY_DEPTH_WRITE) != 0) {
            GL11.glDepthMask(snapshot.depthWrite)
        }
        if ((mask and DIRTY_DEPTH_FUNC) != 0) {
            GL11.glDepthFunc(snapshot.depthFunc)
        }
        if ((mask and DIRTY_STENCIL_ENABLE) != 0) {
            if (snapshot.stencilEnabled) GL11.glEnable(GL11.GL_STENCIL_TEST) else GL11.glDisable(GL11.GL_STENCIL_TEST)
        }
        if ((mask and DIRTY_STENCIL_FUNC) != 0) {
            GL20.glStencilFuncSeparate(
                GL11.GL_FRONT,
                snapshot.stencilFrontFunc,
                snapshot.stencilFrontRef,
                snapshot.stencilFrontValueMask
            )
            GL20.glStencilFuncSeparate(
                GL11.GL_BACK,
                snapshot.stencilBackFunc,
                snapshot.stencilBackRef,
                snapshot.stencilBackValueMask
            )
        }
        if ((mask and DIRTY_STENCIL_OP) != 0) {
            GL20.glStencilOpSeparate(
                GL11.GL_FRONT,
                snapshot.stencilFrontFail,
                snapshot.stencilFrontDepthFail,
                snapshot.stencilFrontPass
            )
            GL20.glStencilOpSeparate(
                GL11.GL_BACK,
                snapshot.stencilBackFail,
                snapshot.stencilBackDepthFail,
                snapshot.stencilBackPass
            )
        }
        if ((mask and DIRTY_STENCIL_MASK) != 0) {
            GL20.glStencilMaskSeparate(GL11.GL_FRONT, snapshot.stencilFrontWriteMask)
            GL20.glStencilMaskSeparate(GL11.GL_BACK, snapshot.stencilBackWriteMask)
        }
        if ((mask and DIRTY_CULL) != 0) {
            if (snapshot.cullEnabled) GL11.glEnable(GL11.GL_CULL_FACE) else GL11.glDisable(GL11.GL_CULL_FACE)
        }
        if ((mask and DIRTY_SCISSOR_ENABLE) != 0) {
            if (snapshot.scissorEnabled) GL11.glEnable(GL11.GL_SCISSOR_TEST) else GL11.glDisable(GL11.GL_SCISSOR_TEST)
        }
        if ((mask and DIRTY_SCISSOR_BOX) != 0) {
            GL11.glScissor(snapshot.scissorX, snapshot.scissorY, snapshot.scissorWidth, snapshot.scissorHeight)
        }
        if ((mask and DIRTY_COLOR_MASK) != 0) {
            GL11.glColorMask(
                snapshot.colorMaskRed,
                snapshot.colorMaskGreen,
                snapshot.colorMaskBlue,
                snapshot.colorMaskAlpha
            )
        }
        if ((mask and DIRTY_BLEND_FUNC) != 0) {
            GL14.glBlendFuncSeparate(
                snapshot.blendSrcRgb,
                snapshot.blendDstRgb,
                snapshot.blendSrcAlpha,
                snapshot.blendDstAlpha
            )
            GL20.glBlendEquationSeparate(snapshot.blendEquationRgb, snapshot.blendEquationAlpha)
        }
        if ((mask and DIRTY_PROGRAM) != 0) {
            GL20.glUseProgram(snapshot.program)
        }
        if ((mask and DIRTY_VAO) != 0) {
            GL30.glBindVertexArray(snapshot.vertexArray)
        }
        if ((mask and DIRTY_ARRAY_BUFFER) != 0) {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, snapshot.arrayBuffer)
        }
        for (index in 0 until texCount) {
            val unit = snapshot.textureUnit(index)
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit)
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, snapshot.boundTexture(index))
            GL33.glBindSampler(unit, snapshot.samplerBinding(index))
        }
        if ((mask and DIRTY_ACTIVE_TEXTURE) != 0 || texCount > 0) {
            GL13.glActiveTexture(snapshot.activeTexture)
        }
    }

    private fun captureColorMask() {
        GL11.glGetIntegerv(GL11.GL_COLOR_WRITEMASK, colorMaskBuffer)
        snapshot.colorMaskRed = colorMaskBuffer[0] != 0
        snapshot.colorMaskGreen = colorMaskBuffer[1] != 0
        snapshot.colorMaskBlue = colorMaskBuffer[2] != 0
        snapshot.colorMaskAlpha = colorMaskBuffer[3] != 0
        dirtyRestoreMask = dirtyRestoreMask or DIRTY_COLOR_MASK
    }

    companion object {
        const val DIRTY_FRAMEBUFFER = 1 shl 0
        const val DIRTY_BLEND_ENABLE = 1 shl 1
        const val DIRTY_BLEND_FUNC = 1 shl 2
        const val DIRTY_DEPTH_ENABLE = 1 shl 3
        const val DIRTY_DEPTH_WRITE = 1 shl 4
        const val DIRTY_DEPTH_FUNC = 1 shl 5
        const val DIRTY_CULL = 1 shl 6
        const val DIRTY_SCISSOR_ENABLE = 1 shl 7
        const val DIRTY_SCISSOR_BOX = 1 shl 8
        const val DIRTY_COLOR_MASK = 1 shl 9
        const val DIRTY_PROGRAM = 1 shl 10
        const val DIRTY_VAO = 1 shl 11
        const val DIRTY_ARRAY_BUFFER = 1 shl 12
        const val DIRTY_ACTIVE_TEXTURE = 1 shl 13
        const val DIRTY_STENCIL_ENABLE = 1 shl 14
        const val DIRTY_STENCIL_FUNC = 1 shl 15
        const val DIRTY_STENCIL_OP = 1 shl 16
        const val DIRTY_STENCIL_MASK = 1 shl 17
        const val ALL_DIRTY_MASK = (1 shl 18) - 1
    }
}
