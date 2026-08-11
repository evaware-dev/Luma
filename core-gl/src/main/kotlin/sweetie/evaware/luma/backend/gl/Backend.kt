package sweetie.evaware.luma.backend.gl

import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.*
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.api.RenderBackend
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.api.RenderTargetFilter
import sweetie.evaware.luma.uniform.*
import sweetie.evaware.luma.vertex.VertexLayout
import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import sweetie.evaware.luma.texture.RgbaTransferBuffer

class Backend(
    private val config: GlBackendConfig = GlBackendConfig()
) : RenderBackend {
    private val frameSnapshot = GlStateSnapshot()
    private val viewportBuffer = IntArray(4)
    private val colorMaskBuffer = IntArray(4)
    private val targetStack = ArrayList<GlRenderTarget?>()
    private val pixelUnpackState = GlPixelUnpackState(config.statePolicy == GlStatePolicy.PRESERVE)
    private val textureTransfer = RgbaTransferBuffer()

    private var boundProgramId = -1
    private var boundVertexArrayId = -1
    private var boundArrayBufferId = -1
    private val textureBindings = TextureBindingCache(config.initialTextureUnits)
    private var samplerBindings = IntArray(config.initialTextureUnits) { UNKNOWN_SAMPLER_BINDING }
    private var activeTextureUnit = -1
    private var frameActive = false

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
        textureBindings.invalidateAll()
        samplerBindings.fill(UNKNOWN_SAMPLER_BINDING)
    }

    override fun beginFrame() {
        if (config.statePolicy == GlStatePolicy.PRESERVE) {
            captureState(frameSnapshot)
            applyGuiState(frameSnapshot)
            activeTextureUnit = frameSnapshot.activeTexture - GL13.GL_TEXTURE0
        } else {
            prepareOwnedFrame()
        }
        invalidateBindingCache()
        targetStack.clear()
        targetStack.add(null)
        frameActive = true
    }

    override fun endFrame() {
        try {
            try {
                pixelUnpackState.restore()
            } finally {
                if (config.statePolicy == GlStatePolicy.PRESERVE) restoreState(frameSnapshot)
            }
        } finally {
            frameActive = false
            activeTextureUnit = -1
            targetStack.clear()
            invalidateBindingCache()
        }
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
        if (!frameActive) return GlTexture.create(image, mipmap)
        pixelUnpackState.prepare()
        val texture = GlTexture(GL11.glGenTextures(), image.width, image.height, mipmap)
        try {
            bindTextureForUpload(texture)
            texture.initializeBound(image, textureTransfer)
            return texture
        } catch (failure: Throwable) {
            texture.close()
            throw failure
        }
    }

    override fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage) {
        val glTexture = texture as GlTexture
        if (!frameActive) {
            glTexture.update(x, y, image)
            return
        }
        pixelUnpackState.prepare()
        bindTextureForUpload(glTexture)
        glTexture.updateBound(x, y, image, textureTransfer)
    }

    override fun bindTexture(texture: TextureHandle, unit: Int) {
        require(unit >= 0) { "Texture unit must be non-negative: $unit" }
        val glTexture = texture as GlTexture
        glTexture.requireOpen()
        if (textureBindings.isBound(unit, glTexture)) return

        if (frameActive) {
            prepareTextureUnit(unit)
            glTexture.bindCurrentUnit()
            ensureSamplerUnbound(unit)
        } else {
            glTexture.bind(unit)
        }
        textureBindings.bind(unit, glTexture)
    }

    override fun draw(
        program: ProgramHandle,
        vertices: FloatBuffer,
        vertexCount: Int,
        uniforms: ShaderUniforms,
        primitiveType: PrimitiveType
    ) {
        val glProgram = program as Program
        
        useProgram(glProgram.programId)
        bindVertexArray(glProgram.vertexBuffer.vao)
        bindArrayBuffer(glProgram.vertexBuffer.vbo)

        glProgram.vertexBuffer.upload(vertices)

        val prepared = glProgram.getPreparedUniforms(uniforms)
        for (i in prepared.indices) {
            val p = prepared[i]
            val handle = p.uniform.getHandle(uniforms)
            if (handle != null && handle.isDirty) {
                p.uniform.upload(p.location, uniforms)
                handle.isDirty = false
            }
        }

        when {
            glProgram.vertexBuffer.layout.instanced -> {
                GL31.glDrawArraysInstanced(
                    GL11.GL_TRIANGLES, 0, glProgram.vertexBuffer.layout.baseVertexCount, vertexCount
                )
            }
            primitiveType == PrimitiveType.QUADS -> {
                val indexCount = glProgram.vertexBuffer.bindQuadIndices(vertexCount)
                GL11.glDrawElements(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, 0L)
            }
            primitiveType == PrimitiveType.LINES -> GL11.glDrawArrays(GL11.GL_LINES, 0, vertexCount)
            else -> GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount)
        }
    }

    override fun createRenderTarget(
        width: Int,
        height: Int,
        useDepth: Boolean,
        format: RenderTargetFormat
    ): RenderTargetHandle = createRenderTarget(width, height, useDepth, format, RenderTargetFilter.NEAREST)

    override fun createRenderTarget(
        width: Int,
        height: Int,
        useDepth: Boolean,
        format: RenderTargetFormat,
        filter: RenderTargetFilter
    ): RenderTargetHandle {
        return GlRenderTarget.create(width, height, useDepth, format, filter)
    }

    override fun beginRenderTarget(
        target: RenderTargetHandle,
        clearColor: FloatArray?
    ) {
        check(frameActive) { "Cannot begin a render target outside a frame" }
        val glTarget = target as GlRenderTarget
        targetStack.add(glTarget)
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, glTarget.fbo)
        GL11.glViewport(0, 0, glTarget.width, glTarget.height)
        if (clearColor != null) {
            GL30.glClearBufferfv(GL11.GL_COLOR, 0, clearColor)
        }
    }

    override fun endRenderTarget() {
        check(targetStack.size > 1) { "No render target to end" }
        targetStack.removeAt(targetStack.size - 1)
        val active = targetStack.last()
        if (active != null) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, active.fbo)
            GL11.glViewport(0, 0, active.width, active.height)
        } else {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, frameSnapshot.drawFramebuffer)
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, frameSnapshot.readFramebuffer)
            GL11.glViewport(frameSnapshot.viewportX, frameSnapshot.viewportY, frameSnapshot.viewportWidth, frameSnapshot.viewportHeight)
        }
    }

    override fun close() {
        textureTransfer.close()
        GlTexture.closeTransferBuffer()
    }

    override fun hasContext(): Boolean = GLFW.glfwGetCurrentContext() != 0L

    fun invalidateStateCache() {
        invalidateBindingCache()
        activeTextureUnit = -1
    }

    fun <T> externalGl(action: () -> T): T {
        pixelUnpackState.invalidate()
        invalidateStateCache()
        return try {
            action()
        } finally {
            if (frameActive) applyOwnedGuiState()
            invalidateStateCache()
        }
    }

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
        if (!Luma.platform.getViewport(viewport)) {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport)
        }
        snapshot.viewportX = viewport[0]
        snapshot.viewportY = viewport[1]
        snapshot.viewportWidth = viewport[2]
        snapshot.viewportHeight = viewport[3]

        snapshot.drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        snapshot.readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)

        snapshot.blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND)
        snapshot.depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        snapshot.cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE)
        snapshot.scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
        GL11.glGetIntegerv(GL11.GL_COLOR_WRITEMASK, colorMaskBuffer)
        snapshot.colorMaskRed = colorMaskBuffer[0] != 0
        snapshot.colorMaskGreen = colorMaskBuffer[1] != 0
        snapshot.colorMaskBlue = colorMaskBuffer[2] != 0
        snapshot.colorMaskAlpha = colorMaskBuffer[3] != 0

        snapshot.blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
        snapshot.blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        snapshot.blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
        snapshot.blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        snapshot.blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB)
        snapshot.blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA)

        snapshot.program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        snapshot.vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
        snapshot.arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)

        snapshot.activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
        snapshot.clearTextureUnits()
    }

    private fun restoreState(snapshot: GlStateSnapshot) {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, snapshot.drawFramebuffer)
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, snapshot.readFramebuffer)
        GL11.glViewport(snapshot.viewportX, snapshot.viewportY, snapshot.viewportWidth, snapshot.viewportHeight)

        if (snapshot.blendEnabled) GL11.glEnable(GL11.GL_BLEND) else GL11.glDisable(GL11.GL_BLEND)
        if (snapshot.depthEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST) else GL11.glDisable(GL11.GL_DEPTH_TEST)
        if (snapshot.cullEnabled) GL11.glEnable(GL11.GL_CULL_FACE) else GL11.glDisable(GL11.GL_CULL_FACE)
        if (snapshot.scissorEnabled) GL11.glEnable(GL11.GL_SCISSOR_TEST) else GL11.glDisable(GL11.GL_SCISSOR_TEST)
        GL11.glColorMask(
            snapshot.colorMaskRed,
            snapshot.colorMaskGreen,
            snapshot.colorMaskBlue,
            snapshot.colorMaskAlpha
        )

        GL14.glBlendFuncSeparate(snapshot.blendSrcRgb, snapshot.blendDstRgb, snapshot.blendSrcAlpha, snapshot.blendDstAlpha)
        GL20.glBlendEquationSeparate(snapshot.blendEquationRgb, snapshot.blendEquationAlpha)

        GL20.glUseProgram(snapshot.program)
        GL30.glBindVertexArray(snapshot.vertexArray)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, snapshot.arrayBuffer)

        for (index in 0 until snapshot.textureUnitCount) {
            val unit = snapshot.textureUnit(index)
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit)
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, snapshot.boundTexture(index))
            GL33.glBindSampler(unit, snapshot.samplerBinding(index))
        }
        GL13.glActiveTexture(snapshot.activeTexture)
    }

    private fun prepareTextureUnit(unit: Int) {
        activateTextureUnit(unit)
        ensureSamplerCapacity(unit + 1)
        if (config.statePolicy == GlStatePolicy.OWNED) {
            ensureSamplerUnbound(unit)
            return
        }
        if (frameSnapshot.hasTextureUnit(unit)) return
        val sampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING)
        frameSnapshot.addTextureUnit(
            unit,
            GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),
            sampler
        )
        if (sampler != 0) {
            GL33.glBindSampler(unit, 0)
        }
        samplerBindings[unit] = 0
    }

    private fun activateTextureUnit(unit: Int) {
        if (activeTextureUnit == unit) return
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit)
        activeTextureUnit = unit
    }

    private fun bindTextureForUpload(texture: GlTexture) {
        texture.requireOpen()
        val unit = activeTextureUnit.coerceAtLeast(0)
        prepareTextureUnit(unit)
        if (!textureBindings.isBound(unit, texture)) {
            texture.bindCurrentUnit()
            textureBindings.bind(unit, texture)
        }
    }

    private fun ensureSamplerUnbound(unit: Int) {
        ensureSamplerCapacity(unit + 1)
        if (samplerBindings[unit] == 0) return
        GL33.glBindSampler(unit, 0)
        samplerBindings[unit] = 0
    }

    private fun ensureSamplerCapacity(required: Int) {
        if (required <= samplerBindings.size) return
        val previousSize = samplerBindings.size
        val capacity = maxOf(required, maxOf(1, previousSize shl 1))
        samplerBindings = samplerBindings.copyOf(capacity)
        java.util.Arrays.fill(samplerBindings, previousSize, capacity, UNKNOWN_SAMPLER_BINDING)
    }

    private fun applyGuiState(snapshot: GlStateSnapshot) {
        if (snapshot.depthEnabled) GL11.glDisable(GL11.GL_DEPTH_TEST)
        if (snapshot.cullEnabled) GL11.glDisable(GL11.GL_CULL_FACE)
        if (snapshot.scissorEnabled) GL11.glDisable(GL11.GL_SCISSOR_TEST)
        if (!snapshot.blendEnabled) GL11.glEnable(GL11.GL_BLEND)
        if (
            !snapshot.colorMaskRed ||
            !snapshot.colorMaskGreen ||
            !snapshot.colorMaskBlue ||
            !snapshot.colorMaskAlpha
        ) {
            GL11.glColorMask(true, true, true, true)
        }

        if (snapshot.blendEquationRgb != GL14.GL_FUNC_ADD || snapshot.blendEquationAlpha != GL14.GL_FUNC_ADD) {
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD)
        }
        if (
            snapshot.blendSrcRgb != GL11.GL_SRC_ALPHA ||
            snapshot.blendDstRgb != GL11.GL_ONE_MINUS_SRC_ALPHA ||
            snapshot.blendSrcAlpha != GL11.GL_ONE ||
            snapshot.blendDstAlpha != GL11.GL_ONE_MINUS_SRC_ALPHA
        ) {
            GL14.glBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ONE_MINUS_SRC_ALPHA
            )
        }
    }

    private fun prepareOwnedFrame() {
        frameSnapshot.drawFramebuffer = config.ownedDrawFramebuffer
        frameSnapshot.readFramebuffer = config.ownedReadFramebuffer
        val viewport = viewportBuffer
        if (!Luma.platform.getViewport(viewport)) GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport)
        frameSnapshot.viewportX = viewport[0]
        frameSnapshot.viewportY = viewport[1]
        frameSnapshot.viewportWidth = viewport[2]
        frameSnapshot.viewportHeight = viewport[3]
        frameSnapshot.clearTextureUnits()
        applyOwnedGuiState()
        GL13.glActiveTexture(GL13.GL_TEXTURE0)
        activeTextureUnit = 0
    }

    private fun applyOwnedGuiState() {
        GL11.glDisable(GL11.GL_DEPTH_TEST)
        GL11.glDisable(GL11.GL_CULL_FACE)
        GL11.glDisable(GL11.GL_SCISSOR_TEST)
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glColorMask(true, true, true, true)
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD)
        GL14.glBlendFuncSeparate(
            GL11.GL_SRC_ALPHA,
            GL11.GL_ONE_MINUS_SRC_ALPHA,
            GL11.GL_ONE,
            GL11.GL_ONE_MINUS_SRC_ALPHA
        )
    }

    private companion object {
        const val UNKNOWN_SAMPLER_BINDING = Int.MIN_VALUE
    }
}
