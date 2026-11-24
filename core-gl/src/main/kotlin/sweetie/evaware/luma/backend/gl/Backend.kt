package sweetie.evaware.luma.backend.gl

import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.util.Arrays
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.*
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.BlendFactor
import sweetie.evaware.luma.api.BlendFunction
import sweetie.evaware.luma.api.BlendOp
import sweetie.evaware.luma.api.BufferUsage
import sweetie.evaware.luma.api.DepthCompare
import sweetie.evaware.luma.api.IndexBufferHandle
import sweetie.evaware.luma.api.IndexType
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.api.RenderBackend
import sweetie.evaware.luma.api.RenderTargetFilter
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.TextureFormat
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.api.VertexBufferHandle
import sweetie.evaware.luma.texture.RgbaTransferBuffer
import sweetie.evaware.luma.uniform.*
import sweetie.evaware.luma.vertex.VertexLayout
import sweetie.evaware.luma.vertex.VertexInputLayout

class Backend(
    private val config: GlBackendConfig = GlBackendConfig()
) : RenderBackend {
    private val preserveState = config.statePolicy == GlStatePolicy.PRESERVE
    private val frameSnapshot = GlStateSnapshot()
    private val viewportBuffer = IntArray(4)
    private val scissorBuffer = IntArray(4)
    private val colorMaskBuffer = IntArray(4)
    private val scalarStateBuffer = BufferUtils.createIntBuffer(1)
    private val clearColorBuffer = BufferUtils.createFloatBuffer(4)
    private val clearDepthBuffer = BufferUtils.createFloatBuffer(1)
    private val targetBindings = ArrayList<IntArray>()
    private val pixelUnpackState = GlPixelUnpackState(preserveState)
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

    // Save-on-write tracking for PRESERVE mode
    private var dirtyRestoreMask = 0
    private var preserveGuiDefaultsApplied = false

    // Logical vs applied framebuffer & viewport state (lazy binding)
    private var logicalDrawFramebuffer = HOST_FBO_SENTINEL
    private var logicalReadFramebuffer = HOST_FBO_SENTINEL
    private var viewportX = 0
    private var viewportY = 0
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var hostViewportKnown = false
    private var hostFramebufferCaptured = false
    private var framebufferDirty = false
    private var appliedFramebufferKnown = false
    private var appliedDrawFramebuffer = -1
    private var appliedReadFramebuffer = -1
    private var appliedViewportKnown = false
    private var appliedViewportX = 0
    private var appliedViewportY = 0
    private var appliedViewportWidth = 0
    private var appliedViewportHeight = 0

    // Scissor state
    private var scissorEnabled = false
    private var scissorX = 0
    private var scissorY = 0
    private var scissorWidth = 0
    private var scissorHeight = 0
    private var scissorBoxDirty = false

    // Direct draw bindings
    private val directVertexBuffers = arrayOfNulls<GlVertexBufferHandle>(16)
    private val directVertexOffsets = LongArray(16)
    private var directIndexBuffer: GlIndexBufferHandle? = null

    // Consecutive immediate draw merging batch
    private var pendingProgram: Program? = null
    private var pendingPrimitive: PrimitiveType = PrimitiveType.TRIANGLES
    private var pendingBaseVertex = 0
    private var pendingVertexCount = 0
    private var pendingInstanced = false

    // One-time debug group capability state
    private var debugGroupMode: Byte = DEBUG_GROUP_UNCHECKED
    private var debugGroupDepth = 0

    private fun useProgram(programId: Int) {
        if (boundProgramId == programId) return
        if (preserveState && frameActive && (dirtyRestoreMask and DIRTY_PROGRAM) == 0) {
            val hostProgram = getInteger(GL20.GL_CURRENT_PROGRAM)
            frameSnapshot.program = hostProgram
            dirtyRestoreMask = dirtyRestoreMask or DIRTY_PROGRAM
            if (hostProgram == programId) {
                boundProgramId = programId
                return
            }
        }
        GL20.glUseProgram(programId)
        boundProgramId = programId
    }

    private fun bindVertexArray(vertexArrayId: Int) {
        if (boundVertexArrayId == vertexArrayId) return
        if (preserveState && frameActive && (dirtyRestoreMask and DIRTY_VAO) == 0) {
            val hostVao = getInteger(GL30.GL_VERTEX_ARRAY_BINDING)
            frameSnapshot.vertexArray = hostVao
            dirtyRestoreMask = dirtyRestoreMask or DIRTY_VAO
            if (hostVao == vertexArrayId) {
                boundVertexArrayId = vertexArrayId
                return
            }
        }
        GL30.glBindVertexArray(vertexArrayId)
        boundVertexArrayId = vertexArrayId
    }

    private fun saveArrayBufferIfNeeded() {
        if (preserveState && frameActive && (dirtyRestoreMask and DIRTY_ARRAY_BUFFER) == 0) {
            frameSnapshot.arrayBuffer = getInteger(GL15.GL_ARRAY_BUFFER_BINDING)
            dirtyRestoreMask = dirtyRestoreMask or DIRTY_ARRAY_BUFFER
        }
    }

    private fun bindArrayBuffer(bufferId: Int) {
        if (boundArrayBufferId == bufferId) return
        if (preserveState && frameActive && (dirtyRestoreMask and DIRTY_ARRAY_BUFFER) == 0) {
            val hostBuffer = getInteger(GL15.GL_ARRAY_BUFFER_BINDING)
            frameSnapshot.arrayBuffer = hostBuffer
            dirtyRestoreMask = dirtyRestoreMask or DIRTY_ARRAY_BUFFER
            if (hostBuffer == bufferId) {
                boundArrayBufferId = bufferId
                return
            }
        }
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
        dirtyRestoreMask = 0
        preserveGuiDefaultsApplied = false
        clearPendingDraw()
        targetDepth = 0
        logicalDrawFramebuffer = HOST_FBO_SENTINEL
        logicalReadFramebuffer = HOST_FBO_SENTINEL
        hostViewportKnown = false
        hostFramebufferCaptured = false
        framebufferDirty = false
        appliedFramebufferKnown = false
        appliedViewportKnown = false
        scissorEnabled = false
        scissorBoxDirty = false
        frameSnapshot.clearTextureUnits()
        invalidateBindingCache()
        if (preserveState) {
            activeTextureUnit = -1
            renderState.invalidate()
        } else {
            prepareOwnedFrame()
        }
        frameActive = true
    }

    override fun endFrame() {
        try {
            flushPendingDraw()
            try {
                pixelUnpackState.restore()
            } finally {
                if (preserveState) {
                    restoreState(frameSnapshot)
                } else {
                    if (scissorEnabled) {
                        GL11.glDisable(GL11.GL_SCISSOR_TEST)
                        scissorEnabled = false
                    }
                    if (hostFramebufferCaptured) {
                        logicalDrawFramebuffer = HOST_FBO_SENTINEL
                        logicalReadFramebuffer = HOST_FBO_SENTINEL
                        framebufferDirty = true
                        flushFramebuffer()
                    }
                }
            }
        } finally {
            clearPendingDraw()
            frameActive = false
            targetDepth = 0
            dirtyRestoreMask = 0
            preserveGuiDefaultsApplied = false
            hostFramebufferCaptured = false
            hostViewportKnown = false
            framebufferDirty = false
            scissorBoxDirty = false
            appliedFramebufferKnown = false
            appliedViewportKnown = false
            if (preserveState) {
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
    ): ProgramHandle = createProgram(vertexSource, fragmentSource, VertexInputLayout().binding(layout))

    override fun createProgram(
        vertexSource: String,
        fragmentSource: String,
        layouts: VertexInputLayout
    ): ProgramHandle {
        require(layouts.size() > 0) { "Program requires at least one vertex binding" }
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

            for (binding in 0 until layouts.size()) {
                val layout = layouts.layout(binding)
                for (index in 0 until layout.size()) {
                    val layoutPos = layout.layoutPos(index)
                    GL20.glBindAttribLocation(programId, layoutPos, "a$layoutPos")
                }
            }

            GL20.glLinkProgram(programId)

            if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                val log = GL20.glGetProgramInfoLog(programId)
                error("Failed to link program: $log")
            }

            loaded = true
            return Program(programId, layouts)
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
        if (pendingProgram != null && pendingProgram !== glProgram) {
            flushPendingDraw()
        }
        useProgram(glProgram.programId)
        bindVertexArray(glProgram.vertexBuffer.vao)
    }

    override fun createTexture(image: BufferedImage, mipmap: Boolean): TextureHandle =
        createTexture(image, mipmap, TextureFormat.RGBA8)

    override fun createTexture(image: BufferedImage, mipmap: Boolean, format: TextureFormat): TextureHandle {
        require(format.supportsBufferedImageUpload) {
            "BufferedImage upload is not supported for format $format; upload raw ByteBuffer data instead"
        }
        if (!frameActive) return GlTexture.create(image, mipmap, format)
        flushPendingDraw()
        pixelUnpackState.prepare()
        val texture = GlTexture(GL11.glGenTextures(), image.width, image.height, mipmap, format = format)
        try {
            bindTextureForUpload(texture)
            texture.initializeBound(image, textureTransfer)
            return texture
        } catch (failure: Throwable) {
            texture.close()
            throw failure
        }
    }

    override fun createTexture(
        width: Int,
        height: Int,
        data: ByteBuffer?,
        format: TextureFormat,
        mipmap: Boolean
    ): TextureHandle {
        require(width > 0 && height > 0) { "Texture dimensions must be positive: ${width}x${height}" }
        if (!frameActive) return GlTexture.createRaw(width, height, data, format, mipmap)
        flushPendingDraw()
        pixelUnpackState.prepare()
        val texture = GlTexture(GL11.glGenTextures(), width, height, mipmap, format = format)
        try {
            bindTextureForUpload(texture)
            texture.initializeBoundRaw(data)
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
        flushPendingDraw()
        pixelUnpackState.prepare()
        bindTextureForUpload(glTexture)
        glTexture.updateBound(x, y, image, textureTransfer)
    }

    override fun updateTexture(
        texture: TextureHandle,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        data: ByteBuffer
    ) {
        val glTexture = texture as GlTexture
        if (!frameActive) {
            glTexture.update(x, y, width, height, data)
            return
        }
        flushPendingDraw()
        pixelUnpackState.prepare()
        bindTextureForUpload(glTexture)
        glTexture.updateBoundRaw(x, y, width, height, data)
    }

    override fun bindTexture(texture: TextureHandle, unit: Int) {
        require(unit >= 0) { "Texture unit must be non-negative: $unit" }
        val glTexture = texture as GlTexture
        glTexture.requireOpen()
        if (textureBindings.isBound(unit, glTexture)) return

        if (pendingProgram != null) flushPendingDraw()

        if (frameActive) {
            prepareTextureUnit(unit)
            glTexture.bindCurrentUnit()
            bindSampler(unit, glTexture.samplerId)
        } else {
            glTexture.bind(unit)
        }
        textureBindings.bind(unit, glTexture)
    }

    override fun createVertexBuffer(sizeBytes: Long, usage: BufferUsage): VertexBufferHandle =
        GlVertexBufferHandle(sizeBytes, usage)

    override fun createIndexBuffer(sizeBytes: Long, indexType: IndexType, usage: BufferUsage): IndexBufferHandle =
        GlIndexBufferHandle(sizeBytes, indexType, usage)

    override fun updateVertexBuffer(buffer: VertexBufferHandle, offsetBytes: Long, data: ByteBuffer) {
        if (pendingProgram != null) flushPendingDraw()
        (buffer as GlVertexBufferHandle).update(offsetBytes, data)
        boundArrayBufferId = -1
    }

    override fun updateIndexBuffer(buffer: IndexBufferHandle, offsetBytes: Long, data: ByteBuffer) {
        if (pendingProgram != null) flushPendingDraw()
        (buffer as GlIndexBufferHandle).update(offsetBytes, data)
        boundArrayBufferId = -1
    }

    override fun bindVertexBuffer(binding: Int, buffer: VertexBufferHandle, offsetBytes: Long) {
        require(binding in directVertexBuffers.indices) { "Vertex binding $binding is out of range" }
        require(offsetBytes >= 0L && offsetBytes < buffer.sizeBytes) { "Vertex buffer offset is out of bounds" }
        directVertexBuffers[binding] = buffer as GlVertexBufferHandle
        directVertexOffsets[binding] = offsetBytes
    }

    override fun bindIndexBuffer(buffer: IndexBufferHandle) {
        directIndexBuffer = buffer as GlIndexBufferHandle
    }

    override fun draw(
        program: ProgramHandle,
        uniforms: ShaderUniforms,
        primitiveType: PrimitiveType,
        firstVertex: Int,
        vertexCount: Int,
        instanceCount: Int,
        firstInstance: Int
    ) {
        require(firstVertex >= 0 && vertexCount >= 0 && instanceCount >= 0 && firstInstance >= 0)
        require(primitiveType != PrimitiveType.QUADS) { "Direct QUADS require an index buffer" }
        if (pendingProgram != null) flushPendingDraw()
        prepareDrawTargetAndState()
        prepareDirectDraw(program, uniforms)
        val mode = primitiveType.glPrimitive
        if (firstInstance == 0) {
            if (instanceCount == 1) {
                GL11.glDrawArrays(mode, firstVertex, vertexCount)
            } else {
                GL31.glDrawArraysInstanced(mode, firstVertex, vertexCount, instanceCount)
            }
        } else {
            GL42.glDrawArraysInstancedBaseInstance(mode, firstVertex, vertexCount, instanceCount, firstInstance)
        }
    }

    override fun drawIndexed(
        program: ProgramHandle,
        uniforms: ShaderUniforms,
        primitiveType: PrimitiveType,
        firstIndex: Int,
        indexCount: Int,
        vertexOffset: Int,
        instanceCount: Int,
        firstInstance: Int
    ) {
        require(firstIndex >= 0 && indexCount >= 0 && instanceCount >= 0 && firstInstance >= 0)
        require(primitiveType != PrimitiveType.QUADS) { "Indexed QUADS must use TRIANGLES topology" }
        if (pendingProgram != null) flushPendingDraw()
        prepareDrawTargetAndState()
        val glProgram = prepareDirectDraw(program, uniforms)
        val indices = requireNotNull(directIndexBuffer) { "Index buffer is not bound" }
        indices.requireOpen()
        if (glProgram.configuredElementBuffer !== indices) {
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indices.id)
            glProgram.configuredElementBuffer = indices
        }
        val mode = primitiveType.glPrimitive
        val glIndexType = indices.indexType.glType
        val byteOffset = firstIndex.toLong() * indices.indexType.byteSize
        if (firstInstance == 0) {
            if (instanceCount == 1) {
                if (vertexOffset == 0) {
                    GL11.glDrawElements(mode, indexCount, glIndexType, byteOffset)
                } else {
                    GL32.glDrawElementsBaseVertex(mode, indexCount, glIndexType, byteOffset, vertexOffset)
                }
            } else if (vertexOffset == 0) {
                GL31.glDrawElementsInstanced(mode, indexCount, glIndexType, byteOffset, instanceCount)
            } else {
                GL32.glDrawElementsInstancedBaseVertex(mode, indexCount, glIndexType, byteOffset, instanceCount, vertexOffset)
            }
        } else {
            GL42.glDrawElementsInstancedBaseVertexBaseInstance(
                mode,
                indexCount,
                glIndexType,
                byteOffset,
                instanceCount,
                vertexOffset,
                firstInstance
            )
        }
    }

    private fun prepareDirectDraw(program: ProgramHandle, uniforms: ShaderUniforms): Program {
        val glProgram = program as Program
        useProgram(glProgram.programId)
        bindVertexArray(glProgram.directVertexArray)
        if (glProgram.configureDirectBindings(directVertexBuffers, directVertexOffsets) { saveArrayBufferIfNeeded() }) {
            boundArrayBufferId = -1
        }
        glProgram.uploadUniforms(uniforms)
        return glProgram
    }

    override fun draw(
        program: ProgramHandle,
        vertices: FloatBuffer,
        vertexCount: Int,
        uniforms: ShaderUniforms,
        primitiveType: PrimitiveType
    ) {
        if (vertexCount <= 0) return
        val glProgram = program as Program
        val vb = glProgram.vertexBuffer
        val floatCount = vertices.limit()

        if (
            frameActive &&
            pendingProgram === glProgram &&
            pendingPrimitive == primitiveType &&
            !glProgram.hasPendingUniformUpload(uniforms) &&
            floatCount == vertexCount * vb.layout.strideFloats &&
            (primitiveType != PrimitiveType.QUADS || ((vertexCount and 3) == 0 && (pendingVertexCount and 3) == 0)) &&
            vb.canAppendContiguously(floatCount)
        ) {
            val baseVertex = uploadVertexBuffer(vb, vertices)
            if (baseVertex == pendingBaseVertex + pendingVertexCount) {
                pendingVertexCount += vertexCount
                return
            }
            // Defensive fallback if offset was not contiguous
            flushPendingDraw()
            pendingProgram = glProgram
            pendingPrimitive = primitiveType
            pendingBaseVertex = baseVertex
            pendingVertexCount = vertexCount
            pendingInstanced = vb.layout.instanced
            return
        }

        if (pendingProgram != null) {
            flushPendingDraw()
        }

        prepareDrawTargetAndState()
        useProgram(glProgram.programId)
        bindVertexArray(vb.vao)

        val baseVertex = uploadVertexBuffer(vb, vertices)
        glProgram.uploadUniforms(uniforms)

        if (frameActive) {
            pendingProgram = glProgram
            pendingPrimitive = primitiveType
            pendingBaseVertex = baseVertex
            pendingVertexCount = vertexCount
            pendingInstanced = vb.layout.instanced
        } else {
            executeImmediateDraw(glProgram, primitiveType, baseVertex, vertexCount, vb.layout.instanced)
        }
    }

    private fun uploadVertexBuffer(vb: GlVertexBuffer, vertices: FloatBuffer): Int {
        return vb.uploadStreaming(vertices) { boundId ->
            saveArrayBufferIfNeeded()
            boundArrayBufferId = boundId
        }
    }

    private fun flushPendingDraw() {
        val glProgram = pendingProgram ?: return
        val primitive = pendingPrimitive
        val baseVertex = pendingBaseVertex
        val vertexCount = pendingVertexCount
        val instanced = pendingInstanced
        pendingProgram = null
        pendingVertexCount = 0
        if (vertexCount <= 0) return
        useProgram(glProgram.programId)
        bindVertexArray(glProgram.vertexBuffer.vao)
        executeImmediateDraw(glProgram, primitive, baseVertex, vertexCount, instanced)
    }

    private fun clearPendingDraw() {
        pendingProgram = null
        pendingVertexCount = 0
    }

    private fun executeImmediateDraw(
        glProgram: Program,
        primitiveType: PrimitiveType,
        baseVertex: Int,
        vertexCount: Int,
        instanced: Boolean
    ) {
        val vb = glProgram.vertexBuffer
        when {
            instanced -> {
                vb.drawInstancedBaseInstance(vb.layout.baseVertexCount, vertexCount, baseVertex)
            }
            primitiveType == PrimitiveType.QUADS -> {
                val indexCount = vb.bindQuadIndices(vertexCount)
                if (baseVertex == 0) {
                    GL11.glDrawElements(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, 0L)
                } else {
                    GL32.glDrawElementsBaseVertex(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, 0L, baseVertex)
                }
            }
            primitiveType == PrimitiveType.LINES -> GL11.glDrawArrays(GL11.GL_LINES, baseVertex, vertexCount)
            else -> GL11.glDrawArrays(GL11.GL_TRIANGLES, baseVertex, vertexCount)
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
        if (pendingProgram != null) flushPendingDraw()
        val glTarget = target as GlRenderTarget
        if (targetDepth == targetBindings.size) targetBindings.add(IntArray(6))
        val previous = targetBindings[targetDepth]
        previous[0] = logicalDrawFramebuffer
        previous[1] = logicalReadFramebuffer
        previous[2] = viewportX
        previous[3] = viewportY
        previous[4] = viewportWidth
        previous[5] = viewportHeight
        targetDepth++

        logicalDrawFramebuffer = glTarget.fbo
        logicalReadFramebuffer = glTarget.fbo
        viewportX = 0
        viewportY = 0
        viewportWidth = glTarget.width
        viewportHeight = glTarget.height
        framebufferDirty = true
        if (scissorEnabled) scissorBoxDirty = true

        if (clearColor != null) {
            ensurePreserveClearState()
            flushFramebuffer()
            if (scissorEnabled) GL11.glDisable(GL11.GL_SCISSOR_TEST)
            try {
                GL30.glClearBufferfv(GL11.GL_COLOR, 0, clearColor)
            } finally {
                if (scissorEnabled) GL11.glEnable(GL11.GL_SCISSOR_TEST)
            }
        }
    }

    override fun endRenderTarget() {
        check(targetDepth > 0) { "No render target to end" }
        if (pendingProgram != null) flushPendingDraw()
        val previous = targetBindings[--targetDepth]
        logicalDrawFramebuffer = previous[0]
        logicalReadFramebuffer = previous[1]
        viewportX = previous[2]
        viewportY = previous[3]
        viewportWidth = previous[4]
        viewportHeight = previous[5]
        framebufferDirty = !(logicalDrawFramebuffer == HOST_FBO_SENTINEL && !hostFramebufferCaptured)
        if (scissorEnabled) scissorBoxDirty = true
    }

    override fun blend(enabled: Boolean) {
        if (!renderState.blend(enabled)) return
        if (pendingProgram != null) flushPendingDraw()
        if (preserveState && frameActive && (dirtyRestoreMask and DIRTY_BLEND_ENABLE) == 0) {
            val host = GL11.glIsEnabled(GL11.GL_BLEND)
            frameSnapshot.blendEnabled = host
            dirtyRestoreMask = dirtyRestoreMask or DIRTY_BLEND_ENABLE
            if (host == enabled) return
        }
        if (enabled) GL11.glEnable(GL11.GL_BLEND) else GL11.glDisable(GL11.GL_BLEND)
    }

    override fun blendFunction(function: BlendFunction) {
        if (!renderState.blendFunction(function)) return
        if (pendingProgram != null) flushPendingDraw()
        val srcRgb = blendFactor(function.sourceColor)
        val dstRgb = blendFactor(function.destinationColor)
        val srcAlpha = blendFactor(function.sourceAlpha)
        val dstAlpha = blendFactor(function.destinationAlpha)
        val eqRgb = blendOp(function.colorOp)
        val eqAlpha = blendOp(function.alphaOp)
        if (preserveState && frameActive && (dirtyRestoreMask and DIRTY_BLEND_FUNC) == 0) {
            saveBlendFuncSnapshot()
            if (
                frameSnapshot.blendSrcRgb != srcRgb ||
                frameSnapshot.blendDstRgb != dstRgb ||
                frameSnapshot.blendSrcAlpha != srcAlpha ||
                frameSnapshot.blendDstAlpha != dstAlpha
            ) {
                GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha)
            }
            if (frameSnapshot.blendEquationRgb != eqRgb || frameSnapshot.blendEquationAlpha != eqAlpha) {
                GL20.glBlendEquationSeparate(eqRgb, eqAlpha)
            }
            return
        }
        GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha)
        GL20.glBlendEquationSeparate(eqRgb, eqAlpha)
    }

    override fun depthTest(enabled: Boolean) {
        if (!renderState.depthTest(enabled)) return
        if (pendingProgram != null) flushPendingDraw()
        if (preserveState && frameActive && (dirtyRestoreMask and DIRTY_DEPTH_ENABLE) == 0) {
            val host = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
            frameSnapshot.depthEnabled = host
            dirtyRestoreMask = dirtyRestoreMask or DIRTY_DEPTH_ENABLE
            if (host == enabled) return
        }
        if (enabled) GL11.glEnable(GL11.GL_DEPTH_TEST) else GL11.glDisable(GL11.GL_DEPTH_TEST)
    }

    override fun depthWrite(enabled: Boolean) {
        if (!renderState.depthWrite(enabled)) return
        if (pendingProgram != null) flushPendingDraw()
        if (preserveState && frameActive && (dirtyRestoreMask and DIRTY_DEPTH_WRITE) == 0) {
            val host = getInteger(GL11.GL_DEPTH_WRITEMASK) != 0
            frameSnapshot.depthWrite = host
            dirtyRestoreMask = dirtyRestoreMask or DIRTY_DEPTH_WRITE
            if (host == enabled) return
        }
        GL11.glDepthMask(enabled)
    }

    override fun depthCompare(compare: DepthCompare) {
        if (!renderState.depthCompare(compare)) return
        if (pendingProgram != null) flushPendingDraw()
        val glFunc = depthFunction(compare)
        if (preserveState && frameActive && (dirtyRestoreMask and DIRTY_DEPTH_FUNC) == 0) {
            val host = getInteger(GL11.GL_DEPTH_FUNC)
            frameSnapshot.depthFunc = host
            dirtyRestoreMask = dirtyRestoreMask or DIRTY_DEPTH_FUNC
            if (host == glFunc) return
        }
        GL11.glDepthFunc(glFunc)
    }

    override fun cull(enabled: Boolean) {
        if (!renderState.cull(enabled)) return
        if (pendingProgram != null) flushPendingDraw()
        if (preserveState && frameActive && (dirtyRestoreMask and DIRTY_CULL) == 0) {
            val host = GL11.glIsEnabled(GL11.GL_CULL_FACE)
            frameSnapshot.cullEnabled = host
            dirtyRestoreMask = dirtyRestoreMask or DIRTY_CULL
            if (host == enabled) return
        }
        if (enabled) GL11.glEnable(GL11.GL_CULL_FACE) else GL11.glDisable(GL11.GL_CULL_FACE)
    }

    override fun scissor(x: Int, y: Int, width: Int, height: Int) {
        val activeViewportWidth: Int
        val activeViewportHeight: Int
        if (logicalDrawFramebuffer == HOST_FBO_SENTINEL) {
            ensureHostViewportKnown()
            activeViewportWidth = frameSnapshot.viewportWidth
            activeViewportHeight = frameSnapshot.viewportHeight
        } else {
            activeViewportWidth = viewportWidth
            activeViewportHeight = viewportHeight
        }
        require(x >= 0 && y >= 0 && width > 0 && height > 0)
        require(x + width <= activeViewportWidth && y + height <= activeViewportHeight) {
            "Scissor [$x, $y, $width, $height] exceeds viewport [$activeViewportWidth, $activeViewportHeight]"
        }
        if (scissorEnabled && scissorX == x && scissorY == y && scissorWidth == width && scissorHeight == height) {
            return
        }
        if (pendingProgram != null) flushPendingDraw()
        scissorX = x
        scissorY = y
        scissorWidth = width
        scissorHeight = height
        if (!scissorEnabled) {
            val saveNow = preserveState && frameActive && (dirtyRestoreMask and DIRTY_SCISSOR_ENABLE) == 0
            if (saveNow) {
                saveScissorEnableIfNeeded()
                if (!frameSnapshot.scissorEnabled) {
                    GL11.glEnable(GL11.GL_SCISSOR_TEST)
                }
            } else {
                GL11.glEnable(GL11.GL_SCISSOR_TEST)
            }
            scissorEnabled = true
        }
        saveScissorBoxIfNeeded()
        scissorBoxDirty = true
        flushScissor()
    }

    override fun disableScissor() {
        if (!scissorEnabled) {
            if (preserveState && frameActive && (dirtyRestoreMask and DIRTY_SCISSOR_ENABLE) == 0) {
                saveScissorEnableIfNeeded()
                if (frameSnapshot.scissorEnabled) {
                    GL11.glDisable(GL11.GL_SCISSOR_TEST)
                }
            }
            return
        }
        if (pendingProgram != null) flushPendingDraw()
        saveScissorEnableIfNeeded()
        GL11.glDisable(GL11.GL_SCISSOR_TEST)
        scissorEnabled = false
        scissorBoxDirty = false
    }

    override fun clearColor(red: Float, green: Float, blue: Float, alpha: Float) {
        check(frameActive) { "Cannot clear outside a frame" }
        if (pendingProgram != null) flushPendingDraw()
        ensurePreserveClearState()
        flushFramebuffer()
        clearColorBuffer.put(0, red).put(1, green).put(2, blue).put(3, alpha)
        clearWithoutScissor { GL30.glClearBufferfv(GL11.GL_COLOR, 0, clearColorBuffer) }
    }

    override fun clearDepth(depth: Double) {
        check(frameActive) { "Cannot clear outside a frame" }
        require(depth in 0.0..1.0) { "Depth clear value must be in 0..1" }
        if (pendingProgram != null) flushPendingDraw()
        prepareDrawTargetAndState()
        clearDepthBuffer.put(0, depth.toFloat())
        clearWithoutScissor { GL30.glClearBufferfv(GL11.GL_DEPTH, 0, clearDepthBuffer) }
    }

    override fun pushDebugGroup(label: String) {
        val mode = resolveDebugGroupMode()
        if (mode == DEBUG_GROUP_NONE) return
        if (pendingProgram != null) flushPendingDraw()
        if (mode == DEBUG_GROUP_GL43) {
            GL43.glPushDebugGroup(GL43.GL_DEBUG_SOURCE_APPLICATION, 0, label)
        } else {
            KHRDebug.glPushDebugGroup(KHRDebug.GL_DEBUG_SOURCE_APPLICATION, 0, label)
        }
        debugGroupDepth++
    }

    override fun popDebugGroup() {
        if (debugGroupDepth <= 0) return
        if (pendingProgram != null) flushPendingDraw()
        debugGroupDepth--
        if (debugGroupMode == DEBUG_GROUP_GL43) {
            GL43.glPopDebugGroup()
        } else if (debugGroupMode == DEBUG_GROUP_KHR) {
            KHRDebug.glPopDebugGroup()
        }
    }

    private fun resolveDebugGroupMode(): Byte {
        val current = debugGroupMode
        if (current != DEBUG_GROUP_UNCHECKED) return current
        val caps = try {
            GL.getCapabilities()
        } catch (_: IllegalStateException) {
            return DEBUG_GROUP_NONE
        }
        val detected = when {
            caps.OpenGL43 -> DEBUG_GROUP_GL43
            caps.GL_KHR_debug -> DEBUG_GROUP_KHR
            else -> DEBUG_GROUP_NONE
        }
        debugGroupMode = detected
        return detected
    }

    private inline fun clearWithoutScissor(action: () -> Unit) {
        if (scissorEnabled) GL11.glDisable(GL11.GL_SCISSOR_TEST)
        try {
            action()
        } finally {
            if (scissorEnabled) GL11.glEnable(GL11.GL_SCISSOR_TEST)
        }
    }

    private fun prepareDrawTargetAndState() {
        if (preserveState && frameActive && !preserveGuiDefaultsApplied) {
            applyLazyPreserveGuiDefaults()
        }
        if (framebufferDirty) {
            flushFramebuffer()
        }
        if (scissorBoxDirty) {
            flushScissor()
        }
    }

    private fun ensurePreserveClearState() {
        if (!preserveState || !frameActive) return
        ensureColorMaskWritable()
        if (!scissorEnabled && (dirtyRestoreMask and DIRTY_SCISSOR_ENABLE) == 0) {
            saveScissorEnableIfNeeded()
            if (frameSnapshot.scissorEnabled) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST)
            }
        }
    }

    private fun applyLazyPreserveGuiDefaults() {
        if ((dirtyRestoreMask and DIRTY_DEPTH_ENABLE) == 0) depthTest(false)
        if ((dirtyRestoreMask and DIRTY_DEPTH_WRITE) == 0) depthWrite(false)
        if ((dirtyRestoreMask and DIRTY_DEPTH_FUNC) == 0) depthCompare(DepthCompare.ALWAYS)
        if ((dirtyRestoreMask and DIRTY_CULL) == 0) cull(false)
        if ((dirtyRestoreMask and DIRTY_BLEND_ENABLE) == 0) blend(true)
        if ((dirtyRestoreMask and DIRTY_BLEND_FUNC) == 0) blendFunction(BlendFunction.TRANSLUCENT)
        ensureColorMaskWritable()
        if (!scissorEnabled && (dirtyRestoreMask and DIRTY_SCISSOR_ENABLE) == 0) {
            saveScissorEnableIfNeeded()
            if (frameSnapshot.scissorEnabled) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST)
            }
        }
        preserveGuiDefaultsApplied = true
    }

    private fun ensureColorMaskWritable() {
        if ((dirtyRestoreMask and DIRTY_COLOR_MASK) != 0) return
        GL11.glGetIntegerv(GL11.GL_COLOR_WRITEMASK, colorMaskBuffer)
        val r = colorMaskBuffer[0] != 0
        val g = colorMaskBuffer[1] != 0
        val b = colorMaskBuffer[2] != 0
        val a = colorMaskBuffer[3] != 0
        frameSnapshot.colorMaskRed = r
        frameSnapshot.colorMaskGreen = g
        frameSnapshot.colorMaskBlue = b
        frameSnapshot.colorMaskAlpha = a
        dirtyRestoreMask = dirtyRestoreMask or DIRTY_COLOR_MASK
        if (!r || !g || !b || !a) {
            GL11.glColorMask(true, true, true, true)
        }
    }

    private fun saveBlendFuncSnapshot() {
        frameSnapshot.blendSrcRgb = getInteger(GL14.GL_BLEND_SRC_RGB)
        frameSnapshot.blendDstRgb = getInteger(GL14.GL_BLEND_DST_RGB)
        frameSnapshot.blendSrcAlpha = getInteger(GL14.GL_BLEND_SRC_ALPHA)
        frameSnapshot.blendDstAlpha = getInteger(GL14.GL_BLEND_DST_ALPHA)
        frameSnapshot.blendEquationRgb = getInteger(GL20.GL_BLEND_EQUATION_RGB)
        frameSnapshot.blendEquationAlpha = getInteger(GL20.GL_BLEND_EQUATION_ALPHA)
        dirtyRestoreMask = dirtyRestoreMask or DIRTY_BLEND_FUNC
    }

    private fun saveScissorEnableIfNeeded() {
        if (preserveState && frameActive && (dirtyRestoreMask and DIRTY_SCISSOR_ENABLE) == 0) {
            frameSnapshot.scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
            dirtyRestoreMask = dirtyRestoreMask or DIRTY_SCISSOR_ENABLE
        }
    }

    private fun saveScissorBoxIfNeeded() {
        if (preserveState && frameActive && (dirtyRestoreMask and DIRTY_SCISSOR_BOX) == 0) {
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBuffer)
            frameSnapshot.scissorX = scissorBuffer[0]
            frameSnapshot.scissorY = scissorBuffer[1]
            frameSnapshot.scissorWidth = scissorBuffer[2]
            frameSnapshot.scissorHeight = scissorBuffer[3]
            dirtyRestoreMask = dirtyRestoreMask or DIRTY_SCISSOR_BOX
        }
    }

    private fun ensureHostViewportKnown() {
        if (hostViewportKnown) return
        val viewport = viewportBuffer
        if (preserveState) {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport)
        } else {
            if (!Luma.platform.getViewport(viewport)) GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport)
        }
        frameSnapshot.viewportX = viewport[0]
        frameSnapshot.viewportY = viewport[1]
        frameSnapshot.viewportWidth = viewport[2]
        frameSnapshot.viewportHeight = viewport[3]
        hostViewportKnown = true
    }

    private fun captureHostFramebuffer() {
        ensureHostViewportKnown()
        if (preserveState) {
            frameSnapshot.drawFramebuffer = getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
            frameSnapshot.readFramebuffer = getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
            dirtyRestoreMask = dirtyRestoreMask or DIRTY_FRAMEBUFFER
        } else {
            frameSnapshot.drawFramebuffer = config.ownedDrawFramebuffer
            frameSnapshot.readFramebuffer = config.ownedReadFramebuffer
        }
        appliedDrawFramebuffer = frameSnapshot.drawFramebuffer
        appliedReadFramebuffer = frameSnapshot.readFramebuffer
        appliedFramebufferKnown = true
        appliedViewportX = frameSnapshot.viewportX
        appliedViewportY = frameSnapshot.viewportY
        appliedViewportWidth = frameSnapshot.viewportWidth
        appliedViewportHeight = frameSnapshot.viewportHeight
        appliedViewportKnown = true
        hostFramebufferCaptured = true
    }

    private fun flushFramebuffer() {
        if (!framebufferDirty) return
        if (!hostFramebufferCaptured) {
            captureHostFramebuffer()
        }
        val drawFbo: Int
        val readFbo: Int
        val vx: Int
        val vy: Int
        val vw: Int
        val vh: Int
        if (logicalDrawFramebuffer == HOST_FBO_SENTINEL) {
            drawFbo = frameSnapshot.drawFramebuffer
            readFbo = frameSnapshot.readFramebuffer
            vx = frameSnapshot.viewportX
            vy = frameSnapshot.viewportY
            vw = frameSnapshot.viewportWidth
            vh = frameSnapshot.viewportHeight
        } else {
            drawFbo = logicalDrawFramebuffer
            readFbo = logicalReadFramebuffer
            vx = viewportX
            vy = viewportY
            vw = viewportWidth
            vh = viewportHeight
        }
        if (!appliedFramebufferKnown || appliedDrawFramebuffer != drawFbo || appliedReadFramebuffer != readFbo) {
            if (drawFbo == readFbo) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, drawFbo)
            } else {
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo)
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo)
            }
            appliedDrawFramebuffer = drawFbo
            appliedReadFramebuffer = readFbo
            appliedFramebufferKnown = true
        }
        if (
            !appliedViewportKnown ||
            appliedViewportX != vx ||
            appliedViewportY != vy ||
            appliedViewportWidth != vw ||
            appliedViewportHeight != vh
        ) {
            GL11.glViewport(vx, vy, vw, vh)
            appliedViewportX = vx
            appliedViewportY = vy
            appliedViewportWidth = vw
            appliedViewportHeight = vh
            appliedViewportKnown = true
        }
        framebufferDirty = false
    }

    private fun flushScissor() {
        if (!scissorBoxDirty) return
        scissorBoxDirty = false
        if (!scissorEnabled) return
        val vx: Int
        val vy: Int
        val vh: Int
        if (logicalDrawFramebuffer == HOST_FBO_SENTINEL) {
            ensureHostViewportKnown()
            vx = frameSnapshot.viewportX
            vy = frameSnapshot.viewportY
            vh = frameSnapshot.viewportHeight
        } else {
            vx = viewportX
            vy = viewportY
            vh = viewportHeight
        }
        GL11.glScissor(
            vx + scissorX,
            vy + vh - scissorY - scissorHeight,
            scissorWidth,
            scissorHeight
        )
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
        appliedFramebufferKnown = false
        appliedViewportKnown = false
        ownedFixedStateKnown = false
    }

    fun <T> externalGl(action: () -> T): T {
        flushPendingDraw()
        flushFramebuffer()
        flushScissor()
        if (frameActive && preserveState) {
            captureAllUnsavedState(frameSnapshot)
        }
        pixelUnpackState.invalidate()
        invalidateStateCache()
        return try {
            action()
        } finally {
            invalidateStateCache()
            if (frameActive && (targetDepth > 0 || hostFramebufferCaptured)) {
                framebufferDirty = true
            }
            if (scissorEnabled) scissorBoxDirty = true
        }
    }

    private fun captureAllUnsavedState(snapshot: GlStateSnapshot) {
        val mask = dirtyRestoreMask
        if ((mask and DIRTY_FRAMEBUFFER) == 0) {
            ensureHostViewportKnown()
            snapshot.drawFramebuffer = getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
            snapshot.readFramebuffer = getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
            hostFramebufferCaptured = true
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
        if ((mask and DIRTY_COLOR_MASK) == 0) {
            GL11.glGetIntegerv(GL11.GL_COLOR_WRITEMASK, colorMaskBuffer)
            snapshot.colorMaskRed = colorMaskBuffer[0] != 0
            snapshot.colorMaskGreen = colorMaskBuffer[1] != 0
            snapshot.colorMaskBlue = colorMaskBuffer[2] != 0
            snapshot.colorMaskAlpha = colorMaskBuffer[3] != 0
        }
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
        dirtyRestoreMask = ALL_DIRTY_MASK
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

    private fun restoreState(snapshot: GlStateSnapshot) {
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

    private fun prepareTextureUnit(unit: Int) {
        activateTextureUnit(unit)
        ensureSamplerCapacity(unit + 1)
        if (!preserveState) {
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
        if (preserveState && frameActive && (dirtyRestoreMask and DIRTY_ACTIVE_TEXTURE) == 0) {
            val hostActive = getInteger(GL13.GL_ACTIVE_TEXTURE)
            frameSnapshot.activeTexture = hostActive
            dirtyRestoreMask = dirtyRestoreMask or DIRTY_ACTIVE_TEXTURE
            val hostUnit = hostActive - GL13.GL_TEXTURE0
            if (hostUnit == unit) {
                activeTextureUnit = unit
                return
            }
        }
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

    private fun getInteger(parameter: Int): Int {
        GL11.glGetIntegerv(parameter, scalarStateBuffer)
        return scalarStateBuffer.get(0)
    }

    private fun prepareOwnedFrame() {
        frameSnapshot.drawFramebuffer = config.ownedDrawFramebuffer
        frameSnapshot.readFramebuffer = config.ownedReadFramebuffer
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
        const val DEBUG_GROUP_UNCHECKED: Byte = -1
        const val DEBUG_GROUP_NONE: Byte = 0
        const val DEBUG_GROUP_GL43: Byte = 1
        const val DEBUG_GROUP_KHR: Byte = 2
        const val HOST_FBO_SENTINEL = -2

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
        const val ALL_DIRTY_MASK = (1 shl 14) - 1

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

        val PrimitiveType.glPrimitive: Int
            get() = when (this) {
                PrimitiveType.TRIANGLES -> GL11.GL_TRIANGLES
                PrimitiveType.LINES -> GL11.GL_LINES
                PrimitiveType.QUADS -> GL11.GL_TRIANGLES
            }

        val IndexType.glType: Int
            get() = when (this) {
                IndexType.UINT16 -> GL11.GL_UNSIGNED_SHORT
                IndexType.UINT32 -> GL11.GL_UNSIGNED_INT
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
