package sweetie.evaware.luma.backend.gl

import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.*
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.api.RenderBackend
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.uniform.*
import sweetie.evaware.luma.vertex.VertexLayout
import java.awt.image.BufferedImage
import java.nio.FloatBuffer

class Backend : RenderBackend {
    private val frameSnapshot = GlStateSnapshot()
    private val viewportBuffer = IntArray(4)

    private class TargetFrame(
        val target: GlRenderTarget?,
        var clearColor: FloatArray?
    )
    private val targetStack = ArrayList<TargetFrame>()

    private var boundProgramId = -1
    private var boundVertexArrayId = -1
    private var boundArrayBufferId = -1

    private fun useProgram(programId: Int) {
        if (boundProgramId == programId) return
        GL20.glUseProgram(programId)
        boundProgramId = programId
    }

    private fun bindVertexArray(vertexArrayId: Int) {
        if (boundVertexArrayId == vertexArrayId) return
        GL30.glBindVertexArray(vertexArrayId)
        boundVertexArrayId = vertexArrayId
    }

    private fun bindArrayBuffer(bufferId: Int) {
        if (boundArrayBufferId == bufferId) return
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId)
        boundArrayBufferId = bufferId
    }

    private fun invalidateBindingCache() {
        boundProgramId = -1
        boundVertexArrayId = -1
        boundArrayBufferId = -1
    }

    override fun beginFrame() {
        captureState(frameSnapshot)
        applyGuiState()
        invalidateBindingCache()
        targetStack.clear()
        targetStack.add(TargetFrame(null, null))
        Luma.platform.swapToMainFramebuffer(Luma)
    }

    override fun endFrame() {
        restoreState(frameSnapshot)
        invalidateBindingCache()
    }

    override fun createProgram(
        vertexSource: String,
        fragmentSource: String,
        layout: VertexLayout
    ): ProgramHandle {
        var vertShader = 0
        var fragShader = 0
        var programId = 0
        var loaded = false

        try {
            vertShader = compile(GL20.GL_VERTEX_SHADER, vertexSource)
            fragShader = compile(GL20.GL_FRAGMENT_SHADER, fragmentSource)

            programId = GL20.glCreateProgram()
            GL20.glAttachShader(programId, vertShader)
            GL20.glAttachShader(programId, fragShader)

            for (index in 0 until layout.size()) {
                val layoutPos = layout.layoutPos(index)
                GL20.glBindAttribLocation(programId, layoutPos, "a$layoutPos")
            }

            GL20.glLinkProgram(programId)

            if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                val log = GL20.glGetProgramInfoLog(programId)
                error("Failed to link program: $log")
            }

            loaded = true
            return Program(programId, layout)
        } finally {
            if (vertShader != 0) GL20.glDeleteShader(vertShader)
            if (fragShader != 0) GL20.glDeleteShader(fragShader)
            if (!loaded) {
                if (programId != 0) GL20.glDeleteProgram(programId)
            }
        }
    }

    override fun bindProgram(program: ProgramHandle) {
        val glProgram = program as Program
        useProgram(glProgram.programId)
        bindVertexArray(glProgram.vertexBuffer.vao)
        bindArrayBuffer(glProgram.vertexBuffer.vbo)
    }

    override fun createTexture(image: BufferedImage, mipmap: Boolean): TextureHandle {
        return GlTexture.create(image, mipmap)
    }

    override fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage) {
        (texture as GlTexture).update(x, y, image)
    }

    override fun bindTexture(texture: TextureHandle, unit: Int) {
        (texture as GlTexture).bind(unit)
    }

    override fun draw(
        program: ProgramHandle,
        vertices: FloatBuffer,
        vertexCount: Int,
        uniforms: ShaderUniforms,
        texture: TextureHandle?,
        primitiveType: Int
    ) {
        val glProgram = program as Program
        
        useProgram(glProgram.programId)
        bindVertexArray(glProgram.vertexBuffer.vao)
        bindArrayBuffer(glProgram.vertexBuffer.vbo)

        glProgram.vertexBuffer.upload(vertices)

        if (texture != null) {
            bindTexture(texture, 0)
        }

        val prepared = glProgram.getPreparedUniforms(uniforms)
        for (i in prepared.indices) {
            val p = prepared[i]
            val handle = p.uniform.getHandle(uniforms)
            if (handle != null && handle.isDirty) {
                p.uniform.upload(p.location, uniforms)
                handle.isDirty = false
            }
        }

        val glMode = when (primitiveType) {
            0 -> GL11.GL_TRIANGLES
            1 -> GL11.GL_LINES
            2 -> GL30.GL_TRIANGLE_FAN
            else -> GL11.GL_TRIANGLES
        }
        GL11.glDrawArrays(glMode, 0, vertexCount)
    }

    override fun createRenderTarget(
        width: Int,
        height: Int,
        useDepth: Boolean,
        format: RenderTargetFormat
    ): RenderTargetHandle {
        return GlRenderTarget.create(width, height)
    }

    override fun beginRenderTarget(
        target: RenderTargetHandle,
        clearColor: FloatArray?
    ) {
        val glTarget = target as GlRenderTarget
        targetStack.add(TargetFrame(glTarget, clearColor))
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, glTarget.fbo)
        GL11.glViewport(0, 0, glTarget.width, glTarget.height)
        if (clearColor != null) {
            GL11.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3])
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
        }
    }

    override fun endRenderTarget() {
        if (targetStack.size > 1) {
            targetStack.removeAt(targetStack.size - 1)
        }
        val active = targetStack.last()
        if (active.target != null) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, active.target.fbo)
            GL11.glViewport(0, 0, active.target.width, active.target.height)
        } else {
            Luma.platform.swapToMainFramebuffer(Luma)
            GL11.glViewport(frameSnapshot.viewportX, frameSnapshot.viewportY, frameSnapshot.viewportWidth, frameSnapshot.viewportHeight)
        }
    }

    override fun close() {}

    override fun hasContext(): Boolean = GLFW.glfwGetCurrentContext() != 0L

    private fun compile(type: Int, source: String): Int {
        val shaderId = GL20.glCreateShader(type)
        GL20.glShaderSource(shaderId, source)
        GL20.glCompileShader(shaderId)

        if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            val log = GL20.glGetShaderInfoLog(shaderId)
            GL20.glDeleteShader(shaderId)
            error("Failed to compile GLSL shader: $log")
        }

        return shaderId
    }

    private fun captureState(snapshot: GlStateSnapshot) {
        val viewport = viewportBuffer
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport)
        snapshot.viewportX = viewport[0]
        snapshot.viewportY = viewport[1]
        snapshot.viewportWidth = viewport[2]
        snapshot.viewportHeight = viewport[3]

        snapshot.drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        snapshot.readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)

        snapshot.blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND)
        snapshot.depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        snapshot.cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE)

        snapshot.blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
        snapshot.blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        snapshot.blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
        snapshot.blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        snapshot.blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB)
        snapshot.blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA)

        snapshot.program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        snapshot.vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)

        snapshot.activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
        for (i in 0..1) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i)
            snapshot.boundTextures[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        }
        GL13.glActiveTexture(snapshot.activeTexture)
    }

    private fun restoreState(snapshot: GlStateSnapshot) {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, snapshot.drawFramebuffer)
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, snapshot.readFramebuffer)
        GL11.glViewport(snapshot.viewportX, snapshot.viewportY, snapshot.viewportWidth, snapshot.viewportHeight)

        if (snapshot.blendEnabled) GL11.glEnable(GL11.GL_BLEND) else GL11.glDisable(GL11.GL_BLEND)
        if (snapshot.depthEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST) else GL11.glDisable(GL11.GL_DEPTH_TEST)
        if (snapshot.cullEnabled) GL11.glEnable(GL11.GL_CULL_FACE) else GL11.glDisable(GL11.GL_CULL_FACE)

        GL14.glBlendFuncSeparate(snapshot.blendSrcRgb, snapshot.blendDstRgb, snapshot.blendSrcAlpha, snapshot.blendDstAlpha)
        GL20.glBlendEquationSeparate(snapshot.blendEquationRgb, snapshot.blendEquationAlpha)

        GL20.glUseProgram(snapshot.program)
        GL30.glBindVertexArray(snapshot.vertexArray)

        for (i in 0..1) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i)
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, snapshot.boundTextures[i])
        }
        GL13.glActiveTexture(snapshot.activeTexture)
    }

    private fun applyGuiState() {
        GL11.glDisable(GL11.GL_DEPTH_TEST)
        GL11.glDisable(GL11.GL_CULL_FACE)
        GL11.glEnable(GL11.GL_BLEND)
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD)
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO)
    }
}
