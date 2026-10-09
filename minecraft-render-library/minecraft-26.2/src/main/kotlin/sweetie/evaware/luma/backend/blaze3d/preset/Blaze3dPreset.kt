package sweetie.evaware.luma.backend.blaze3d.preset

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.buffers.GpuFence
import com.mojang.blaze3d.pipeline.BindGroupLayout
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.shaders.ShaderSource
import com.mojang.blaze3d.shaders.ShaderType
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.systems.CommandEncoder
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.AddressMode
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.VertexFormat
import java.nio.ByteBuffer
import java.util.Optional
import java.util.OptionalDouble
import net.minecraft.resources.Identifier
import org.joml.Vector4f
import sweetie.evaware.luma.LumaNames
import sweetie.evaware.luma.api.BlendFactor
import sweetie.evaware.luma.api.BlendFunction
import sweetie.evaware.luma.api.BlendOp
import sweetie.evaware.luma.api.DepthCompare
import sweetie.evaware.luma.api.IndexType
import sweetie.evaware.luma.api.PrimitiveType

open class Blaze3dPreset : VulkanPreset {
    override val name: String get() = "Minecraft 26.2 Blaze3D"

    override val minUniformOffsetAlignment: Int
        get() = RenderSystem.getDevice().deviceInfo.limits.minUniformOffsetAlignment

    override fun createBuffer(label: () -> String, usage: Int, sizeBytes: Long): PresetGpuBuffer {
        val buf = RenderSystem.getDevice().createBuffer(label, usage, sizeBytes)
        return BufferWrapper(buf, sizeBytes)
    }

    override fun createTexture(label: () -> String, usage: Int, format: PresetFormat, width: Int, height: Int): PresetGpuTexture {
        val tex = RenderSystem.getDevice().createTexture(
            label,
            usage,
            format.toBlaze3d(),
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

    override fun createSampler(addressMode: PresetAddressMode, filterMode: PresetFilterMode, maxAnisotropy: Int): PresetGpuSampler {
        val addr = addressMode.toBlaze3d()
        val filter = filterMode.toBlaze3d()
        val sampler = RenderSystem.getDevice().createSampler(
            addr,
            addr,
            filter,
            filter,
            maxAnisotropy,
            OptionalDouble.empty()
        )
        return SamplerWrapper(sampler)
    }

    override fun createPipeline(descriptor: PresetPipelineDescriptor): PresetPipeline {
        val layoutBuilder = BindGroupLayout.builder()
        if (descriptor.hasUniforms) {
            layoutBuilder.withUniform(LumaNames.UNIFORMS_BLOCK, UniformType.UNIFORM_BUFFER)
        }
        for (sampler in descriptor.samplers) {
            layoutBuilder.withSampler(sampler)
        }
        val bindGroupLayout = layoutBuilder.build()

        val depthState = DepthStencilState(
            if (descriptor.depthEnabled) descriptor.depthFunc.toBlaze3d() else CompareOp.ALWAYS_PASS,
            descriptor.depthWrite
        )

        val id = toIdentifier(descriptor.id)
        val pipelineBuilder = RenderPipeline.builder()
            .withLocation(id)
            .withVertexShader(id)
            .withFragmentShader(id)
            .withBindGroupLayout(bindGroupLayout)
            .withCull(descriptor.cullEnabled)
            .withDepthStencilState(
                if (descriptor.hasDepthAttachment) Optional.of(depthState) else Optional.empty()
            )
            .withColorTargetState(
                if (descriptor.blendEnabled) ColorTargetState(descriptor.blendFunction.toBlaze3d()) else ColorTargetState.DEFAULT
            )
            .withPrimitiveTopology(descriptor.topology.toBlaze3d())

        for (binding in 0 until descriptor.layouts.size()) {
            pipelineBuilder.withVertexBinding(
                binding,
                convertLayout(descriptor.layouts.layout(binding), descriptor.attributeNames, descriptor.layouts.stepRate(binding))
            )
        }

        return PipelineWrapper(pipelineBuilder.build())
    }

    override fun precompilePipeline(pipeline: PresetPipeline, vertexSource: String, fragmentSource: String): PresetCompilationResult {
        val raw = (pipeline as PipelineWrapper).handle
        val source = object : ShaderSource {
            override fun get(id: Identifier, type: ShaderType): String {
                return if (type == ShaderType.VERTEX) vertexSource else fragmentSource
            }
        }
        val result = RenderSystem.getDevice().precompilePipeline(raw, source)
        return PresetCompilationResult(result.isValid)
    }

    override fun createCommandEncoder(): PresetCommandEncoder {
        return CommandEncoderWrapper(RenderSystem.getDevice().createCommandEncoder())
    }

    override fun bindDefaultUniforms(pass: PresetRenderPass) {
        val rawPass = (pass as RenderPassWrapper).handle
        RenderSystem.bindDefaultUniforms(rawPass)
    }

    override fun getSequentialQuadBuffer(indexCount: Int): PresetSequentialBuffer {
        val seq = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)
        val buf = seq.getBuffer(indexCount)
        return SequentialBufferWrapper(BufferWrapper(buf, indexCount.toLong() * 2), indexCount)
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
        override fun getWidth(mip: Int): Int = handle.getWidth(mip)
        override fun getHeight(mip: Int): Int = handle.getHeight(mip)
        override fun <T> unwrap(): T = @Suppress("UNCHECKED_CAST") (handle as T)
        override fun close() = handle.close()
    }

    private class SamplerWrapper(val handle: GpuSampler) : PresetGpuSampler {
        override fun <T> unwrap(): T = @Suppress("UNCHECKED_CAST") (handle as T)
        override fun close() = handle.close()
    }

    private class PipelineWrapper(val handle: RenderPipeline) : PresetPipeline {
        override fun close() {}
    }

    private class FenceWrapper(val handle: GpuFence) : PresetFence {
        override fun awaitCompletion(timeoutNs: Long): Boolean = handle.awaitCompletion(timeoutNs)
        override fun close() = handle.close()
    }

    private class SequentialBufferWrapper(override val buffer: PresetGpuBuffer, override val indexCount: Int) : PresetSequentialBuffer

    private class CommandEncoderWrapper(val handle: CommandEncoder) : PresetCommandEncoder {
        private val tempColor = Vector4f()

        override fun writeToBuffer(slice: PresetGpuBufferSlice, data: ByteBuffer) {
            handle.writeToBuffer((slice as SliceWrapper).handle, data)
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
            handle.writeToTexture((texture as TextureWrapper).handle, data, mip, depth, x, y, width, height)
        }

        override fun clearColorTexture(texture: PresetGpuTexture, red: Float, green: Float, blue: Float, alpha: Float) {
            tempColor.set(red, green, blue, alpha)
            handle.clearColorTexture((texture as TextureWrapper).handle, tempColor)
        }

        override fun clearDepthTexture(texture: PresetGpuTexture, depth: Double) {
            handle.clearDepthTexture((texture as TextureWrapper).handle, depth)
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
            handle.clearColorAndDepthTextures((color as TextureWrapper).handle, tempColor, (depth as TextureWrapper).handle, depthValue)
        }

        override fun createRenderPass(label: () -> String, colorView: PresetGpuTextureView, depthView: PresetGpuTextureView?): PresetRenderPass {
            val cv = (colorView as TextureViewWrapper).handle
            val dv = (depthView as? TextureViewWrapper)?.handle
            val pass = handle.createRenderPass(label, cv, Optional.empty(), dv, OptionalDouble.empty())
            return RenderPassWrapper(pass)
        }

        override fun createFence(): PresetFence = FenceWrapper(handle.createFence())

        override fun submit() = handle.submit()
    }

    private class RenderPassWrapper(val handle: RenderPass) : PresetRenderPass {
        override fun setPipeline(pipeline: PresetPipeline) {
            handle.setPipeline((pipeline as PipelineWrapper).handle)
        }

        override fun setUniform(name: String, slice: PresetGpuBufferSlice) {
            handle.setUniform(name, (slice as SliceWrapper).handle)
        }

        override fun bindTexture(name: String, view: PresetGpuTextureView, sampler: PresetGpuSampler) {
            handle.bindTexture(name, (view as TextureViewWrapper).handle, (sampler as SamplerWrapper).handle)
        }

        override fun enableScissor(x: Int, y: Int, width: Int, height: Int) {
            handle.enableScissor(x, y, width, height)
        }

        override fun disableScissor() {
            handle.disableScissor()
        }

        override fun setVertexBuffer(slot: Int, slice: PresetGpuBufferSlice) {
            handle.setVertexBuffer(slot, (slice as SliceWrapper).handle)
        }

        override fun setIndexBuffer(buffer: PresetGpuBuffer, indexType: IndexType) {
            handle.setIndexBuffer(
                (buffer as BufferWrapper).handle,
                if (indexType == IndexType.UINT16) com.mojang.blaze3d.IndexType.SHORT else com.mojang.blaze3d.IndexType.INT
            )
        }

        override fun setSequentialIndexBuffer(buffer: PresetSequentialBuffer) {
            val seq = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)
            handle.setIndexBuffer((buffer.buffer as BufferWrapper).handle, seq.type())
        }

        override fun draw(vertexCount: Int, instanceCount: Int, firstVertex: Int, firstInstance: Int) {
            handle.draw(vertexCount, instanceCount, firstVertex, firstInstance)
        }

        override fun drawIndexed(indexCount: Int, instanceCount: Int, firstIndex: Int, baseVertex: Int, firstInstance: Int) {
            handle.drawIndexed(indexCount, instanceCount, firstIndex, baseVertex, firstInstance)
        }

        override fun close() = handle.close()
    }

    private fun PresetFormat.toBlaze3d(): GpuFormat = GpuFormat.valueOf(name)

    private fun PresetAddressMode.toBlaze3d(): AddressMode = when (this) {
        PresetAddressMode.CLAMP_TO_EDGE, PresetAddressMode.CLAMP_TO_BORDER, PresetAddressMode.MIRROR_CLAMP_TO_EDGE -> AddressMode.CLAMP_TO_EDGE
        PresetAddressMode.REPEAT, PresetAddressMode.MIRRORED_REPEAT -> AddressMode.REPEAT
    }

    private fun PresetFilterMode.toBlaze3d(): FilterMode = when (this) {
        PresetFilterMode.NEAREST, PresetFilterMode.NEAREST_MIPMAP_NEAREST, PresetFilterMode.NEAREST_MIPMAP_LINEAR -> FilterMode.NEAREST
        PresetFilterMode.LINEAR, PresetFilterMode.LINEAR_MIPMAP_NEAREST, PresetFilterMode.LINEAR_MIPMAP_LINEAR -> FilterMode.LINEAR
    }

    private fun PrimitiveType.toBlaze3d() = when (this) {
        PrimitiveType.TRIANGLES -> PrimitiveTopology.TRIANGLES
        PrimitiveType.LINES -> PrimitiveTopology.LINES
        PrimitiveType.QUADS -> PrimitiveTopology.QUADS
    }

    private fun DepthCompare.toBlaze3d(): CompareOp = when (this) {
        DepthCompare.ALWAYS -> CompareOp.ALWAYS_PASS
        DepthCompare.LESS -> CompareOp.LESS_THAN
        DepthCompare.LESS_OR_EQUAL -> CompareOp.LESS_THAN_OR_EQUAL
        DepthCompare.EQUAL -> CompareOp.EQUAL
        DepthCompare.NOT_EQUAL -> CompareOp.NOT_EQUAL
        DepthCompare.GREATER_OR_EQUAL -> CompareOp.GREATER_THAN_OR_EQUAL
        DepthCompare.GREATER -> CompareOp.GREATER_THAN
        DepthCompare.NEVER -> CompareOp.NEVER_PASS
    }

    private fun BlendFunction.toBlaze3d() = com.mojang.blaze3d.pipeline.BlendFunction(
        sourceColor.toBlaze3d(),
        destinationColor.toBlaze3d(),
        colorOp.toBlaze3d(),
        sourceAlpha.toBlaze3d(),
        destinationAlpha.toBlaze3d(),
        alphaOp.toBlaze3d()
    )

    private fun BlendFactor.toBlaze3d() = com.mojang.blaze3d.platform.BlendFactor.valueOf(name)
    private fun BlendOp.toBlaze3d() = com.mojang.blaze3d.platform.BlendOp.valueOf(name)

    private fun toIdentifier(raw: Any): Identifier = when (raw) {
        is Identifier -> raw
        else -> Identifier.parse(raw.toString())
    }
}
