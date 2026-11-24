package sweetie.evaware.luma.api

import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.shader.translator.ShaderReflection
import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.VertexInputLayout
import sweetie.evaware.luma.vertex.VertexLayout

open class LazyRenderBackend(
    private val factory: () -> RenderBackend
) : RenderBackend {
    @Volatile
    private var resolved: RenderBackend? = null

    val isInitialized: Boolean
        get() = resolved != null

    private fun resolve(): RenderBackend {
        resolved?.let { return it }
        return synchronized(this) {
            resolved ?: factory().also { created ->
                resolved = created
                Luma.replaceBackendIfSame(this, created)
            }
        }
    }

    override val supportsStencil: Boolean get() = resolve().supportsStencil
    override val supportsGpuTimestamps: Boolean get() = resolve().supportsGpuTimestamps
    override val isDepthTestEnabled: Boolean get() = resolved?.isDepthTestEnabled ?: false
    override val isDepthWriteEnabled: Boolean get() = resolved?.isDepthWriteEnabled ?: false
    override val currentDepthCompare: DepthCompare get() = resolved?.currentDepthCompare ?: DepthCompare.ALWAYS
    override val colorMaskRed: Boolean get() = resolved?.colorMaskRed ?: true
    override val colorMaskGreen: Boolean get() = resolved?.colorMaskGreen ?: true
    override val colorMaskBlue: Boolean get() = resolved?.colorMaskBlue ?: true
    override val colorMaskAlpha: Boolean get() = resolved?.colorMaskAlpha ?: true

    override fun beginFrame() = resolve().beginFrame()
    override fun endFrame() = resolve().endFrame()

    override fun createProgram(
        vertexSource: String,
        fragmentSource: String,
        layouts: VertexInputLayout,
        reflection: ShaderReflection?
    ): ProgramHandle = resolve().createProgram(vertexSource, fragmentSource, layouts, reflection)

    override fun createProgram(
        vertexSource: String,
        fragmentSource: String,
        layout: VertexLayout,
        reflection: ShaderReflection?
    ): ProgramHandle = resolve().createProgram(vertexSource, fragmentSource, layout, reflection)

    override fun bindProgram(program: ProgramHandle) = resolve().bindProgram(program)

    override fun createTexture(
        image: BufferedImage,
        mipmap: Boolean,
        format: TextureFormat,
        sampler: SamplerState
    ): TextureHandle = resolve().createTexture(image, mipmap, format, sampler)

    override fun createTexture(
        width: Int,
        height: Int,
        data: ByteBuffer?,
        format: TextureFormat,
        mipmap: Boolean,
        sampler: SamplerState
    ): TextureHandle = resolve().createTexture(width, height, data, format, mipmap, sampler)

    override fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage) =
        resolve().updateTexture(texture, x, y, image)

    override fun updateTexture(
        texture: TextureHandle,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        data: ByteBuffer
    ) = resolve().updateTexture(texture, x, y, width, height, data)

    override fun setTextureSampler(texture: TextureHandle, sampler: SamplerState) =
        resolve().setTextureSampler(texture, sampler)

    override fun generateMipmaps(texture: TextureHandle) = resolve().generateMipmaps(texture)

    override fun bindTexture(texture: TextureHandle, unit: Int) = resolve().bindTexture(texture, unit)

    override fun createVertexBuffer(sizeBytes: Long, usage: BufferUsage): VertexBufferHandle =
        resolve().createVertexBuffer(sizeBytes, usage)

    override fun createIndexBuffer(sizeBytes: Long, indexType: IndexType, usage: BufferUsage): IndexBufferHandle =
        resolve().createIndexBuffer(sizeBytes, indexType, usage)

    override fun updateVertexBuffer(buffer: VertexBufferHandle, offsetBytes: Long, data: ByteBuffer) =
        resolve().updateVertexBuffer(buffer, offsetBytes, data)

    override fun updateIndexBuffer(buffer: IndexBufferHandle, offsetBytes: Long, data: ByteBuffer) =
        resolve().updateIndexBuffer(buffer, offsetBytes, data)

    override fun bindVertexBuffer(binding: Int, buffer: VertexBufferHandle, offsetBytes: Long) =
        resolve().bindVertexBuffer(binding, buffer, offsetBytes)

    override fun bindIndexBuffer(buffer: IndexBufferHandle) = resolve().bindIndexBuffer(buffer)

    override fun draw(
        program: ProgramHandle,
        uniforms: ShaderUniforms,
        primitiveType: PrimitiveType,
        firstVertex: Int,
        vertexCount: Int,
        instanceCount: Int,
        firstInstance: Int
    ) = resolve().draw(program, uniforms, primitiveType, firstVertex, vertexCount, instanceCount, firstInstance)

    override fun drawIndexed(
        program: ProgramHandle,
        uniforms: ShaderUniforms,
        primitiveType: PrimitiveType,
        firstIndex: Int,
        indexCount: Int,
        vertexOffset: Int,
        instanceCount: Int,
        firstInstance: Int
    ) = resolve().drawIndexed(
        program, uniforms, primitiveType, firstIndex, indexCount, vertexOffset, instanceCount, firstInstance
    )

    override fun draw(
        program: ProgramHandle,
        vertices: FloatBuffer,
        vertexCount: Int,
        uniforms: ShaderUniforms,
        primitiveType: PrimitiveType
    ) = resolve().draw(program, vertices, vertexCount, uniforms, primitiveType)

    override fun createRenderTarget(
        width: Int,
        height: Int,
        useDepth: Boolean,
        format: RenderTargetFormat,
        sampler: SamplerState,
        useStencil: Boolean
    ): RenderTargetHandle = resolve().createRenderTarget(width, height, useDepth, format, sampler, useStencil)

    override fun beginRenderTarget(target: RenderTargetHandle, clearColor: FloatArray?) =
        resolve().beginRenderTarget(target, clearColor)

    override fun endRenderTarget() = resolve().endRenderTarget()

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
    ) = resolve().blitRenderTarget(
        source, destination, srcX, srcY, srcWidth, srcHeight, dstX, dstY, dstWidth, dstHeight, filter, copyColor, copyDepth
    )

    override fun blend(enabled: Boolean) = resolve().blend(enabled)
    override fun blendFunction(function: BlendFunction) = resolve().blendFunction(function)
    override fun colorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) =
        resolve().colorMask(red, green, blue, alpha)
    override fun depthTest(enabled: Boolean) = resolve().depthTest(enabled)
    override fun depthWrite(enabled: Boolean) = resolve().depthWrite(enabled)
    override fun depthCompare(compare: DepthCompare) = resolve().depthCompare(compare)
    override fun stencilTest(enabled: Boolean) = resolve().stencilTest(enabled)
    override fun stencilFunc(compare: StencilCompare, ref: Int, readMask: Int) =
        resolve().stencilFunc(compare, ref, readMask)
    override fun stencilOp(stencilFail: StencilOp, depthFail: StencilOp, pass: StencilOp) =
        resolve().stencilOp(stencilFail, depthFail, pass)
    override fun stencilMask(writeMask: Int) = resolve().stencilMask(writeMask)
    override fun cull(enabled: Boolean) = resolve().cull(enabled)
    override fun scissor(x: Int, y: Int, width: Int, height: Int) = resolve().scissor(x, y, width, height)
    override fun disableScissor() = resolve().disableScissor()
    override fun clearColor(red: Float, green: Float, blue: Float, alpha: Float) =
        resolve().clearColor(red, green, blue, alpha)
    override fun clearDepth(depth: Double) = resolve().clearDepth(depth)
    override fun clearStencil(value: Int) = resolve().clearStencil(value)
    override fun pushDebugGroup(label: String) = resolve().pushDebugGroup(label)
    override fun popDebugGroup() = resolve().popDebugGroup()
    override fun createTimestampPool(capacity: Int): GpuTimestampPool = resolve().createTimestampPool(capacity)
    override fun writeTimestamp(pool: GpuTimestampPool, queryIndex: Int) = resolve().writeTimestamp(pool, queryIndex)

    override fun invalidatePipelineCache() {
        resolved?.invalidatePipelineCache()
    }

    override fun close() {
        resolved?.close()
    }

    override fun hasContext(): Boolean = resolved?.hasContext() ?: false
}
