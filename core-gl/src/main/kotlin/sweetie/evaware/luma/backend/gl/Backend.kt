package sweetie.evaware.luma.backend.gl

import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.util.Arrays
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.*
import sweetie.evaware.luma.api.*
import sweetie.evaware.luma.shader.translator.ShaderReflection
import sweetie.evaware.luma.texture.RgbaTransferBuffer
import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.VertexInputLayout

class Backend(
    private val config: GlBackendConfig = GlBackendConfig()
) : RenderBackend {
    private val preserveState = config.statePolicy == GlStatePolicy.PRESERVE
    private val stateGuard = GlStateGuard(preserveState)
    private val framebuffers = GlFramebufferStack(config, stateGuard)
    private val batcher = GlImmediateBatcher()
    private val samplerCache = GlSamplerCache()
    private val renderState = GlRenderStateCache()
    private val textureBindings = TextureBindingCache(config.initialTextureUnits)
    private val pixelUnpackState = GlPixelUnpackState(preserveState)
    private val textureTransfer = RgbaTransferBuffer()

    private val clearColorBuffer = BufferUtils.createFloatBuffer(4)
    private val clearDepthBuffer = BufferUtils.createFloatBuffer(1)
    private val clearStencilBuffer = BufferUtils.createIntBuffer(1)

    private var boundProgramId = -1
    private var boundVertexArrayId = -1
    private var boundArrayBufferId = -1
    private var samplerBindings = IntArray(config.initialTextureUnits) { UNKNOWN_SAMPLER_BINDING }
    private var activeTextureUnit = -1
    private var frameActive = false

    // Direct draw bindings
    private val directVertexBuffers = arrayOfNulls<GlVertexBufferHandle>(16)
    private val directVertexOffsets = LongArray(16)
    private var directIndexBuffer: GlIndexBufferHandle? = null

    // One-time debug group and timestamp capability state
    private var debugGroupMode: Byte = DEBUG_GROUP_UNCHECKED
    private var debugGroupDepth = 0
    private var timestampMode: Byte = TIMESTAMP_UNCHECKED

    override val supportsStencil: Boolean = true
    override val supportsGpuTimestamps: Boolean get() = resolveTimestampMode() != TIMESTAMP_NONE
    override val isDepthTestEnabled: Boolean get() = renderState.isDepthTestEnabled
    override val isDepthWriteEnabled: Boolean get() = renderState.isDepthWriteEnabled
    override val currentDepthCompare: DepthCompare get() = renderState.currentDepthCompare
    override val colorMaskRed: Boolean get() = renderState.colorMaskRed
    override val colorMaskGreen: Boolean get() = renderState.colorMaskGreen
    override val colorMaskBlue: Boolean get() = renderState.colorMaskBlue
    override val colorMaskAlpha: Boolean get() = renderState.colorMaskAlpha

    override fun beginFrame() {
        stateGuard.beginFrame()
        batcher.clear()
        framebuffers.beginFrame()
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
                    stateGuard.restoreState()
                } else {
                    framebuffers.endOwnedFrame()
                }
            }
        } finally {
            batcher.clear()
            frameActive = false
            stateGuard.resetAfterFrame()
            framebuffers.resetAfterFrame()
            if (preserveState) {
                invalidateStateCache()
            } else {
                invalidateBindingCache()
            }
        }
    }

    override fun createProgram(
        vertexSource: String,
        fragmentSource: String,
        layouts: VertexInputLayout,
        reflection: ShaderReflection?
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
                error("Failed to link program: ${GL20.glGetProgramInfoLog(programId)}")
            }
            loaded = true
            return Program(programId, layouts)
        } finally {
            if (vertShader != 0) GL20.glDeleteShader(vertShader)
            if (fragShader != 0) GL20.glDeleteShader(fragShader)
            if (!loaded && programId != 0) GL20.glDeleteProgram(programId)
        }
    }

    override fun bindProgram(program: ProgramHandle) {
        val glProgram = program as Program
        if (batcher.pendingProgram != null && batcher.pendingProgram !== glProgram) {
            flushPendingDraw()
        }
        useProgram(glProgram.programId)
        bindVertexArray(glProgram.vertexBuffer.vao)
    }

    override fun createTexture(
        image: BufferedImage,
        mipmap: Boolean,
        format: TextureFormat,
        sampler: SamplerState
    ): TextureHandle {
        require(format.supportsBufferedImageUpload) {
            "BufferedImage upload is not supported for format $format; upload raw ByteBuffer data instead"
        }
        val samplerId = samplerCache.get(sampler, mipmap)
        if (!frameActive) return GlTexture.create(image, mipmap, format, sampler, samplerId, ownsSampler = false)
        flushPendingDraw()
        pixelUnpackState.prepare()
        val texture = GlTexture(
            textureId = GL11.glGenTextures(),
            width = image.width,
            height = image.height,
            mipmap = mipmap,
            samplerId = samplerId,
            ownsTexture = true,
            format = format,
            samplerState = sampler,
            ownsSampler = false
        )
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
        mipmap: Boolean,
        sampler: SamplerState
    ): TextureHandle {
        require(width > 0 && height > 0) { "Texture dimensions must be positive: ${width}x${height}" }
        val samplerId = samplerCache.get(sampler, mipmap)
        if (!frameActive) return GlTexture.createRaw(width, height, data, format, mipmap, sampler, samplerId, ownsSampler = false)
        flushPendingDraw()
        pixelUnpackState.prepare()
        val texture = GlTexture(
            textureId = GL11.glGenTextures(),
            width = width,
            height = height,
            mipmap = mipmap,
            samplerId = samplerId,
            ownsTexture = true,
            format = format,
            samplerState = sampler,
            ownsSampler = false
        )
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

    override fun setTextureSampler(texture: TextureHandle, sampler: SamplerState) {
        val glTexture = texture as GlTexture
        glTexture.requireOpen()
        if (batcher.hasPending) flushPendingDraw()
        val samplerId = samplerCache.get(sampler, glTexture.mipmap)
        glTexture.replaceSampler(sampler, samplerId, newOwnsSampler = false)
        if (frameActive) {
            textureBindings.forEachBoundUnit(glTexture) { unit ->
                bindSampler(unit, samplerId)
            }
        }
    }

    override fun generateMipmaps(texture: TextureHandle) {
        val glTexture = texture as GlTexture
        glTexture.requireOpen()
        if (batcher.hasPending) flushPendingDraw()
        val samplerId = samplerCache.get(glTexture.samplerState, mipmap = true)
        glTexture.replaceSampler(glTexture.samplerState, samplerId, newOwnsSampler = false)
        if (frameActive) {
            bindTextureForUpload(glTexture)
            glTexture.generateMipmapsBound()
            textureBindings.forEachBoundUnit(glTexture) { unit ->
                bindSampler(unit, samplerId)
            }
        } else {
            glTexture.generateMipmapsUnbound()
        }
    }

    override fun bindTexture(texture: TextureHandle, unit: Int) {
        require(unit >= 0) { "Texture unit must be non-negative: $unit" }
        val glTexture = texture as GlTexture
        glTexture.requireOpen()
        if (textureBindings.isBound(unit, glTexture)) {
            if (frameActive && unit < samplerBindings.size && samplerBindings[unit] != glTexture.samplerId) {
                if (batcher.hasPending) flushPendingDraw()
                bindSampler(unit, glTexture.samplerId)
            }
            return
        }
        if (batcher.hasPending) flushPendingDraw()
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
        if (batcher.hasPending) flushPendingDraw()
        (buffer as GlVertexBufferHandle).update(offsetBytes, data)
        boundArrayBufferId = -1
    }

    override fun updateIndexBuffer(buffer: IndexBufferHandle, offsetBytes: Long, data: ByteBuffer) {
        if (batcher.hasPending) flushPendingDraw()
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
        if (batcher.hasPending) flushPendingDraw()
        prepareDrawTargetAndState()
        prepareDirectDraw(program, uniforms)
        val mode = GlEnums.primitiveMode(primitiveType)
        if (firstInstance == 0) {
            if (instanceCount == 1) GL11.glDrawArrays(mode, firstVertex, vertexCount)
            else GL31.glDrawArraysInstanced(mode, firstVertex, vertexCount, instanceCount)
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
        if (batcher.hasPending) flushPendingDraw()
        prepareDrawTargetAndState()
        val glProgram = prepareDirectDraw(program, uniforms)
        val indices = requireNotNull(directIndexBuffer) { "Index buffer is not bound" }
        indices.requireOpen()
        if (glProgram.configuredElementBuffer !== indices) {
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indices.id)
            glProgram.configuredElementBuffer = indices
        }
        val mode = GlEnums.primitiveMode(primitiveType)
        val glIndexType = GlEnums.indexType(indices.indexType)
        val byteOffset = firstIndex.toLong() * indices.indexType.byteSize
        if (firstInstance == 0) {
            if (instanceCount == 1) {
                if (vertexOffset == 0) GL11.glDrawElements(mode, indexCount, glIndexType, byteOffset)
                else GL32.glDrawElementsBaseVertex(mode, indexCount, glIndexType, byteOffset, vertexOffset)
            } else if (vertexOffset == 0) {
                GL31.glDrawElementsInstanced(mode, indexCount, glIndexType, byteOffset, instanceCount)
            } else {
                GL32.glDrawElementsInstancedBaseVertex(mode, indexCount, glIndexType, byteOffset, instanceCount, vertexOffset)
            }
        } else {
            GL42.glDrawElementsInstancedBaseVertexBaseInstance(
                mode, indexCount, glIndexType, byteOffset, instanceCount, vertexOffset, firstInstance
            )
        }
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

        if (frameActive && batcher.tryAppend(
                glProgram,
                primitiveType,
                uniforms,
                vertices,
                vertexCount,
                upload = ::uploadVertexBuffer,
                flushCurrent = ::flushPendingDraw
            )
        ) {
            return
        }

        if (batcher.hasPending) flushPendingDraw()
        prepareDrawTargetAndState()
        useProgram(glProgram.programId)
        bindVertexArray(vb.vao)

        val baseVertex = uploadVertexBuffer(vb, vertices)
        glProgram.uploadUniforms(uniforms)

        if (frameActive) {
            batcher.recordPending(glProgram, primitiveType, baseVertex, vertexCount, vb.layout.instanced)
        } else {
            batcher.executeDraw(glProgram, primitiveType, baseVertex, vertexCount, vb.layout.instanced)
        }
    }

    override fun createRenderTarget(
        width: Int,
        height: Int,
        useDepth: Boolean,
        format: RenderTargetFormat,
        sampler: SamplerState,
        useStencil: Boolean
    ): RenderTargetHandle {
        val colorSamplerId = samplerCache.get(sampler, mipmap = false)
        val depthSamplerId = if (useDepth || useStencil) {
            samplerCache.get(SamplerState.NEAREST_CLAMP, mipmap = false)
        } else {
            0
        }
        return GlRenderTarget.create(
            width = width,
            height = height,
            useDepth = useDepth,
            format = format,
            sampler = sampler,
            useStencil = useStencil,
            colorSamplerId = colorSamplerId,
            ownsColorSampler = false,
            depthSamplerId = depthSamplerId,
            ownsDepthSampler = false
        )
    }

    override fun beginRenderTarget(target: RenderTargetHandle, clearColor: FloatArray?) {
        check(frameActive) { "Cannot begin a render target outside a frame" }
        if (batcher.hasPending) flushPendingDraw()
        val glTarget = target as GlRenderTarget
        framebuffers.pushTarget(glTarget.fbo, glTarget.width, glTarget.height)
        if (clearColor != null) {
            ensurePreserveClearState()
            framebuffers.flushFramebuffer()
            clearWithUnmaskedColor { GL30.glClearBufferfv(GL11.GL_COLOR, 0, clearColor) }
        }
    }

    override fun endRenderTarget() {
        if (batcher.hasPending) flushPendingDraw()
        framebuffers.popTarget()
    }

    override fun blitRenderTarget(
        source: RenderTargetHandle,
        destination: RenderTargetHandle?,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int,
        dstX: Int,
        dstY: Int,
        dstWidth: Int,
        dstHeight: Int,
        filter: TextureFilter,
        copyColor: Boolean,
        copyDepth: Boolean
    ) {
        if (batcher.hasPending) flushPendingDraw()
        framebuffers.blit(
            frameActive,
            source as GlRenderTarget,
            destination as? GlRenderTarget,
            srcX, srcY, srcWidth, srcHeight,
            dstX, dstY, dstWidth, dstHeight,
            filter, copyColor, copyDepth
        )
    }

    override fun blend(enabled: Boolean) {
        if (!renderState.blend(enabled)) return
        if (batcher.hasPending) flushPendingDraw()
        if (stateGuard.saveBlendEnableAndMatchesHost(frameActive, enabled)) return
        if (enabled) GL11.glEnable(GL11.GL_BLEND) else GL11.glDisable(GL11.GL_BLEND)
    }

    override fun blendFunction(function: BlendFunction) {
        if (!renderState.blendFunction(function)) return
        if (batcher.hasPending) flushPendingDraw()
        val srcRgb = GlEnums.blendFactor(function.sourceColor)
        val dstRgb = GlEnums.blendFactor(function.destinationColor)
        val srcAlpha = GlEnums.blendFactor(function.sourceAlpha)
        val dstAlpha = GlEnums.blendFactor(function.destinationAlpha)
        val eqRgb = GlEnums.blendOp(function.colorOp)
        val eqAlpha = GlEnums.blendOp(function.alphaOp)
        if (stateGuard.saveBlendFuncAndApplyIfFirst(frameActive, srcRgb, dstRgb, srcAlpha, dstAlpha, eqRgb, eqAlpha)) {
            return
        }
        GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha)
        GL20.glBlendEquationSeparate(eqRgb, eqAlpha)
    }

    override fun colorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) {
        if (!renderState.colorMask(red, green, blue, alpha)) return
        if (batcher.hasPending) flushPendingDraw()
        if (stateGuard.saveColorMaskAndMatchesHost(frameActive, red, green, blue, alpha)) return
        GL11.glColorMask(red, green, blue, alpha)
    }

    override fun depthTest(enabled: Boolean) {
        if (!renderState.depthTest(enabled)) return
        if (batcher.hasPending) flushPendingDraw()
        if (stateGuard.saveDepthEnableAndMatchesHost(frameActive, enabled)) return
        if (enabled) GL11.glEnable(GL11.GL_DEPTH_TEST) else GL11.glDisable(GL11.GL_DEPTH_TEST)
    }

    override fun depthWrite(enabled: Boolean) {
        if (!renderState.depthWrite(enabled)) return
        if (batcher.hasPending) flushPendingDraw()
        if (stateGuard.saveDepthWriteAndMatchesHost(frameActive, enabled)) return
        GL11.glDepthMask(enabled)
    }

    override fun depthCompare(compare: DepthCompare) {
        if (!renderState.depthCompare(compare)) return
        if (batcher.hasPending) flushPendingDraw()
        val glFunc = GlEnums.depthFunction(compare)
        if (stateGuard.saveDepthFuncAndMatchesHost(frameActive, glFunc)) return
        GL11.glDepthFunc(glFunc)
    }

    override fun stencilTest(enabled: Boolean) {
        if (!renderState.stencilTest(enabled)) return
        if (batcher.hasPending) flushPendingDraw()
        if (stateGuard.saveStencilEnableAndMatchesHost(frameActive, enabled)) return
        if (enabled) GL11.glEnable(GL11.GL_STENCIL_TEST) else GL11.glDisable(GL11.GL_STENCIL_TEST)
    }

    override fun stencilFunc(compare: StencilCompare, ref: Int, readMask: Int) {
        if (!renderState.stencilFunc(compare, ref, readMask)) return
        if (batcher.hasPending) flushPendingDraw()
        stateGuard.saveStencilFuncIfNeeded(frameActive)
        GL11.glStencilFunc(GlEnums.stencilFunction(compare), ref, readMask)
    }

    override fun stencilOp(stencilFail: StencilOp, depthFail: StencilOp, pass: StencilOp) {
        if (!renderState.stencilOp(stencilFail, depthFail, pass)) return
        if (batcher.hasPending) flushPendingDraw()
        stateGuard.saveStencilOpIfNeeded(frameActive)
        GL11.glStencilOp(
            GlEnums.stencilOperation(stencilFail),
            GlEnums.stencilOperation(depthFail),
            GlEnums.stencilOperation(pass)
        )
    }

    override fun stencilMask(writeMask: Int) {
        if (!renderState.stencilMask(writeMask)) return
        if (batcher.hasPending) flushPendingDraw()
        stateGuard.saveStencilMaskIfNeeded(frameActive)
        GL11.glStencilMask(writeMask)
    }

    override fun cull(enabled: Boolean) {
        if (!renderState.cull(enabled)) return
        if (batcher.hasPending) flushPendingDraw()
        if (stateGuard.saveCullAndMatchesHost(frameActive, enabled)) return
        if (enabled) GL11.glEnable(GL11.GL_CULL_FACE) else GL11.glDisable(GL11.GL_CULL_FACE)
    }

    override fun scissor(x: Int, y: Int, width: Int, height: Int) {
        framebuffers.setScissor(frameActive, x, y, width, height, ::flushPendingDraw)
    }

    override fun disableScissor() {
        framebuffers.disableScissor(frameActive, ::flushPendingDraw)
    }

    override fun clearColor(red: Float, green: Float, blue: Float, alpha: Float) {
        check(frameActive) { "Cannot clear outside a frame" }
        if (batcher.hasPending) flushPendingDraw()
        ensurePreserveClearState()
        framebuffers.flushFramebuffer()
        clearColorBuffer.put(0, red).put(1, green).put(2, blue).put(3, alpha)
        clearWithUnmaskedColor { GL30.glClearBufferfv(GL11.GL_COLOR, 0, clearColorBuffer) }
    }

    override fun clearDepth(depth: Double) {
        check(frameActive) { "Cannot clear outside a frame" }
        require(depth in 0.0..1.0) { "Depth clear value must be in 0..1" }
        if (batcher.hasPending) flushPendingDraw()
        prepareDrawTargetAndState()
        clearDepthBuffer.put(0, depth.toFloat())
        val restoreDepthWrite = !renderState.isDepthWriteEnabled
        if (restoreDepthWrite) {
            stateGuard.saveDepthWriteIfNeeded(frameActive)
            GL11.glDepthMask(true)
        }
        try {
            framebuffers.withoutScissor { GL30.glClearBufferfv(GL11.GL_DEPTH, 0, clearDepthBuffer) }
        } finally {
            if (restoreDepthWrite) GL11.glDepthMask(false)
        }
    }

    override fun clearStencil(value: Int) {
        check(frameActive) { "Cannot clear outside a frame" }
        if (batcher.hasPending) flushPendingDraw()
        prepareDrawTargetAndState()
        clearStencilBuffer.put(0, value)
        val previousMask = renderState.currentStencilWriteMask
        val needsFullMask = previousMask != 0xFF || (preserveState && !stateGuard.isDirty(GlStateGuard.DIRTY_STENCIL_MASK))
        if (needsFullMask) {
            stateGuard.saveStencilMaskIfNeeded(frameActive)
            GL11.glStencilMask(0xFF)
        }
        try {
            framebuffers.withoutScissor { GL30.glClearBufferiv(GL11.GL_STENCIL, 0, clearStencilBuffer) }
        } finally {
            if (needsFullMask && previousMask != 0xFF) {
                GL11.glStencilMask(previousMask)
            }
        }
    }

    override fun pushDebugGroup(label: String) {
        val mode = resolveDebugGroupMode()
        if (mode == DEBUG_GROUP_NONE) return
        if (batcher.hasPending) flushPendingDraw()
        if (mode == DEBUG_GROUP_GL43) GL43.glPushDebugGroup(GL43.GL_DEBUG_SOURCE_APPLICATION, 0, label)
        else KHRDebug.glPushDebugGroup(KHRDebug.GL_DEBUG_SOURCE_APPLICATION, 0, label)
        debugGroupDepth++
    }

    override fun popDebugGroup() {
        if (debugGroupDepth <= 0) return
        if (batcher.hasPending) flushPendingDraw()
        debugGroupDepth--
        if (debugGroupMode == DEBUG_GROUP_GL43) GL43.glPopDebugGroup()
        else if (debugGroupMode == DEBUG_GROUP_KHR) KHRDebug.glPopDebugGroup()
    }

    override fun createTimestampPool(capacity: Int): GpuTimestampPool {
        val mode = resolveTimestampMode()
        if (mode == TIMESTAMP_NONE) {
            throw UnsupportedOperationException("OpenGL GPU timestamp queries require OpenGL 3.3 or GL_ARB_timer_query")
        }
        return GlTimestampPool(capacity, useArb = mode == TIMESTAMP_ARB)
    }

    override fun writeTimestamp(pool: GpuTimestampPool, queryIndex: Int) {
        if (batcher.hasPending) flushPendingDraw()
        (pool as GlTimestampPool).write(queryIndex)
    }

    override fun close() {
        samplerCache.close()
        textureTransfer.close()
        GlTexture.closeTransferBuffer()
    }

    override fun hasContext(): Boolean = GLFW.glfwGetCurrentContext() != 0L

    fun invalidateStateCache() {
        invalidateBindingCache()
        activeTextureUnit = -1
        renderState.invalidate()
        framebuffers.invalidateApplied()
        stateGuard.ownedFixedStateKnown = false
    }

    fun <T> externalGl(action: () -> T): T {
        flushPendingDraw()
        framebuffers.flushFramebuffer()
        framebuffers.flushScissor()
        if (frameActive && preserveState) {
            stateGuard.captureAllUnsavedState(
                ensureHostViewport = framebuffers::ensureHostViewportKnown,
                markHostFramebufferCaptured = framebuffers::markHostFramebufferCaptured
            )
        }
        pixelUnpackState.invalidate()
        invalidateStateCache()
        return try {
            action()
        } finally {
            invalidateStateCache()
            framebuffers.markDirtyAfterExternal(frameActive)
        }
    }

    private fun prepareDrawTargetAndState() {
        if (preserveState && frameActive && !stateGuard.preserveGuiDefaultsApplied) {
            applyLazyPreserveGuiDefaults()
        }
        if (framebuffers.framebufferDirty) framebuffers.flushFramebuffer()
        if (framebuffers.scissorBoxDirty) framebuffers.flushScissor()
    }

    private fun ensurePreserveClearState() {
        if (!preserveState || !frameActive) return
        stateGuard.ensureColorMaskWritable(frameActive)
        stateGuard.ensureHostScissorDisabled(frameActive, framebuffers.scissorEnabled)
    }

    private inline fun clearWithUnmaskedColor(action: () -> Unit) {
        val masked = renderState.currentColorMaskBits != GlRenderStateCache.FULL_COLOR_MASK
        if (masked) GL11.glColorMask(true, true, true, true)
        try {
            framebuffers.withoutScissor(action)
        } finally {
            if (masked) {
                GL11.glColorMask(
                    renderState.colorMaskRed,
                    renderState.colorMaskGreen,
                    renderState.colorMaskBlue,
                    renderState.colorMaskAlpha
                )
            }
        }
    }

    private fun applyLazyPreserveGuiDefaults() {
        if (!stateGuard.isDirty(GlStateGuard.DIRTY_DEPTH_ENABLE)) depthTest(false)
        if (!stateGuard.isDirty(GlStateGuard.DIRTY_DEPTH_WRITE)) depthWrite(false)
        if (!stateGuard.isDirty(GlStateGuard.DIRTY_DEPTH_FUNC)) depthCompare(DepthCompare.ALWAYS)
        if (!stateGuard.isDirty(GlStateGuard.DIRTY_STENCIL_ENABLE)) stencilTest(false)
        if (!stateGuard.isDirty(GlStateGuard.DIRTY_CULL)) cull(false)
        if (!stateGuard.isDirty(GlStateGuard.DIRTY_BLEND_ENABLE)) blend(true)
        if (!stateGuard.isDirty(GlStateGuard.DIRTY_BLEND_FUNC)) blendFunction(BlendFunction.TRANSLUCENT)
        if (!stateGuard.isDirty(GlStateGuard.DIRTY_COLOR_MASK)) {
            stateGuard.ensureColorMaskWritable(frameActive)
            renderState.colorMask(true, true, true, true)
        }
        stateGuard.ensureHostScissorDisabled(frameActive, framebuffers.scissorEnabled)
        stateGuard.preserveGuiDefaultsApplied = true
    }

    private fun prepareOwnedFrame() {
        stateGuard.snapshot.drawFramebuffer = config.ownedDrawFramebuffer
        stateGuard.snapshot.readFramebuffer = config.ownedReadFramebuffer
        depthTest(false)
        depthWrite(false)
        depthCompare(DepthCompare.ALWAYS)
        stencilTest(false)
        cull(false)
        blend(true)
        blendFunction(BlendFunction.TRANSLUCENT)
        colorMask(true, true, true, true)
        if (!stateGuard.ownedFixedStateKnown) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST)
            stateGuard.ownedFixedStateKnown = true
        }
        activateTextureUnit(0)
    }

    private fun prepareDirectDraw(program: ProgramHandle, uniforms: ShaderUniforms): Program {
        val glProgram = program as Program
        useProgram(glProgram.programId)
        bindVertexArray(glProgram.directVertexArray)
        if (glProgram.configureDirectBindings(directVertexBuffers, directVertexOffsets) {
                stateGuard.saveArrayBufferIfNeeded(frameActive)
            }
        ) {
            boundArrayBufferId = -1
        }
        glProgram.uploadUniforms(uniforms)
        return glProgram
    }

    private fun uploadVertexBuffer(vb: GlVertexBuffer, vertices: FloatBuffer): Int {
        return vb.uploadStreaming(vertices) { boundId ->
            stateGuard.saveArrayBufferIfNeeded(frameActive)
            boundArrayBufferId = boundId
        }
    }

    private fun flushPendingDraw() {
        batcher.flush { glProgram ->
            useProgram(glProgram.programId)
            bindVertexArray(glProgram.vertexBuffer.vao)
        }
    }

    private fun useProgram(programId: Int) {
        if (boundProgramId == programId) return
        if (stateGuard.saveProgramAndMatchesHost(frameActive, programId)) {
            boundProgramId = programId
            return
        }
        GL20.glUseProgram(programId)
        boundProgramId = programId
    }

    private fun bindVertexArray(vertexArrayId: Int) {
        if (boundVertexArrayId == vertexArrayId) return
        if (stateGuard.saveVaoAndMatchesHost(frameActive, vertexArrayId)) {
            boundVertexArrayId = vertexArrayId
            return
        }
        GL30.glBindVertexArray(vertexArrayId)
        boundVertexArrayId = vertexArrayId
    }

    private fun invalidateBindingCache() {
        boundProgramId = -1
        boundVertexArrayId = -1
        boundArrayBufferId = -1
        textureBindings.invalidateAll()
        samplerBindings.fill(UNKNOWN_SAMPLER_BINDING)
    }

    private fun prepareTextureUnit(unit: Int) {
        activateTextureUnit(unit)
        ensureSamplerCapacity(unit + 1)
        if (!preserveState) {
            bindSampler(unit, 0)
            return
        }
        val snapshot = stateGuard.snapshot
        if (snapshot.hasTextureUnit(unit)) return
        val sampler = stateGuard.getInteger(GL33.GL_SAMPLER_BINDING)
        snapshot.addTextureUnit(unit, stateGuard.getInteger(GL11.GL_TEXTURE_BINDING_2D), sampler)
        if (sampler != 0) GL33.glBindSampler(unit, 0)
        samplerBindings[unit] = 0
    }

    private fun activateTextureUnit(unit: Int) {
        if (activeTextureUnit == unit) return
        if (stateGuard.saveActiveTextureAndMatchesHost(frameActive, unit)) {
            activeTextureUnit = unit
            return
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

    private fun resolveTimestampMode(): Byte {
        val current = timestampMode
        if (current != TIMESTAMP_UNCHECKED) return current
        val caps = try {
            GL.getCapabilities()
        } catch (_: IllegalStateException) {
            return TIMESTAMP_NONE
        }
        val detected = when {
            caps.OpenGL33 -> TIMESTAMP_GL33
            caps.GL_ARB_timer_query -> TIMESTAMP_ARB
            else -> TIMESTAMP_NONE
        }
        timestampMode = detected
        return detected
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

    private companion object {
        const val UNKNOWN_SAMPLER_BINDING = Int.MIN_VALUE
        const val DEBUG_GROUP_UNCHECKED: Byte = -1
        const val DEBUG_GROUP_NONE: Byte = 0
        const val DEBUG_GROUP_GL43: Byte = 1
        const val DEBUG_GROUP_KHR: Byte = 2
        const val TIMESTAMP_UNCHECKED: Byte = -1
        const val TIMESTAMP_NONE: Byte = 0
        const val TIMESTAMP_GL33: Byte = 1
        const val TIMESTAMP_ARB: Byte = 2
    }
}
