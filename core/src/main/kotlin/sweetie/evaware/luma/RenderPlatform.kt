package sweetie.evaware.luma

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30

interface RenderPlatform {
    fun getGuiScaledWidth(): Float
    fun getGuiScaledHeight(): Float
    fun getGuiScale(): Float
    fun getWindowHeight(): Float
    fun getViewport(viewport: IntArray): Boolean
    fun captureState(snapshot: Luma.GlStateSnapshot)
    fun restoreState(snapshot: Luma.GlStateSnapshot)
    fun getBoundTexture2d(): Int
    fun swapToMainFramebuffer(luma: Luma)

    fun activeTexture(texture: Int) { GL13.glActiveTexture(texture) }
    fun bindTexture2d(textureId: Int) { GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId) }
    fun useProgram(programId: Int) { GL20.glUseProgram(programId) }
    fun bindVertexArray(vertexArrayId: Int) { GL30.glBindVertexArray(vertexArrayId) }
    fun bindArrayBuffer(bufferId: Int) { GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId) }
    fun bindFramebuffer(target: Int, framebufferId: Int) { GL30.glBindFramebuffer(target, framebufferId) }
    fun viewport(x: Int, y: Int, width: Int, height: Int) { GL11.glViewport(x, y, width, height) }

    fun enableBlend() { GL11.glEnable(GL11.GL_BLEND) }
    fun disableBlend() { GL11.glDisable(GL11.GL_BLEND) }
    fun enableDepthTest() { GL11.glEnable(GL11.GL_DEPTH_TEST) }
    fun disableDepthTest() { GL11.glDisable(GL11.GL_DEPTH_TEST) }
    fun enableCull() { GL11.glEnable(GL11.GL_CULL_FACE) }
    fun disableCull() { GL11.glDisable(GL11.GL_CULL_FACE) }

    fun getActiveTextureUnit(): Int
    fun getBoundTextureForUnit(unit: Int): Int
    fun getSamplerForUnit(unit: Int): Int
    fun getActiveProgram(): Int
    fun getActiveVertexArray(): Int
    fun getActiveArrayBuffer(): Int
    fun getBlendEquationRgb(): Int
    fun getBlendEquationAlpha(): Int
}

object DefaultRenderPlatform : RenderPlatform {
    override fun getGuiScaledWidth(): Float = 960f
    override fun getGuiScaledHeight(): Float = 540f
    override fun getGuiScale(): Float = 1f
    override fun getWindowHeight(): Float = 540f

    private var activeTexUnit = GL13.GL_TEXTURE0
    private val textures = IntArray(32)
    private var program = 0
    private var vao = 0
    private var arrayBuffer = 0

    override fun getViewport(viewport: IntArray): Boolean = false

    override fun captureState(snapshot: Luma.GlStateSnapshot) {
        snapshot.blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND)
        snapshot.depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        snapshot.cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE)
        snapshot.blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
        snapshot.blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        snapshot.blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
        snapshot.blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        snapshot.drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        snapshot.readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
    }

    override fun restoreState(snapshot: Luma.GlStateSnapshot) {
        if (snapshot.blendEnabled) GL11.glEnable(GL11.GL_BLEND) else GL11.glDisable(GL11.GL_BLEND)
        if (snapshot.depthEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST) else GL11.glDisable(GL11.GL_DEPTH_TEST)
        if (snapshot.cullEnabled) GL11.glEnable(GL11.GL_CULL_FACE) else GL11.glDisable(GL11.GL_CULL_FACE)
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, snapshot.drawFramebuffer)
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, snapshot.readFramebuffer)
    }

    override fun getBoundTexture2d(): Int = textures[activeTexUnit - GL13.GL_TEXTURE0]
    override fun swapToMainFramebuffer(luma: Luma) { luma.bindFramebuffer(0, 960, 540) }

    override fun activeTexture(texture: Int) {
        activeTexUnit = texture
        GL13.glActiveTexture(texture)
    }

    override fun bindTexture2d(textureId: Int) {
        textures[activeTexUnit - GL13.GL_TEXTURE0] = textureId
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
    }

    override fun useProgram(programId: Int) {
        program = programId
        GL20.glUseProgram(programId)
    }

    override fun bindVertexArray(vertexArrayId: Int) {
        vao = vertexArrayId
        GL30.glBindVertexArray(vertexArrayId)
    }

    override fun bindArrayBuffer(bufferId: Int) {
        arrayBuffer = bufferId
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId)
    }

    override fun getActiveTextureUnit(): Int = activeTexUnit
    override fun getBoundTextureForUnit(unit: Int): Int = textures[unit]
    override fun getSamplerForUnit(unit: Int): Int = 0
    override fun getActiveProgram(): Int = program
    override fun getActiveVertexArray(): Int = vao
    override fun getActiveArrayBuffer(): Int = arrayBuffer
    override fun getBlendEquationRgb(): Int = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB)
    override fun getBlendEquationAlpha(): Int = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA)
}