package sweetie.evaware.luma.minecraft

import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import sweetie.evaware.luma.GraphicsBackend
import sweetie.evaware.luma.Luma
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
import sweetie.evaware.luma.backend.blaze3d.Backend as VulkanBackend
import sweetie.evaware.luma.backend.gl.Backend as GlBackend
import sweetie.evaware.luma.shader.translator.DefaultShaderTranslator
import sweetie.evaware.luma.shader.translator.ShaderTarget
import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.VertexLayout
import sweetie.evaware.luma.vertex.VertexInputLayout

object LumaMinecraft {
    fun install() {
        Luma.platform = MinecraftRenderPlatform
        Luma.shaderTranslator = DefaultShaderTranslator {
            if (MinecraftRenderPlatform.activeBackend == GraphicsBackend.OPENGL) {
                ShaderTarget.OPENGL
            } else {
                ShaderTarget.BLAZE3D
            }
        }
        Luma.backend = LazyBackend()
    }
}

class LazyBackend : RenderBackend {
    private val lazyDelegate = lazy {
        if (MinecraftRenderPlatform.activeBackend == GraphicsBackend.OPENGL) {
            GlBackend()
        } else {
            VulkanBackend()
        }
    }
    private val delegate: RenderBackend get() = lazyDelegate.value

    override fun beginFrame() = delegate.beginFrame()
    override fun endFrame() = delegate.endFrame()
    override fun createProgram(vertexSource: String, fragmentSource: String, layout: VertexLayout): ProgramHandle =
        delegate.createProgram(vertexSource, fragmentSource, layout)
    override fun createProgram(vertexSource: String, fragmentSource: String, layouts: VertexInputLayout): ProgramHandle =
        delegate.createProgram(vertexSource, fragmentSource, layouts)
    override fun bindProgram(program: ProgramHandle) = delegate.bindProgram(program)
    override fun createTexture(image: BufferedImage, mipmap: Boolean): TextureHandle = delegate.createTexture(image, mipmap)
    override fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage) = delegate.updateTexture(texture, x, y, image)
    override fun bindTexture(texture: TextureHandle, unit: Int) = delegate.bindTexture(texture, unit)
    override fun createVertexBuffer(sizeBytes: Long, usage: BufferUsage) = delegate.createVertexBuffer(sizeBytes, usage)
    override fun createIndexBuffer(sizeBytes: Long, indexType: IndexType, usage: BufferUsage) =
        delegate.createIndexBuffer(sizeBytes, indexType, usage)
    override fun updateVertexBuffer(buffer: VertexBufferHandle, offsetBytes: Long, data: ByteBuffer) =
        delegate.updateVertexBuffer(buffer, offsetBytes, data)
    override fun updateIndexBuffer(buffer: IndexBufferHandle, offsetBytes: Long, data: ByteBuffer) =
        delegate.updateIndexBuffer(buffer, offsetBytes, data)
    override fun bindVertexBuffer(binding: Int, buffer: VertexBufferHandle, offsetBytes: Long) =
        delegate.bindVertexBuffer(binding, buffer, offsetBytes)
    override fun bindIndexBuffer(buffer: IndexBufferHandle) = delegate.bindIndexBuffer(buffer)
    override fun draw(
        program: ProgramHandle,
        uniforms: ShaderUniforms,
        primitiveType: PrimitiveType,
        firstVertex: Int,
        vertexCount: Int,
        instanceCount: Int,
        firstInstance: Int
    ) = delegate.draw(program, uniforms, primitiveType, firstVertex, vertexCount, instanceCount, firstInstance)
    override fun drawIndexed(
        program: ProgramHandle,
        uniforms: ShaderUniforms,
        primitiveType: PrimitiveType,
        firstIndex: Int,
        indexCount: Int,
        vertexOffset: Int,
        instanceCount: Int,
        firstInstance: Int
    ) = delegate.drawIndexed(
        program, uniforms, primitiveType, firstIndex, indexCount, vertexOffset, instanceCount, firstInstance
    )
    override fun draw(program: ProgramHandle, vertices: FloatBuffer, vertexCount: Int, uniforms: ShaderUniforms, primitiveType: PrimitiveType) =
        delegate.draw(program, vertices, vertexCount, uniforms, primitiveType)
    override fun createRenderTarget(width: Int, height: Int, useDepth: Boolean, format: RenderTargetFormat): RenderTargetHandle =
        delegate.createRenderTarget(width, height, useDepth, format)
    override fun createRenderTarget(
        width: Int,
        height: Int,
        useDepth: Boolean,
        format: RenderTargetFormat,
        filter: RenderTargetFilter
    ): RenderTargetHandle = delegate.createRenderTarget(width, height, useDepth, format, filter)
    override fun beginRenderTarget(target: RenderTargetHandle, clearColor: FloatArray?) =
        delegate.beginRenderTarget(target, clearColor)
    override fun endRenderTarget() =
        delegate.endRenderTarget()
    override fun blend(enabled: Boolean) = delegate.blend(enabled)
    override fun blendFunction(function: BlendFunction) = delegate.blendFunction(function)
    override fun depthTest(enabled: Boolean) = delegate.depthTest(enabled)
    override fun depthWrite(enabled: Boolean) = delegate.depthWrite(enabled)
    override fun depthCompare(compare: DepthCompare) = delegate.depthCompare(compare)
    override fun cull(enabled: Boolean) = delegate.cull(enabled)
    override fun scissor(x: Int, y: Int, width: Int, height: Int) = delegate.scissor(x, y, width, height)
    override fun disableScissor() = delegate.disableScissor()
    override fun clearColor(red: Float, green: Float, blue: Float, alpha: Float) =
        delegate.clearColor(red, green, blue, alpha)
    override fun clearDepth(depth: Double) = delegate.clearDepth(depth)
    override fun invalidatePipelineCache() {
        if (lazyDelegate.isInitialized()) delegate.invalidatePipelineCache()
    }
    override fun close() { if (lazyDelegate.isInitialized()) delegate.close() }
    override fun hasContext(): Boolean = lazyDelegate.isInitialized() && delegate.hasContext()
}
