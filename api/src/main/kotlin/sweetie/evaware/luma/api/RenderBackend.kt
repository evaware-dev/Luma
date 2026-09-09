package sweetie.evaware.luma.api

import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.VertexInputLayout
import sweetie.evaware.luma.vertex.VertexLayout

interface RenderBackend {
    fun beginFrame()
    fun endFrame()
    fun createProgram(vertexSource: String, fragmentSource: String, layout: VertexLayout): ProgramHandle
    fun createProgram(vertexSource: String, fragmentSource: String, layouts: VertexInputLayout): ProgramHandle {
        require(layouts.size() == 1) { "This backend does not support multiple vertex bindings" }
        return createProgram(vertexSource, fragmentSource, layouts.layout(0))
    }
    fun bindProgram(program: ProgramHandle)
    fun createTexture(image: BufferedImage, mipmap: Boolean): TextureHandle
    fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage)
    fun bindTexture(texture: TextureHandle, unit: Int)
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
    fun draw(
        program: ProgramHandle,
        vertices: FloatBuffer,
        vertexCount: Int,
        uniforms: ShaderUniforms,
        primitiveType: PrimitiveType
    )
    fun createRenderTarget(width: Int, height: Int, useDepth: Boolean, format: RenderTargetFormat): RenderTargetHandle
    fun createRenderTarget(
        width: Int,
        height: Int,
        useDepth: Boolean,
        format: RenderTargetFormat,
        filter: RenderTargetFilter
    ): RenderTargetHandle = throw UnsupportedOperationException("Render target filtering is not supported")
    fun beginRenderTarget(target: RenderTargetHandle, clearColor: FloatArray?)
    fun endRenderTarget()
    fun blend(enabled: Boolean) {}
    fun blendFunction(function: BlendFunction) {}
    fun depthTest(enabled: Boolean) {}
    fun depthWrite(enabled: Boolean) {}
    fun depthCompare(compare: DepthCompare) {}
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
    fun invalidatePipelineCache() {}
    fun close()
    fun hasContext(): Boolean
}

interface ProgramHandle : AutoCloseable

interface TextureHandle : AutoCloseable {
    val width: Int
    val height: Int
}
