package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuFence
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.resources.Identifier
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.LumaNames
import sweetie.evaware.luma.api.BlendFunction
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
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.api.VertexBufferHandle
import sweetie.evaware.luma.uniform.*
import sweetie.evaware.luma.vertex.VertexLayout
import sweetie.evaware.luma.vertex.VertexInputLayout
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.FloatBuffer

class Backend(
    private val config: VulkanBackendConfig = VulkanBackendConfig()
) : RenderBackend {
    private var nextProgramId = 1

    private var frameId = 0L
    private var pipelineGeneration = 0L

    private val vertexBuffer = VulkanBuffer(
        { LumaNames.VERTEX_BUFFER },
        GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST,
        config.initialVertexBufferBytes
    )
    private val uboBuffer = VulkanBuffer(
        { LumaNames.UBO_BUFFER },
        GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST,
        config.initialUniformBufferBytes
    )

    private val boundTextures = arrayOfNulls<TextureHandle>(DrawCall.TEXTURE_UNITS)
    private val textureSnapshots = ArrayList<Array<TextureHandle?>>()
    private var textureSnapshotCount = 0
    private var textureSnapshot: Array<TextureHandle?> = DrawCall.NO_TEXTURES
    private var texturesDirty = false

    private val vertexStaging = StagingBuffer(config.initialVertexStagingBytes)
    private val uboStaging = StagingBuffer(config.initialUniformStagingBytes)
    private val uniformOffsetAlignment = RenderSystem.getDevice().deviceInfo.limits.minUniformOffsetAlignment

    private val recorder = DrawCallRecorder()
    private val merger = DrawCallMerger()
    private val passEncoder = RenderPassEncoder(vertexBuffer, uboBuffer)
    private val renderState = RenderStateTracker()
    private val postRecordActions = ArrayList<() -> Unit>()
    private val samplerCache = VulkanSamplerCache(RenderSystem.getDevice())
    private val textureUploads = TextureUploadQueue(config.maxPooledUploadBytes)
    private val bufferUploads = BufferUploadQueue(config.initialBufferUploadBytes)

    private val boundVertexBuffers = arrayOfNulls<VulkanVertexBuffer>(RenderPass.MAX_VERTEX_BUFFERS)
    private val boundVertexOffsets = LongArray(RenderPass.MAX_VERTEX_BUFFERS)
    private val vertexBindingSnapshots = ArrayList<VertexBindingSnapshot>()
    private var vertexBindingSnapshotCount = 0
    private var vertexBindingsDirty = true
    private var currentVertexBindings: VertexBindingSnapshot? = null
    private var boundIndexBuffer: VulkanIndexBuffer? = null

    private val targetStack = ArrayList<VulkanRenderTarget?>()
    private val targetClearColors = ArrayList<FloatArray?>()
    private var targetPassId = 0
    private var completionFenceRequested = false
    private var completionFence: GpuFence? = null

    override fun beginFrame() {
        VulkanResourceRetirement.collectCompleted()
        renderState.beginFrame()
        frameId++
        recorder.reset()
        resetTextureSnapshots()
        vertexStaging.reset()
        uboStaging.reset()
        bufferUploads.beginFrame()
        resetVertexBindingSnapshots()

        targetStack.clear()
        targetClearColors.clear()
        targetStack.add(null)
        targetClearColors.add(null)
        targetPassId = 0
    }

    override fun endFrame() {
        if (
            recorder.size == 0 &&
            !textureUploads.hasPending() &&
            !bufferUploads.hasPending() &&
            !VulkanResourceRetirement.hasPending() &&
            !completionFenceRequested
        ) {
            runPostRecordActions()
            return
        }

        val device = RenderSystem.getDevice()
        val encoder = device.createCommandEncoder()

        textureUploads.record(encoder)
        bufferUploads.record(encoder)

        if (recorder.size > 0) {
            vertexStaging.flip()
            vertexBuffer.write(encoder, vertexStaging.buffer)

            uboStaging.flip()
            uboBuffer.write(encoder, uboStaging.buffer)

            val pass = passEncoder.begin(device, encoder, pipelineGeneration)
            try {
                merger.run(recorder, pass)
            } finally {
                pass.finish()
            }
        }

        val retirement = VulkanResourceRetirement.attachTo(encoder)
        val requestedFence = if (completionFenceRequested) encoder.createFence() else null
        encoder.submit()
        VulkanResourceRetirement.submitted(retirement)
        completionFence = requestedFence
        completionFenceRequested = false
        runPostRecordActions()
    }

    override fun hasContext(): Boolean = true

    fun requestCompletionFence() {
        check(!completionFenceRequested && completionFence == null) { "A completion fence is already pending" }
        completionFenceRequested = true
    }

    fun awaitCompletionFence() {
        val fence = checkNotNull(completionFence) { "No submitted completion fence" }
        try {
            check(fence.awaitCompletion(Long.MAX_VALUE)) { "Timed out waiting for Vulkan work" }
        } finally {
            fence.close()
            completionFence = null
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
        val prog = Program(
            Identifier.fromNamespaceAndPath(LumaNames.SHADER_NAMESPACE, "${LumaNames.PROGRAM_PREFIX}${nextProgramId++}"),
            vertexSource,
            fragmentSource,
            layouts,
            this::schedulePostRecord
        )
        if (config.precompileDefaultPipeline) {
            prog.precompileDefaults(RenderSystem.getDevice(), pipelineGeneration)
        }
        return prog
    }

    override fun bindProgram(program: ProgramHandle) {}

    override fun createTexture(image: BufferedImage, mipmap: Boolean): TextureHandle {
        require(!mipmap) { "Automatic mipmap generation is not supported by the Vulkan backend" }
        return VulkanTexture.create(
            image,
            samplerCache.get(VulkanSamplerDescriptor.clamp(FilterMode.LINEAR)),
            textureUploads
        )
    }

    override fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage) {
        (texture as VulkanTexture).update(x, y, image)
    }

    override fun bindTexture(texture: TextureHandle, unit: Int) {
        require(unit in boundTextures.indices) {
            "Texture unit $unit is outside 0..${boundTextures.lastIndex}"
        }
        if (boundTextures[unit] === texture) return
        boundTextures[unit] = texture
        texturesDirty = true
    }

    override fun createVertexBuffer(sizeBytes: Long, usage: BufferUsage): VertexBufferHandle {
        require(sizeBytes > 0L) { "Buffer size must be positive" }
        return VulkanVertexBuffer(sizeBytes, usage)
    }

    override fun createIndexBuffer(sizeBytes: Long, indexType: IndexType, usage: BufferUsage): IndexBufferHandle {
        require(sizeBytes > 0L) { "Buffer size must be positive" }
        return VulkanIndexBuffer(sizeBytes, indexType, usage)
    }

    override fun updateVertexBuffer(buffer: VertexBufferHandle, offsetBytes: Long, data: ByteBuffer) {
        bufferUploads.add(buffer as VulkanVertexBuffer, offsetBytes, data)
    }

    override fun updateIndexBuffer(buffer: IndexBufferHandle, offsetBytes: Long, data: ByteBuffer) {
        bufferUploads.add(buffer as VulkanIndexBuffer, offsetBytes, data)
    }

    override fun bindVertexBuffer(binding: Int, buffer: VertexBufferHandle, offsetBytes: Long) {
        require(binding in boundVertexBuffers.indices) { "Vertex binding $binding is out of range" }
        require(offsetBytes >= 0L && offsetBytes < buffer.sizeBytes) { "Vertex buffer offset is out of bounds" }
        val vertexBuffer = buffer as VulkanVertexBuffer
        vertexBuffer.requireOpen()
        if (boundVertexBuffers[binding] === vertexBuffer && boundVertexOffsets[binding] == offsetBytes) return
        boundVertexBuffers[binding] = vertexBuffer
        boundVertexOffsets[binding] = offsetBytes
        vertexBindingsDirty = true
    }

    override fun bindIndexBuffer(buffer: IndexBufferHandle) {
        val indexBuffer = buffer as VulkanIndexBuffer
        indexBuffer.requireOpen()
        boundIndexBuffer = indexBuffer
    }

    private fun currentVertexBindings(): VertexBindingSnapshot {
        if (vertexBindingsDirty || currentVertexBindings == null) {
            val snapshot = if (vertexBindingSnapshotCount < vertexBindingSnapshots.size) {
                vertexBindingSnapshots[vertexBindingSnapshotCount]
            } else {
                VertexBindingSnapshot().also(vertexBindingSnapshots::add)
            }
            vertexBindingSnapshotCount++
            boundVertexBuffers.copyInto(snapshot.buffers)
            boundVertexOffsets.copyInto(snapshot.offsets)
            currentVertexBindings = snapshot
            vertexBindingsDirty = false
        }
        return currentVertexBindings!!
    }

    private fun resetVertexBindingSnapshots() {
        for (index in 0 until vertexBindingSnapshotCount) vertexBindingSnapshots[index].clear()
        vertexBindingSnapshotCount = 0
        currentVertexBindings = null
        vertexBindingsDirty = true
    }

    private fun currentTextures(): Array<TextureHandle?> {
        if (texturesDirty) {
            val snapshot = if (textureSnapshotCount < textureSnapshots.size) {
                textureSnapshots[textureSnapshotCount]
            } else {
                arrayOfNulls<TextureHandle>(DrawCall.TEXTURE_UNITS).also(textureSnapshots::add)
            }
            textureSnapshotCount++
            boundTextures.copyInto(snapshot)
            textureSnapshot = snapshot
            texturesDirty = false
        }
        return textureSnapshot
    }

    private fun resetTextureSnapshots() {
        for (index in 0 until textureSnapshotCount) {
            textureSnapshots[index].fill(null)
        }
        textureSnapshotCount = 0
        textureSnapshot = DrawCall.NO_TEXTURES
        texturesDirty = true
    }

    override fun createRenderTarget(
        width: Int,
        height: Int,
        useDepth: Boolean,
        format: RenderTargetFormat
    ): RenderTargetHandle = createRenderTarget(width, height, useDepth, format, RenderTargetFilter.LINEAR)

    override fun createRenderTarget(
        width: Int,
        height: Int,
        useDepth: Boolean,
        format: RenderTargetFormat,
        filter: RenderTargetFilter
    ): RenderTargetHandle {
        val gpuFilter = when (filter) {
            RenderTargetFilter.NEAREST -> FilterMode.NEAREST
            RenderTargetFilter.LINEAR -> FilterMode.LINEAR
        }
        return VulkanRenderTarget.create(
            width,
            height,
            useDepth,
            format,
            samplerCache.get(VulkanSamplerDescriptor.clamp(gpuFilter)),
            textureUploads
        )
    }

    override fun beginRenderTarget(target: RenderTargetHandle, clearColor: FloatArray?) {
        val vulkanTarget = target as VulkanRenderTarget
        targetStack.add(vulkanTarget)
        targetClearColors.add(null)
        targetPassId++
        if (clearColor != null) {
            require(clearColor.size >= 4) { "Clear color must contain four components" }
            recordClear(
                color = true,
                red = clearColor[0],
                green = clearColor[1],
                blue = clearColor[2],
                alpha = clearColor[3],
                depth = vulkanTarget.gpuDepthTexture != null,
                depthValue = 1.0
            )
        }
    }

    override fun endRenderTarget() {
        check(targetStack.size > 1) { "No render target to end" }
        targetStack.removeAt(targetStack.size - 1)
        targetClearColors.removeAt(targetClearColors.size - 1)
        targetPassId++
    }

    override fun blend(enabled: Boolean) = renderState.blend(enabled)

    override fun blendFunction(function: BlendFunction) =
        renderState.blendFunction(function)

    override fun depthTest(enabled: Boolean) = renderState.depthTest(enabled)

    override fun depthWrite(enabled: Boolean) = renderState.depthWrite(enabled)

    override fun depthCompare(compare: DepthCompare) = renderState.depthCompare(compareOp(compare))

    override fun cull(enabled: Boolean) = renderState.cull(enabled)

    override fun scissor(x: Int, y: Int, width: Int, height: Int) =
        renderState.scissor(x, y, width, height)

    override fun disableScissor() = renderState.disableScissor()

    override fun clearColor(red: Float, green: Float, blue: Float, alpha: Float) {
        recordClear(true, red, green, blue, alpha, false, 1.0)
    }

    override fun clearDepth(depth: Double) {
        require(depth in 0.0..1.0) { "Depth clear value must be in 0..1" }
        recordClear(false, 0f, 0f, 0f, 0f, true, depth)
    }

    private fun recordClear(
        color: Boolean,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        depth: Boolean,
        depthValue: Double
    ) {
        check(targetStack.isNotEmpty()) { "Cannot clear outside a frame" }
        val draw = recorder.obtain()
        draw.target = targetStack.last()
        draw.targetPassId = targetPassId
        draw.clearColorEnabled = color
        draw.clearDepthEnabled = depth
        draw.clearRed = red
        draw.clearGreen = green
        draw.clearBlue = blue
        draw.clearAlpha = alpha
        draw.clearDepth = depthValue
    }

    override fun invalidatePipelineCache() {
        pipelineGeneration++
    }

    override fun draw(
        program: ProgramHandle,
        vertices: FloatBuffer,
        vertexCount: Int,
        uniforms: ShaderUniforms,
        primitiveType: PrimitiveType
    ) {
        val prog = program as Program
        prog.requireOpen()
        val vertexBytes = vertexCount * prog.layout.strideFloats * Float.SIZE_BYTES
        val vertexOffset = vertexStaging.appendFromAddress(MemoryUtil.memAddress(vertices), vertexBytes)

        val draw = recordDraw(prog, uniforms, primitiveType)
        draw.vertexOffset = vertexOffset
        draw.vertexBytes = vertexBytes.toLong()
        draw.vertexCount = vertexCount
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
        val draw = recordDraw(program as Program, uniforms, primitiveType)
        draw.vertexBindings = currentVertexBindings()
        draw.firstVertex = firstVertex
        draw.vertexCount = vertexCount
        draw.instanceCount = instanceCount
        draw.firstInstance = firstInstance
        draw.direct = true
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
        val draw = recordDraw(program as Program, uniforms, primitiveType)
        draw.vertexBindings = currentVertexBindings()
        draw.indexBuffer = requireNotNull(boundIndexBuffer) { "Index buffer is not bound" }
        draw.firstIndex = firstIndex
        draw.vertexCount = indexCount
        draw.baseVertex = vertexOffset
        draw.instanceCount = instanceCount
        draw.firstInstance = firstInstance
        draw.indexed = true
        draw.direct = true
    }

    private fun recordDraw(prog: Program, uniforms: ShaderUniforms, primitiveType: PrimitiveType): DrawCall {
        prog.requireOpen()

        val uboOffset: Long
        val uboBytes: Int
        val vulkanUniforms = prog.getVulkanUniforms(uniforms)
        if (vulkanUniforms.isEmpty()) {
            uboOffset = 0L
            uboBytes = 0
        } else {
            var anyDirty = false
            for (i in vulkanUniforms.indices) {
                if (vulkanUniforms[i].isHandleDirty(uniforms)) {
                    anyDirty = true
                    break
                }
            }

            if (!anyDirty && prog.uboCacheFrameId == frameId) {
                uboOffset = prog.uboCacheOffset
                uboBytes = prog.uboCacheBytes
            } else {
                val stack = MemoryStack.stackPush()
                try {
                    val builder = Std140Builder.onStack(stack, config.uniformScratchBytes)
                    for (i in vulkanUniforms.indices) {
                        vulkanUniforms[i].write(builder, uniforms)
                    }
                    val uboData = builder.get()
                    uboBytes = uboData.remaining()
                    uboOffset = uboStaging.appendAligned(uboData, uniformOffsetAlignment)
                } finally {
                    stack.pop()
                }
                for (i in vulkanUniforms.indices) {
                    vulkanUniforms[i].clearDirty(uniforms)
                }
                prog.uboCacheOffset = uboOffset
                prog.uboCacheBytes = uboBytes
                prog.uboCacheFrameId = frameId
            }
        }

        val activeTargetIndex = targetStack.lastIndex
        val draw = recorder.obtain()
        draw.program = prog
        draw.uboOffset = uboOffset
        draw.uboBytes = uboBytes.toLong()
        draw.textures = currentTextures()
        draw.primitiveType = primitiveType
        draw.blendEnabled = renderState.blendEnabled
        draw.blendFunction = renderState.blendFunction
        draw.depthEnabled = renderState.depthEnabled
        draw.depthWrite = draw.depthEnabled && renderState.depthWrite
        draw.depthFunc = if (draw.depthEnabled) renderState.depthFunc else CompareOp.ALWAYS_PASS
        draw.cullEnabled = renderState.cullEnabled
        draw.scissorEnabled = renderState.scissorEnabled
        draw.scissorX = renderState.scissorX
        draw.scissorY = renderState.scissorY
        draw.scissorWidth = renderState.scissorWidth
        draw.scissorHeight = renderState.scissorHeight
        draw.target = targetStack[activeTargetIndex]
        draw.targetPassId = targetPassId
        draw.clearColor = targetClearColors[activeTargetIndex]

        targetClearColors[activeTargetIndex] = null
        return draw
    }

    private fun compareOp(compare: DepthCompare): CompareOp = when (compare) {
        DepthCompare.ALWAYS -> CompareOp.ALWAYS_PASS
        DepthCompare.LESS -> CompareOp.LESS_THAN
        DepthCompare.LESS_OR_EQUAL -> CompareOp.LESS_THAN_OR_EQUAL
        DepthCompare.EQUAL -> CompareOp.EQUAL
        DepthCompare.NOT_EQUAL -> CompareOp.NOT_EQUAL
        DepthCompare.GREATER_OR_EQUAL -> CompareOp.GREATER_THAN_OR_EQUAL
        DepthCompare.GREATER -> CompareOp.GREATER_THAN
        DepthCompare.NEVER -> CompareOp.NEVER_PASS
    }

    override fun close() {
        completionFence?.let {
            try {
                it.awaitCompletion(Long.MAX_VALUE)
            } finally {
                it.close()
                completionFence = null
            }
        }
        completionFenceRequested = false
        textureUploads.close()
        bufferUploads.close()
        vertexStaging.close()
        uboStaging.close()
        VulkanResourceRetirement.closeAll(RenderSystem.getDevice()::createCommandEncoder)
        runPostRecordActions()
        vertexBuffer.close()
        uboBuffer.close()
        samplerCache.close()
        VulkanResourceRetirement.closeAll(RenderSystem.getDevice()::createCommandEncoder)
    }

    private fun schedulePostRecord(action: () -> Unit) {
        postRecordActions.add(action)
    }

    private fun runPostRecordActions() {
        for (index in postRecordActions.indices) postRecordActions[index]()
        postRecordActions.clear()
    }
}
