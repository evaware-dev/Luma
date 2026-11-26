package sweetie.evaware.luma.api

import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import sweetie.evaware.luma.shader.translator.ShaderReflection
import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.VertexCursor
import sweetie.evaware.luma.vertex.VertexInputLayout
import sweetie.evaware.luma.vertex.VertexLayout

interface FrameBackend {
    fun beginFrame()
    fun endFrame()
    fun close()
    fun hasContext(): Boolean
}

interface ProgramBackend {
    fun createProgram(
        vertexSource: String,
        fragmentSource: String,
        layouts: VertexInputLayout,
        reflection: ShaderReflection? = null
    ): ProgramHandle

    fun createProgram(
        vertexSource: String,
        fragmentSource: String,
        layout: VertexLayout,
        reflection: ShaderReflection? = null
    ): ProgramHandle = createProgram(vertexSource, fragmentSource, VertexInputLayout().binding(layout), reflection)

    fun bindProgram(program: ProgramHandle)
    fun invalidatePipelineCache() {}
}

interface TextureBackend {
    fun createTexture(
        image: BufferedImage,
        mipmap: Boolean = false,
        format: TextureFormat = TextureFormat.RGBA8,
        sampler: SamplerState = SamplerState.LINEAR_CLAMP
    ): TextureHandle

    fun createTexture(
        width: Int,
        height: Int,
        data: ByteBuffer? = null,
        format: TextureFormat = TextureFormat.RGBA8,
        mipmap: Boolean = false,
        sampler: SamplerState = SamplerState.LINEAR_CLAMP
    ): TextureHandle {
        throw UnsupportedOperationException("Raw texture creation is not supported")
    }

    fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage)

    fun updateTexture(
        texture: TextureHandle,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        data: ByteBuffer
    ) {
        throw UnsupportedOperationException("Raw texture updates are not supported")
    }

    fun setTextureSampler(texture: TextureHandle, sampler: SamplerState) {
        throw UnsupportedOperationException("Dynamic texture sampler updates are not supported")
    }

    fun generateMipmaps(texture: TextureHandle) {
        throw UnsupportedOperationException("Mipmap generation is not supported")
    }

    fun bindTexture(texture: TextureHandle, unit: Int = 0)
}

interface BufferBackend {
    fun createVertexBuffer(sizeBytes: Long, usage: BufferUsage): VertexBufferHandle =
        throw UnsupportedOperationException("GPU vertex buffers are not supported")

    fun createIndexBuffer(sizeBytes: Long, indexType: IndexType, usage: BufferUsage): IndexBufferHandle =
        throw UnsupportedOperationException("GPU index buffers are not supported")

    fun updateVertexBuffer(buffer: VertexBufferHandle, offsetBytes: Long, data: ByteBuffer) {
        throw UnsupportedOperationException("GPU vertex buffer updates are not supported")
    }

    fun updateIndexBuffer(buffer: IndexBufferHandle, offsetBytes: Long, data: ByteBuffer) {
        throw UnsupportedOperationException("GPU index buffer updates are not supported")
    }

    fun bindVertexBuffer(binding: Int, buffer: VertexBufferHandle, offsetBytes: Long = 0L) {
        throw UnsupportedOperationException("GPU vertex buffer binding is not supported")
    }

    fun bindIndexBuffer(buffer: IndexBufferHandle) {
        throw UnsupportedOperationException("GPU index buffer binding is not supported")
    }
}

interface RenderTargetBackend {
    fun createRenderTarget(
        width: Int,
        height: Int,
        useDepth: Boolean = false,
        format: RenderTargetFormat = RenderTargetFormat.RGBA8,
        sampler: SamplerState = SamplerState.LINEAR_CLAMP,
        useStencil: Boolean = false
    ): RenderTargetHandle

    fun beginRenderTarget(target: RenderTargetHandle, clearColor: FloatArray? = null)
    fun endRenderTarget()

    fun blitRenderTarget(
        source: RenderTargetHandle,
        destination: RenderTargetHandle?,
        srcX: Int = 0,
        srcY: Int = 0,
        srcWidth: Int = source.width,
        srcHeight: Int = source.height,
        dstX: Int = 0,
        dstY: Int = 0,
        dstWidth: Int = destination?.width ?: srcWidth,
        dstHeight: Int = destination?.height ?: srcHeight,
        filter: TextureFilter = TextureFilter.LINEAR,
        copyColor: Boolean = true,
        copyDepth: Boolean = false
    ) {
        throw UnsupportedOperationException("Render target blitting is not supported")
    }
}

interface StateBackend {
    val supportsStencil: Boolean get() = false

    val isDepthTestEnabled: Boolean get() = false
    val isDepthWriteEnabled: Boolean get() = false
    val currentDepthCompare: DepthCompare get() = DepthCompare.ALWAYS
    val colorMaskRed: Boolean get() = true
    val colorMaskGreen: Boolean get() = true
    val colorMaskBlue: Boolean get() = true
    val colorMaskAlpha: Boolean get() = true

    fun blend(enabled: Boolean) {}
    fun blendFunction(function: BlendFunction) {}
    fun colorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) {}
    fun depthTest(enabled: Boolean) {}
    fun depthWrite(enabled: Boolean) {}
    fun depthCompare(compare: DepthCompare) {}
    fun stencilTest(enabled: Boolean) {}
    fun stencilFunc(compare: StencilCompare, ref: Int, readMask: Int = 0xFF) {}
    fun stencilOp(stencilFail: StencilOp, depthFail: StencilOp, pass: StencilOp) {}
    fun stencilMask(writeMask: Int) {}
    fun cull(enabled: Boolean) {}

    fun scissor(x: Int, y: Int, width: Int, height: Int) {
        throw UnsupportedOperationException("Raster scissor is not supported")
    }

    fun disableScissor() {
        throw UnsupportedOperationException("Raster scissor is not supported")
    }

    fun clearColor(red: Float, green: Float, blue: Float, alpha: Float) {
        throw UnsupportedOperationException("Color attachment clearing is not supported")
    }

    fun clearDepth(depth: Double) {
        throw UnsupportedOperationException("Depth attachment clearing is not supported")
    }

    fun clearStencil(value: Int = 0) {
        throw UnsupportedOperationException("Stencil attachment clearing is not supported")
    }
}

interface DrawBackend {
    fun draw(
        program: ProgramHandle,
        uniforms: ShaderUniforms,
        primitiveType: PrimitiveType,
        firstVertex: Int,
        vertexCount: Int,
        instanceCount: Int = 1,
        firstInstance: Int = 0
    ) {
        throw UnsupportedOperationException("GPU buffer drawing is not supported")
    }

    fun drawIndexed(
        program: ProgramHandle,
        uniforms: ShaderUniforms,
        primitiveType: PrimitiveType,
        firstIndex: Int,
        indexCount: Int,
        vertexOffset: Int = 0,
        instanceCount: Int = 1,
        firstInstance: Int = 0
    ) {
        throw UnsupportedOperationException("Indexed drawing is not supported")
    }

    fun beginVertices(
        program: ProgramHandle,
        vertexCount: Int,
        cursor: VertexCursor
    )

    fun endVertices(
        program: ProgramHandle,
        cursor: VertexCursor,
        vertexCount: Int,
        uniforms: ShaderUniforms,
        primitiveType: PrimitiveType
    )
}

interface DebugBackend {
    val supportsGpuTimestamps: Boolean get() = false

    fun pushDebugGroup(label: String) {}
    fun popDebugGroup() {}

    fun createTimestampPool(capacity: Int): GpuTimestampPool {
        throw UnsupportedOperationException("GPU timestamp queries are not supported")
    }

    fun writeTimestamp(pool: GpuTimestampPool, queryIndex: Int) {
        throw UnsupportedOperationException("GPU timestamp queries are not supported")
    }
}

interface RenderBackend :
    FrameBackend,
    ProgramBackend,
    TextureBackend,
    BufferBackend,
    RenderTargetBackend,
    StateBackend,
    DrawBackend,
    DebugBackend

interface ProgramHandle : AutoCloseable {
    val vertexStrideBytes: Int get() = 0
}

interface TextureHandle : AutoCloseable {
    val width: Int
    val height: Int
    val format: TextureFormat get() = TextureFormat.RGBA8
}
