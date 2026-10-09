package sweetie.evaware.luma.backend.blaze3d.preset

import java.nio.ByteBuffer
import sweetie.evaware.luma.api.BlendFunction
import sweetie.evaware.luma.api.DepthCompare
import sweetie.evaware.luma.api.IndexType
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.vertex.VertexInputLayout

enum class PresetFormat {
    R8_UNORM,
    R8_SNORM,
    RG8_UNORM,
    RG8_SNORM,
    RGB8_UNORM,
    RGB8_SNORM,
    RGBA8_UNORM,
    RGBA8_SNORM,
    R16_UNORM,
    R16_SNORM,
    RG16_UNORM,
    RG16_SNORM,
    RGB16_UNORM,
    RGB16_SNORM,
    RGBA16_UNORM,
    RGBA16_SNORM,
    R8_UINT,
    R8_SINT,
    RG8_UINT,
    RG8_SINT,
    RGB8_UINT,
    RGB8_SINT,
    RGBA8_UINT,
    RGBA8_SINT,
    R16_UINT,
    R16_SINT,
    RG16_UINT,
    RG16_SINT,
    RGB16_UINT,
    RGB16_SINT,
    RGBA16_UINT,
    RGBA16_SINT,
    R32_UINT,
    R32_SINT,
    RG32_UINT,
    RG32_SINT,
    RGB32_UINT,
    RGB32_SINT,
    RGBA32_UINT,
    RGBA32_SINT,
    R16_FLOAT,
    RG16_FLOAT,
    RGB16_FLOAT,
    RGBA16_FLOAT,
    R32_FLOAT,
    RG32_FLOAT,
    RGB32_FLOAT,
    RGBA32_FLOAT,
    RGB10A2_UNORM,
    RGB10A2_UINT,
    RG11B10_FLOAT,
    D32_FLOAT,
    D32_FLOAT_S8_UINT,
    D24_UNORM_S8_UINT,
    D16_UNORM,
    S8_UINT
}

enum class PresetAddressMode {
    CLAMP_TO_EDGE,
    REPEAT,
    MIRRORED_REPEAT,
    CLAMP_TO_BORDER,
    MIRROR_CLAMP_TO_EDGE
}

enum class PresetFilterMode {
    NEAREST,
    LINEAR,
    NEAREST_MIPMAP_NEAREST,
    LINEAR_MIPMAP_NEAREST,
    NEAREST_MIPMAP_LINEAR,
    LINEAR_MIPMAP_LINEAR
}

data class PresetPipelineDescriptor(
    val id: Any,
    val topology: PrimitiveType,
    val blendEnabled: Boolean,
    val blendFunction: BlendFunction,
    val depthEnabled: Boolean,
    val depthWrite: Boolean,
    val depthFunc: DepthCompare,
    val cullEnabled: Boolean,
    val hasDepthAttachment: Boolean,
    val layouts: VertexInputLayout,
    val attributeNames: Map<Int, String>,
    val samplers: List<String>,
    val hasUniforms: Boolean
)

data class PresetCompilationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

interface PresetGpuBuffer : AutoCloseable {
    val sizeBytes: Long
    fun slice(offset: Long, length: Long): PresetGpuBufferSlice
    fun slice(): PresetGpuBufferSlice
}

interface PresetGpuBufferSlice {
    val offset: Long
    val length: Long
}

interface PresetGpuTexture : AutoCloseable {
    val width: Int
    val height: Int
    fun <T> unwrap(): T = @Suppress("UNCHECKED_CAST") (this as T)
}

interface PresetGpuTextureView : AutoCloseable {
    val texture: PresetGpuTexture
    fun getWidth(mip: Int): Int
    fun getHeight(mip: Int): Int
    fun <T> unwrap(): T = @Suppress("UNCHECKED_CAST") (this as T)
}

interface PresetGpuSampler : AutoCloseable {
    fun <T> unwrap(): T = @Suppress("UNCHECKED_CAST") (this as T)
}

interface PresetPipeline : AutoCloseable

interface PresetFence : AutoCloseable {
    fun awaitCompletion(timeoutNs: Long): Boolean
}

interface PresetSequentialBuffer {
    val buffer: PresetGpuBuffer
    val indexCount: Int
}

interface PresetCommandEncoder {
    fun writeToBuffer(slice: PresetGpuBufferSlice, data: ByteBuffer)
    fun writeToTexture(texture: PresetGpuTexture, data: ByteBuffer, mip: Int, depth: Int, x: Int, y: Int, width: Int, height: Int)
    fun clearColorTexture(texture: PresetGpuTexture, red: Float, green: Float, blue: Float, alpha: Float)
    fun clearDepthTexture(texture: PresetGpuTexture, depth: Double)
    fun clearColorAndDepthTextures(
        color: PresetGpuTexture,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        depth: PresetGpuTexture,
        depthValue: Double
    )
    fun createRenderPass(label: () -> String, colorView: PresetGpuTextureView, depthView: PresetGpuTextureView?): PresetRenderPass
    fun createFence(): PresetFence
    fun submit()
}

interface PresetRenderPass : AutoCloseable {
    fun setPipeline(pipeline: PresetPipeline)
    fun setUniform(name: String, slice: PresetGpuBufferSlice)
    fun bindTexture(name: String, view: PresetGpuTextureView, sampler: PresetGpuSampler)
    fun enableScissor(x: Int, y: Int, width: Int, height: Int)
    fun disableScissor()
    fun setVertexBuffer(slot: Int, slice: PresetGpuBufferSlice)
    fun setIndexBuffer(buffer: PresetGpuBuffer, indexType: IndexType)
    fun setSequentialIndexBuffer(buffer: PresetSequentialBuffer)
    fun draw(vertexCount: Int, instanceCount: Int, firstVertex: Int, firstInstance: Int)
    fun drawIndexed(indexCount: Int, instanceCount: Int, firstIndex: Int, baseVertex: Int, firstInstance: Int)
}

interface VulkanPreset {
    val name: String
    val minUniformOffsetAlignment: Int
    fun createBuffer(label: () -> String, usage: Int, sizeBytes: Long): PresetGpuBuffer
    fun createTexture(label: () -> String, usage: Int, format: PresetFormat, width: Int, height: Int): PresetGpuTexture
    fun createTextureView(texture: PresetGpuTexture): PresetGpuTextureView
    fun createTextureView(texture: PresetGpuTexture, baseMip: Int, mipCount: Int): PresetGpuTextureView
    fun createSampler(addressMode: PresetAddressMode, filterMode: PresetFilterMode, maxAnisotropy: Int): PresetGpuSampler
    fun createPipeline(descriptor: PresetPipelineDescriptor): PresetPipeline
    fun precompilePipeline(pipeline: PresetPipeline, vertexSource: String, fragmentSource: String): PresetCompilationResult
    fun createCommandEncoder(): PresetCommandEncoder
    fun bindDefaultUniforms(pass: PresetRenderPass)
    fun getSequentialQuadBuffer(indexCount: Int): PresetSequentialBuffer
    fun getLastDebugMessages(): List<String>
    fun wrapTextureView(rawView: Any): PresetGpuTextureView
}
