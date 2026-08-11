package sweetie.evaware.luma.backend.blaze3d

import sweetie.evaware.luma.LumaNames

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuFence
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.resources.Identifier
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
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

class Backend(
    private val config: VulkanBackendConfig = VulkanBackendConfig()
) : RenderBackend {
    private var nextProgramId = 1

    private var frameId = 0L

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

    private val recorder = DrawCallRecorder()
    private val merger = DrawCallMerger()
    private val passEncoder = RenderPassEncoder(vertexBuffer, uboBuffer)
    private val postRecordActions = ArrayList<() -> Unit>()
    private val samplerCache = VulkanSamplerCache(RenderSystem.getDevice())
    private val textureUploads = TextureUploadQueue(config.maxPooledUploadBytes)

    private val targetStack = ArrayList<VulkanRenderTarget?>()
    private val targetClearColors = ArrayList<FloatArray?>()
    private var completionFenceRequested = false
    private var completionFence: GpuFence? = null

    override fun beginFrame() {
        VulkanResourceRetirement.collectCompleted()
        RenderStateTracker.beginFrame()
        frameId++
        recorder.reset()
        resetTextureSnapshots()
        vertexStaging.reset()
        uboStaging.reset()

        targetStack.clear()
        targetClearColors.clear()
        targetStack.add(null)
        targetClearColors.add(null)
    }

    override fun endFrame() {
        if (
            recorder.size == 0 &&
            !textureUploads.hasPending() &&
            !VulkanResourceRetirement.hasPending() &&
            !completionFenceRequested
        ) {
            runPostRecordActions()
            return
        }

        val device = RenderSystem.getDevice()
        val encoder = device.createCommandEncoder()

        textureUploads.record(encoder)

        if (recorder.size > 0) {
            vertexStaging.flip()
            vertexBuffer.write(encoder, vertexStaging.buffer)

            uboStaging.flip()
            uboBuffer.write(encoder, uboStaging.buffer)

            val pass = passEncoder.begin(device, encoder)
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
    ): ProgramHandle {
        val prog = Program(
            Identifier.fromNamespaceAndPath(LumaNames.SHADER_NAMESPACE, "${LumaNames.PROGRAM_PREFIX}${nextProgramId++}"),
            vertexSource,
            fragmentSource,
            layout,
            this::schedulePostRecord
        )
        if (config.precompileDefaultPipeline) prog.precompileDefaults(RenderSystem.getDevice())
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
        targetStack.add(target as VulkanRenderTarget)
        targetClearColors.add(clearColor)
    }

    override fun endRenderTarget() {
        check(targetStack.size > 1) { "No render target to end" }
        targetStack.removeAt(targetStack.size - 1)
        targetClearColors.removeAt(targetClearColors.size - 1)
    }

    override fun depthTest(enabled: Boolean) = RenderStateTracker.depthTest(enabled)

    override fun cull(enabled: Boolean) = RenderStateTracker.cull(enabled)

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
                    uboOffset = uboStaging.append(uboData)
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
        draw.vertexOffset = vertexOffset
        draw.vertexBytes = vertexBytes.toLong()
        draw.vertexCount = vertexCount
        draw.uboOffset = uboOffset
        draw.uboBytes = uboBytes.toLong()
        draw.textures = currentTextures()
        draw.primitiveType = primitiveType
        draw.depthEnabled = RenderStateTracker.depthEnabled
        draw.depthWrite = draw.depthEnabled && RenderStateTracker.depthWrite
        draw.depthFunc = if (draw.depthEnabled) RenderStateTracker.depthFunc else CompareOp.ALWAYS_PASS
        draw.cullEnabled = RenderStateTracker.cullEnabled
        draw.target = targetStack[activeTargetIndex]
        draw.clearColor = targetClearColors[activeTargetIndex]

        targetClearColors[activeTargetIndex] = null
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
