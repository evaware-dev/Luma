package sweetie.evaware.luma.backend.blaze3d.preset

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.commands.CommandEncoder
import com.mojang.renderpearl.api.commands.GpuFence
import com.mojang.renderpearl.api.commands.GpuQueryPool
import com.mojang.renderpearl.api.commands.RenderPass
import com.mojang.renderpearl.api.commands.RenderPassDescriptor
import com.mojang.renderpearl.api.pipeline.*
import com.mojang.renderpearl.api.textures.AddressMode
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuSampler
import com.mojang.renderpearl.api.textures.GpuTexture
import com.mojang.renderpearl.api.textures.GpuTextureView
import com.mojang.renderpearl.api.vertex.VertexFormat
import java.nio.ByteBuffer
import java.util.Optional
import java.util.OptionalDouble
import java.util.function.Supplier
import net.minecraft.resources.Identifier
import org.joml.Vector4f
import sweetie.evaware.luma.LumaNames
import sweetie.evaware.luma.api.BlendFactor
import sweetie.evaware.luma.api.BlendFunction
import sweetie.evaware.luma.api.BlendOp
import sweetie.evaware.luma.api.DepthCompare
import sweetie.evaware.luma.api.IndexType
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.vertex.VertexLayout

open class RenderpearlPreset : VulkanPreset {
    private val reusableEncoder = CommandEncoderWrapper()
    private val reusableSequentialBuffer = SequentialBufferWrapper()

    override val name: String get() = "Minecraft 26.3 Renderpearl"

    override val minUniformOffsetAlignment: Int
        get() = RenderSystem.getDevice().deviceInfo.limits().minUniformOffsetAlignment()

    override val supportsTimestamps: Boolean
        get() = RenderSystem.getDevice().deviceInfo.timestampPeriod() > 0f

    override val timestampPeriodNanos: Double
        get() = RenderSystem.getDevice().deviceInfo.timestampPeriod().toDouble()

    override fun createTimestampQueryPool(size: Int): PresetTimestampQueryPool {
        return QueryPoolWrapper(RenderSystem.getDevice().createTimestampQueryPool(size))
    }

    override fun createBuffer(label: () -> String, usage: Int, sizeBytes: Long): PresetGpuBuffer {
        val buf = RenderSystem.getDevice().createBuffer(label, usage, sizeBytes)
        return BufferWrapper(buf, sizeBytes)
    }

    override fun createTexture(label: () -> String, usage: Int, format: PresetFormat, width: Int, height: Int): PresetGpuTexture {
        val tex = RenderSystem.getDevice().createTexture(
            label,
            usage,
            format.toRenderpearl(),
            width,
            height,
            1,
            1
        )
        return TextureWrapper(tex, width, height)
    }

    override fun createTextureView(texture: PresetGpuTexture): PresetGpuTextureView {
        val tex = (texture as TextureWrapper).handle
        val view = RenderSystem.getDevice().createTextureView(tex)
        return TextureViewWrapper(texture, view)
    }

    override fun createTextureView(texture: PresetGpuTexture, baseMip: Int, mipCount: Int): PresetGpuTextureView {
        val tex = (texture as TextureWrapper).handle
        val view = RenderSystem.getDevice().createTextureView(tex, baseMip, mipCount)
        return TextureViewWrapper(texture, view)
    }

    override fun createSampler(
        addressU: PresetAddressMode,
        addressV: PresetAddressMode,
        minFilter: PresetFilterMode,
        magFilter: PresetFilterMode,
        maxAnisotropy: Int,
        maxLod: Double?
    ): PresetGpuSampler {
        val device = RenderSystem.getDevice()
        val clampedAnisotropy = maxAnisotropy.coerceIn(1, device.deviceInfo.limits().maxAnisotropy())
        val sampler = device.createSampler(
            addressU.toRenderpearl(),
            addressV.toRenderpearl(),
            minFilter.toRenderpearl(),
            magFilter.toRenderpearl(),
            clampedAnisotropy,
            if (maxLod != null) OptionalDouble.of(maxLod) else OptionalDouble.empty()
        )
        return SamplerWrapper(sampler)
    }

    override fun createPipeline(descriptor: PresetPipelineDescriptor): PresetPipeline {
        val layoutBuilder = BindGroupLayout.builder()
        if (descriptor.hasUniforms) {
            layoutBuilder.withUniform(LumaNames.UNIFORMS_BLOCK, UniformType.UNIFORM_BUFFER)
        }
        for (sampler in descriptor.samplers) {
            layoutBuilder.withUniform(sampler, UniformType.COMBINED_IMAGE_SAMPLER)
        }
        val bindGroupLayout = layoutBuilder.build()

        val depthState = DepthStencilState(
            if (descriptor.depthEnabled) descriptor.depthFunc.toRenderpearl() else CompareOp.ALWAYS_PASS,
            descriptor.depthWrite
        )

        val colorTargetState = if (!descriptor.blendEnabled && descriptor.colorWriteMask == ColorTargetState.WRITE_ALL) {
            ColorTargetState.DEFAULT
        } else {
            ColorTargetState(
                if (descriptor.blendEnabled) Optional.of(descriptor.blendFunction.toRenderpearl()) else Optional.empty(),
                GpuFormat.RGBA8_UNORM,
                descriptor.colorWriteMask
            )
        }

        val id = toIdentifier(descriptor.id)
        val pipelineBuilder = RenderPipeline.builder()
            .withLocation(id)
            .withVertexShader(id)
            .withFragmentShader(id)
            .withBindGroupLayout(bindGroupLayout)
            .withCull(descriptor.cullEnabled)
            .withColorTargetState(colorTargetState)
            .withPrimitiveTopology(descriptor.topology.toRenderpearl())

        if (descriptor.hasDepthAttachment) {
            pipelineBuilder.withDepthStencilState(depthState)
        }

        for (binding in 0 until descriptor.layouts.size()) {
            pipelineBuilder.withVertexBinding(
                binding,
                convertLayout(descriptor.layouts.layout(binding), descriptor.attributeNames, descriptor.layouts.stepRate(binding))
            )
        }

        return PipelineWrapper(pipelineBuilder.build())
    }

    override fun precompilePipeline(pipeline: PresetPipeline, vertexSource: String, fragmentSource: String): PresetCompilationResult {
        val wrapper = pipeline as PipelineWrapper
        val source = object : ShaderSource {
            override fun getShader(id: Identifier, type: ShaderType): String {
                return if (type == ShaderType.VERTEX) vertexSource else fragmentSource
            }
            override fun getInclude(id: Identifier): ShaderSource.CachedIncludeSource? = null
            override fun close() {}
        }
        val future = RenderSystem.getDevice().compilePipeline(wrapper.handle, source, Runnable::run)
        val compiled = future.join().finishCompile()
        wrapper.compiledPipeline = compiled
        return PresetCompilationResult(true)
    }

    override fun createCommandEncoder(): PresetCommandEncoder {
        return reusableEncoder.reset(RenderSystem.getDevice().createCommandEncoder())
    }

    override fun bindDefaultUniforms(pass: PresetRenderPass) {
        val rawPass = (pass as RenderPassWrapper).requireHandle()
        RenderSystem.bindDefaultUniforms(rawPass)
    }

    override fun getSequentialQuadBuffer(indexCount: Int): PresetSequentialBuffer {
        val seq = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)
        val buf = seq.getBuffer(indexCount)
        val type = seq.type()
        val byteSize = indexCount.toLong() * if (type == com.mojang.renderpearl.api.pipeline.IndexType.SHORT) 2L else 4L
        return reusableSequentialBuffer.update(buf, byteSize, indexCount, type)
    }

    override fun getLastDebugMessages(): List<String> {
        return RenderSystem.getDevice().lastDebugMessages
    }

    override fun wrapTextureView(rawView: Any): PresetGpuTextureView {
        if (rawView is PresetGpuTextureView) return rawView
        val view = rawView as GpuTextureView
        val tex = view.texture()
        return TextureViewWrapper(TextureWrapper(tex, view.getWidth(0), view.getHeight(0)), view)
    }

    private class BufferWrapper(val handle: GpuBuffer, override val sizeBytes: Long) : PresetGpuBuffer {
        override fun slice(offset: Long, length: Long): PresetGpuBufferSlice = SliceWrapper(handle.slice(offset, length), offset, length)
        override fun slice(): PresetGpuBufferSlice = SliceWrapper(handle.slice(), 0, sizeBytes)
        override fun close() = handle.close()
    }

    private class SliceWrapper(val handle: GpuBufferSlice, override val offset: Long, override val length: Long) : PresetGpuBufferSlice

    private class TextureWrapper(val handle: GpuTexture, override val width: Int, override val height: Int) : PresetGpuTexture {
        override fun <T> unwrap(): T = @Suppress("UNCHECKED_CAST") (handle as T)
        override fun close() = handle.close()
    }

    private class TextureViewWrapper(override val texture: PresetGpuTexture, val handle: GpuTextureView) : PresetGpuTextureView {
        private var cachedDescriptor: RenderPassDescriptor? = null
        private var cachedDepthView: GpuTextureView? = null
        private var cachedLabel: (() -> String)? = null

        fun renderPassDescriptor(label: () -> String, depthView: GpuTextureView?): RenderPassDescriptor {
            val existing = cachedDescriptor
            if (existing != null && cachedDepthView === depthView && cachedLabel === label) {
                return existing
            }
            val builder = RenderPassDescriptor.builder(Supplier(label))
                .withColorAttachment(handle, Optional.empty())
                .withRenderArea(RenderPass.RenderArea(0, 0, handle.getWidth(0), handle.getHeight(0)))
            if (depthView != null) {
                builder.withDepthAttachment(depthView, OptionalDouble.empty())
            }
            val descriptor = builder.build()
            cachedDescriptor = descriptor
            cachedDepthView = depthView
            cachedLabel = label
            return descriptor
        }

        override fun getWidth(mip: Int): Int = handle.getWidth(mip)
        override fun getHeight(mip: Int): Int = handle.getHeight(mip)
        override fun <T> unwrap(): T = @Suppress("UNCHECKED_CAST") (handle as T)
        override fun close() {
            cachedDescriptor = null
            cachedDepthView = null
            cachedLabel = null
            handle.close()
        }
    }

    private class SamplerWrapper(val handle: GpuSampler) : PresetGpuSampler {
        override fun <T> unwrap(): T = @Suppress("UNCHECKED_CAST") (handle as T)
        override fun close() = handle.close()
    }

    private class PipelineWrapper(val handle: RenderPipeline) : PresetPipeline {
        var compiledPipeline: CompiledRenderPipeline? = null
        override fun close() {}
    }

    private class FenceWrapper(val handle: GpuFence) : PresetFence {
        override fun awaitCompletion(timeoutNs: Long): Boolean = handle.awaitCompletion(timeoutNs)
        override fun close() = handle.close()
    }

    private class QueryPoolWrapper(val handle: GpuQueryPool) : PresetTimestampQueryPool {
        override val size: Int get() = handle.size()

        override fun tryReadValues(count: Int, outValues: LongArray): Boolean {
            if (count == 0) return true
            val values = handle.getValues(0, count)
            for (i in 0 until count) {
                if (!values[i].isPresent) return false
            }
            for (i in 0 until count) {
                outValues[i] = values[i].asLong
            }
            return true
        }

        override fun close() = handle.close()
    }

    private class SequentialBufferWrapper : PresetSequentialBuffer {
        private var cachedWrapper: BufferWrapper? = null
        var rawBuffer: GpuBuffer? = null
            private set
        var indexType: com.mojang.renderpearl.api.pipeline.IndexType? = null
            private set
        override var indexCount: Int = 0
            private set

        override val buffer: PresetGpuBuffer
            get() = requireNotNull(cachedWrapper) { "Sequential buffer is not initialized" }

        fun update(
            raw: GpuBuffer,
            sizeBytes: Long,
            count: Int,
            type: com.mojang.renderpearl.api.pipeline.IndexType
        ): SequentialBufferWrapper {
            if (rawBuffer !== raw || indexType != type) {
                cachedWrapper = BufferWrapper(raw, sizeBytes)
                rawBuffer = raw
                indexType = type
            }
            indexCount = count
            return this
        }
    }

    private class CommandEncoderWrapper : PresetCommandEncoder {
        private var handle: CommandEncoder? = null
        private val tempColor = Vector4f()
        private val reusablePass = RenderPassWrapper()

        fun reset(encoder: CommandEncoder): CommandEncoderWrapper {
            handle = encoder
            return this
        }

        private fun requireHandle(): CommandEncoder =
            checkNotNull(handle) { "Command encoder is not active" }

        override fun writeToBuffer(slice: PresetGpuBufferSlice, data: ByteBuffer) {
            requireHandle().writeToBuffer((slice as SliceWrapper).handle, data)
        }

        override fun writeToTexture(
            texture: PresetGpuTexture,
            data: ByteBuffer,
            mip: Int,
            depth: Int,
            x: Int,
            y: Int,
            width: Int,
            height: Int
        ) {
            requireHandle().writeToTexture((texture as TextureWrapper).handle, data, mip, depth, x, y, width, height)
        }

        override fun copyTextureToTexture(
            source: PresetGpuTexture,
            destination: PresetGpuTexture,
            mipLevel: Int,
            dstX: Int,
            dstY: Int,
            srcX: Int,
            srcY: Int,
            width: Int,
            height: Int
        ) {
            requireHandle().copyTextureToTexture(
                (source as TextureWrapper).handle,
                (destination as TextureWrapper).handle,
                mipLevel,
                dstX,
                dstY,
                srcX,
                srcY,
                width,
                height
            )
        }

        override fun clearColorTexture(texture: PresetGpuTexture, red: Float, green: Float, blue: Float, alpha: Float) {
            tempColor.set(red, green, blue, alpha)
            requireHandle().clearColorTexture((texture as TextureWrapper).handle, tempColor)
        }

        override fun clearDepthTexture(texture: PresetGpuTexture, depth: Double) {
            requireHandle().clearDepthTexture((texture as TextureWrapper).handle, depth)
        }

        override fun clearColorAndDepthTextures(
            color: PresetGpuTexture,
            red: Float,
            green: Float,
            blue: Float,
            alpha: Float,
            depth: PresetGpuTexture,
            depthValue: Double
        ) {
            tempColor.set(red, green, blue, alpha)
            requireHandle().clearColorAndDepthTextures((color as TextureWrapper).handle, tempColor, (depth as TextureWrapper).handle, depthValue)
        }

        override fun createRenderPass(label: () -> String, colorView: PresetGpuTextureView, depthView: PresetGpuTextureView?): PresetRenderPass {
            val colorWrapper = colorView as TextureViewWrapper
            val dv = (depthView as? TextureViewWrapper)?.handle
            val pass = requireHandle().createRenderPass(colorWrapper.renderPassDescriptor(label, dv))
            return if (reusablePass.inUse) RenderPassWrapper().reset(pass) else reusablePass.reset(pass)
        }

        override fun writeTimestamp(pool: PresetTimestampQueryPool, queryIndex: Int) {
            requireHandle().writeTimestamp((pool as QueryPoolWrapper).handle, queryIndex)
        }

        override fun createFence(): PresetFence = FenceWrapper(requireHandle().createFence())

        override fun submit() {
            val active = requireHandle()
            handle = null
            active.submit()
        }
    }

    private class RenderPassWrapper : PresetRenderPass {
        private var handle: RenderPass? = null

        val inUse: Boolean
            get() = handle != null

        fun reset(pass: RenderPass): RenderPassWrapper {
            handle = pass
            return this
        }

        fun requireHandle(): RenderPass =
            checkNotNull(handle) { "Render pass is closed" }

        override fun pushDebugGroup(label: String) {
            requireHandle().pushDebugGroup { label }
        }

        override fun popDebugGroup() {
            requireHandle().popDebugGroup()
        }

        override fun writeTimestamp(pool: PresetTimestampQueryPool, queryIndex: Int) {
            requireHandle().writeTimestamp((pool as QueryPoolWrapper).handle, queryIndex)
        }

        override fun setPipeline(pipeline: PresetPipeline) {
            val compiled = checkNotNull((pipeline as PipelineWrapper).compiledPipeline) { "Pipeline is not compiled" }
            requireHandle().setPipeline(compiled)
        }

        override fun setUniform(name: String, slice: PresetGpuBufferSlice) {
            requireHandle().setUniform(name, (slice as SliceWrapper).handle)
        }

        override fun bindTexture(name: String, view: PresetGpuTextureView, sampler: PresetGpuSampler) {
            requireHandle().setUniform(name, (view as TextureViewWrapper).handle, (sampler as SamplerWrapper).handle)
        }

        override fun enableScissor(x: Int, y: Int, width: Int, height: Int) {
            requireHandle().enableScissor(x, y, width, height)
        }

        override fun disableScissor() {
            requireHandle().disableScissor()
        }

        override fun setVertexBuffer(slot: Int, slice: PresetGpuBufferSlice) {
            requireHandle().setVertexBuffer(slot, (slice as SliceWrapper).handle)
        }

        override fun setIndexBuffer(buffer: PresetGpuBuffer, indexType: IndexType) {
            requireHandle().setIndexBuffer(
                (buffer as BufferWrapper).handle,
                if (indexType == IndexType.UINT16) com.mojang.renderpearl.api.pipeline.IndexType.SHORT else com.mojang.renderpearl.api.pipeline.IndexType.INT
            )
        }

        override fun setSequentialIndexBuffer(buffer: PresetSequentialBuffer) {
            val seq = buffer as SequentialBufferWrapper
            requireHandle().setIndexBuffer(
                requireNotNull(seq.rawBuffer),
                requireNotNull(seq.indexType)
            )
        }

        override fun draw(vertexCount: Int, instanceCount: Int, firstVertex: Int, firstInstance: Int) {
            requireHandle().draw(vertexCount, instanceCount, firstVertex, firstInstance)
        }

        override fun drawIndexed(indexCount: Int, instanceCount: Int, firstIndex: Int, baseVertex: Int, firstInstance: Int) {
            requireHandle().drawIndexed(indexCount, instanceCount, firstIndex, baseVertex, firstInstance)
        }

        override fun close() {
            val active = handle ?: return
            handle = null
            active.close()
        }
    }

    private fun convertLayout(layout: VertexLayout, attributeNames: Map<Int, String>, stepRate: Int = 0): VertexFormat {
        val builder = VertexFormat.builder(stepRate)
        for (i in 0 until layout.size()) {
            val layoutPos = layout.layoutPos(i)
            val name = attributeNames[layoutPos] ?: "a$layoutPos"
            val format = layout.type(i).toPresetFormat(layout.count(i), layout.normalized(i)).toRenderpearl()
            builder.addAttribute(name, format)
        }
        return builder.build()
    }

    private fun PresetFormat.toRenderpearl(): GpuFormat = GpuFormat.valueOf(name)

    private fun PresetAddressMode.toRenderpearl(): AddressMode = when (this) {
        PresetAddressMode.CLAMP_TO_EDGE, PresetAddressMode.CLAMP_TO_BORDER, PresetAddressMode.MIRROR_CLAMP_TO_EDGE -> AddressMode.CLAMP_TO_EDGE
        PresetAddressMode.REPEAT, PresetAddressMode.MIRRORED_REPEAT -> AddressMode.REPEAT
    }

    private fun PresetFilterMode.toRenderpearl(): FilterMode = when (this) {
        PresetFilterMode.NEAREST, PresetFilterMode.NEAREST_MIPMAP_NEAREST, PresetFilterMode.NEAREST_MIPMAP_LINEAR -> FilterMode.NEAREST
        PresetFilterMode.LINEAR, PresetFilterMode.LINEAR_MIPMAP_NEAREST, PresetFilterMode.LINEAR_MIPMAP_LINEAR -> FilterMode.LINEAR
    }

    private fun PrimitiveType.toRenderpearl() = when (this) {
        PrimitiveType.TRIANGLES -> PrimitiveTopology.TRIANGLES
        PrimitiveType.LINES -> PrimitiveTopology.LINES
        PrimitiveType.QUADS -> PrimitiveTopology.QUADS
    }

    private fun DepthCompare.toRenderpearl(): CompareOp = when (this) {
        DepthCompare.ALWAYS -> CompareOp.ALWAYS_PASS
        DepthCompare.LESS -> CompareOp.LESS_THAN
        DepthCompare.LESS_OR_EQUAL -> CompareOp.LESS_THAN_OR_EQUAL
        DepthCompare.EQUAL -> CompareOp.EQUAL
        DepthCompare.NOT_EQUAL -> CompareOp.NOT_EQUAL
        DepthCompare.GREATER_OR_EQUAL -> CompareOp.GREATER_THAN_OR_EQUAL
        DepthCompare.GREATER -> CompareOp.GREATER_THAN
        DepthCompare.NEVER -> CompareOp.NEVER_PASS
    }

    private fun BlendFunction.toRenderpearl() = com.mojang.renderpearl.api.pipeline.BlendFunction(
        sourceColor.toRenderpearl(),
        destinationColor.toRenderpearl(),
        colorOp.toRenderpearl(),
        sourceAlpha.toRenderpearl(),
        destinationAlpha.toRenderpearl(),
        alphaOp.toRenderpearl()
    )

    private fun BlendFactor.toRenderpearl() = com.mojang.renderpearl.api.pipeline.BlendFactor.valueOf(name)
    private fun BlendOp.toRenderpearl() = com.mojang.renderpearl.api.pipeline.BlendOp.valueOf(name)

    private fun toIdentifier(raw: Any): Identifier = when (raw) {
        is Identifier -> raw
        else -> Identifier.parse(raw.toString())
    }
}
