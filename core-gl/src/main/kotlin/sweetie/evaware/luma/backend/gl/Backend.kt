package sweetie.evaware.luma.backend.gl

import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import java.util.Arrays
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.*
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.BlendFactor
import sweetie.evaware.luma.api.BlendFunction
import sweetie.evaware.luma.api.BlendOp
import sweetie.evaware.luma.api.DepthCompare
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.api.RenderBackend
import sweetie.evaware.luma.api.RenderTargetFilter
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.texture.RgbaTransferBuffer
import sweetie.evaware.luma.uniform.*
import sweetie.evaware.luma.vertex.VertexLayout

class Backend(
    private val config: GlBackendConfig = GlBackendConfig()
) : RenderBackend {
    private val frameSnapshot = GlStateSnapshot()
    private val viewportBuffer = IntArray(4)
    private val colorMaskBuffer = IntArray(4)
    private val scalarStateBuffer = BufferUtils.createIntBuffer(1)
    private val targetBindings = ArrayList<IntArray>()
    private val pixelUnpackState = GlPixelUnpackState(config.statePolicy == GlStatePolicy.PRESERVE)
    private val textureTransfer = RgbaTransferBuffer()

    private var boundProgramId = -1
    private var boundVertexArrayId = -1
    private var boundArrayBufferId = -1
    private val textureBindings = TextureBindingCache(config.initialTextureUnits)
    private val renderState = GlRenderStateCache()
    private val framebufferState = GlFramebufferStateCache()
    private var samplerBindings = IntArray(config.initialTextureUnits) { UNKNOWN_SAMPLER_BINDING }
    private var activeTextureUnit = -1
    private var frameActive = false
    private var targetDepth = 0
    private var ownedFixedStateKnown = false

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
            renderState.resetGui()
        } else {
            prepareOwnedFrame()
        }
        framebufferState.seed(
            frameSnapshot.drawFramebuffer,
            frameSnapshot.readFramebuffer,
            frameSnapshot.viewportX,
            frameSnapshot.viewportY,
            frameSnapshot.viewportWidth,
            frameSnapshot.viewportHeight
        )
        invalidateBindingCache()
        targetDepth = 0
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
            targetDepth = 0
            if (config.statePolicy == GlStatePolicy.PRESERVE) {
                invalidateStateCache()
            } else {
                invalidateBindingCache()
                framebufferState.invalidate()
            }
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
            bindSampler(unit, glTexture.samplerId)
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
        if (targetDepth == targetBindings.size) targetBindings.add(IntArray(6))
        val previous = targetBindings[targetDepth]
        if (config.statePolicy == GlStatePolicy.PRESERVE) {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewportBuffer)
            framebufferState.seed(
                getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                viewportBuffer[0],
                viewportBuffer[1],
                viewportBuffer[2],
                viewportBuffer[3]
            )
        }
        check(framebufferState.saveTo(previous))
        targetDepth++
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, glTarget.fbo)
        GL11.glViewport(0, 0, glTarget.width, glTarget.height)
        framebufferState.seed(glTarget.fbo, glTarget.fbo, 0, 0, glTarget.width, glTarget.height)
        if (clearColor != null) {
            GL30.glClearBufferfv(GL11.GL_COLOR, 0, clearColor)
        }
    }

    override fun endRenderTarget() {
        check(targetDepth > 0) { "No render target to end" }
        val previous = targetBindings[--targetDepth]
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previous[0])
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previous[1])
        GL11.glViewport(previous[2], previous[3], previous[4], previous[5])
        framebufferState.restoreFrom(previous)
    }

    override fun blend(enabled: Boolean) {
        if (!renderState.blend(enabled)) return
        if (enabled) GL11.glEnable(GL11.GL_BLEND) else GL11.glDisable(GL11.GL_BLEND)
    }

    override fun blendFunction(function: BlendFunction) {
        if (!renderState.blendFunction(function)) return
        GL14.glBlendFuncSeparate(
            blendFactor(function.sourceColor),
            blendFactor(function.destinationColor),
            blendFactor(function.sourceAlpha),
            blendFactor(function.destinationAlpha)
        )
        GL20.glBlendEquationSeparate(blendOp(function.colorOp), blendOp(function.alphaOp))
    }

    override fun depthTest(enabled: Boolean) {
        if (!renderState.depthTest(enabled)) return
        if (enabled) GL11.glEnable(GL11.GL_DEPTH_TEST) else GL11.glDisable(GL11.GL_DEPTH_TEST)
    }

    override fun depthWrite(enabled: Boolean) {
        if (!renderState.depthWrite(enabled)) return
        GL11.glDepthMask(enabled)
    }

    override fun depthCompare(compare: DepthCompare) {
        if (!renderState.depthCompare(compare)) return
        GL11.glDepthFunc(depthFunction(compare))
    }

    override fun cull(enabled: Boolean) {
        if (!renderState.cull(enabled)) return
        if (enabled) GL11.glEnable(GL11.GL_CULL_FACE) else GL11.glDisable(GL11.GL_CULL_FACE)
    }

    override fun close() {
        textureTransfer.close()
        GlTexture.closeTransferBuffer()
    }

    override fun hasContext(): Boolean = GLFW.glfwGetCurrentContext() != 0L

    fun invalidateStateCache() {
        invalidateBindingCache()
        activeTextureUnit = -1
        renderState.invalidate()
        framebufferState.invalidate()
        ownedFixedStateKnown = false
    }

    fun <T> externalGl(action: () -> T): T {
        pixelUnpackState.invalidate()
        invalidateStateCache()
        return try {
            action()
        } finally {
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
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport)
        snapshot.viewportX = viewport[0]
        snapshot.viewportY = viewport[1]
        snapshot.viewportWidth = viewport[2]
        snapshot.viewportHeight = viewport[3]

        snapshot.drawFramebuffer = getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        snapshot.readFramebuffer = getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)

        snapshot.blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND)
        snapshot.depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        snapshot.depthWrite = getInteger(GL11.GL_DEPTH_WRITEMASK) != 0
        snapshot.depthFunc = getInteger(GL11.GL_DEPTH_FUNC)
        snapshot.cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE)
        snapshot.scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
        GL11.glGetIntegerv(GL11.GL_COLOR_WRITEMASK, colorMaskBuffer)
        snapshot.colorMaskRed = colorMaskBuffer[0] != 0
        snapshot.colorMaskGreen = colorMaskBuffer[1] != 0
        snapshot.colorMaskBlue = colorMaskBuffer[2] != 0
        snapshot.colorMaskAlpha = colorMaskBuffer[3] != 0

        snapshot.blendSrcRgb = getInteger(GL14.GL_BLEND_SRC_RGB)
        snapshot.blendDstRgb = getInteger(GL14.GL_BLEND_DST_RGB)
        snapshot.blendSrcAlpha = getInteger(GL14.GL_BLEND_SRC_ALPHA)
        snapshot.blendDstAlpha = getInteger(GL14.GL_BLEND_DST_ALPHA)
        snapshot.blendEquationRgb = getInteger(GL20.GL_BLEND_EQUATION_RGB)
        snapshot.blendEquationAlpha = getInteger(GL20.GL_BLEND_EQUATION_ALPHA)

        snapshot.program = getInteger(GL20.GL_CURRENT_PROGRAM)
        snapshot.vertexArray = getInteger(GL30.GL_VERTEX_ARRAY_BINDING)
        snapshot.arrayBuffer = getInteger(GL15.GL_ARRAY_BUFFER_BINDING)

        snapshot.activeTexture = getInteger(GL13.GL_ACTIVE_TEXTURE)
        snapshot.clearTextureUnits()
    }

    private fun restoreState(snapshot: GlStateSnapshot) {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, snapshot.drawFramebuffer)
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, snapshot.readFramebuffer)
        GL11.glViewport(snapshot.viewportX, snapshot.viewportY, snapshot.viewportWidth, snapshot.viewportHeight)

        if (snapshot.blendEnabled) GL11.glEnable(GL11.GL_BLEND) else GL11.glDisable(GL11.GL_BLEND)
        if (snapshot.depthEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST) else GL11.glDisable(GL11.GL_DEPTH_TEST)
        GL11.glDepthMask(snapshot.depthWrite)
        GL11.glDepthFunc(snapshot.depthFunc)
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
        val sampler = getInteger(GL33.GL_SAMPLER_BINDING)
        frameSnapshot.addTextureUnit(
            unit,
            getInteger(GL11.GL_TEXTURE_BINDING_2D),
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
        bindSampler(unit, 0)
    }

    private fun bindSampler(unit: Int, sampler: Int) {
        ensureSamplerCapacity(unit + 1)
        if (samplerBindings[unit] == sampler) return
        GL33.glBindSampler(unit, sampler)
        samplerBindings[unit] = sampler
    }

    private fun ensureSamplerCapacity(required: Int) {
        if (required <= samplerBindings.size) return
        val previousSize = samplerBindings.size
        val capacity = maxOf(required, maxOf(1, previousSize shl 1))
        samplerBindings = samplerBindings.copyOf(capacity)
        Arrays.fill(samplerBindings, previousSize, capacity, UNKNOWN_SAMPLER_BINDING)
    }

    private fun applyGuiState(snapshot: GlStateSnapshot) {
        if (snapshot.depthEnabled) GL11.glDisable(GL11.GL_DEPTH_TEST)
        if (snapshot.depthWrite) GL11.glDepthMask(false)
        if (snapshot.depthFunc != GL11.GL_ALWAYS) GL11.glDepthFunc(GL11.GL_ALWAYS)
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

    private fun getInteger(parameter: Int): Int {
        GL11.glGetIntegerv(parameter, scalarStateBuffer)
        return scalarStateBuffer.get(0)
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
        activateTextureUnit(0)
    }

    private fun applyOwnedGuiState() {
        depthTest(false)
        depthWrite(false)
        depthCompare(DepthCompare.ALWAYS)
        cull(false)
        blend(true)
        blendFunction(BlendFunction.TRANSLUCENT)
        if (!ownedFixedStateKnown) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST)
            GL11.glColorMask(true, true, true, true)
            ownedFixedStateKnown = true
        }
    }

    private companion object {
        const val UNKNOWN_SAMPLER_BINDING = Int.MIN_VALUE

        fun depthFunction(compare: DepthCompare): Int = when (compare) {
            DepthCompare.ALWAYS -> GL11.GL_ALWAYS
            DepthCompare.LESS -> GL11.GL_LESS
            DepthCompare.LESS_OR_EQUAL -> GL11.GL_LEQUAL
            DepthCompare.EQUAL -> GL11.GL_EQUAL
            DepthCompare.NOT_EQUAL -> GL11.GL_NOTEQUAL
            DepthCompare.GREATER_OR_EQUAL -> GL11.GL_GEQUAL
            DepthCompare.GREATER -> GL11.GL_GREATER
            DepthCompare.NEVER -> GL11.GL_NEVER
        }

        fun blendFactor(factor: BlendFactor): Int = when (factor) {
            BlendFactor.ZERO -> GL11.GL_ZERO
            BlendFactor.ONE -> GL11.GL_ONE
            BlendFactor.SRC_COLOR -> GL11.GL_SRC_COLOR
            BlendFactor.ONE_MINUS_SRC_COLOR -> GL11.GL_ONE_MINUS_SRC_COLOR
            BlendFactor.DST_COLOR -> GL11.GL_DST_COLOR
            BlendFactor.ONE_MINUS_DST_COLOR -> GL11.GL_ONE_MINUS_DST_COLOR
            BlendFactor.SRC_ALPHA -> GL11.GL_SRC_ALPHA
            BlendFactor.ONE_MINUS_SRC_ALPHA -> GL11.GL_ONE_MINUS_SRC_ALPHA
            BlendFactor.DST_ALPHA -> GL11.GL_DST_ALPHA
            BlendFactor.ONE_MINUS_DST_ALPHA -> GL11.GL_ONE_MINUS_DST_ALPHA
            BlendFactor.CONSTANT_COLOR -> GL14.GL_CONSTANT_COLOR
            BlendFactor.ONE_MINUS_CONSTANT_COLOR -> GL14.GL_ONE_MINUS_CONSTANT_COLOR
            BlendFactor.CONSTANT_ALPHA -> GL14.GL_CONSTANT_ALPHA
            BlendFactor.ONE_MINUS_CONSTANT_ALPHA -> GL14.GL_ONE_MINUS_CONSTANT_ALPHA
            BlendFactor.SRC_ALPHA_SATURATE -> GL11.GL_SRC_ALPHA_SATURATE
        }

        fun blendOp(op: BlendOp): Int = when (op) {
            BlendOp.ADD -> GL14.GL_FUNC_ADD
            BlendOp.SUBTRACT -> GL14.GL_FUNC_SUBTRACT
            BlendOp.REVERSE_SUBTRACT -> GL14.GL_FUNC_REVERSE_SUBTRACT
            BlendOp.MIN -> GL14.GL_MIN
            BlendOp.MAX -> GL14.GL_MAX
        }
    }
}
