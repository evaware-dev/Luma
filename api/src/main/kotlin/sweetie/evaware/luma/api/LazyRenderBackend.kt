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

    override fun beginFrame() = resolve().beginFrame()
    override fun endFrame() = resolve().endFrame()
    override fun createProgram(vertexSource: String, fragmentSource: String, layout: VertexLayout): ProgramHandle =
        resolve().createProgram(vertexSource, fragmentSource, layout)
    override fun createProgram(
        vertexSource: String,
        fragmentSource: String,
        layout: VertexLayout,
        reflection: ShaderReflection?
    ): ProgramHandle = resolve().createProgram(vertexSource, fragmentSource, layout, reflection)
    override fun createProgram(vertexSource: String, fragmentSource: String, layouts: VertexInputLayout): ProgramHandle =
        resolve().createProgram(vertexSource, fragmentSource, layouts)
    override fun createProgram(
        vertexSource: String,
        fragmentSource: String,
        layouts: VertexInputLayout,
        reflection: ShaderReflection?
    ): ProgramHandle = resolve().createProgram(vertexSource, fragmentSource, layouts, reflection)
    override fun bindProgram(program: ProgramHandle) = resolve().bindProgram(program)
    override fun createTexture(image: BufferedImage, mipmap: Boolean): TextureHandle =
        resolve().createTexture(image, mipmap)
    override fun createTexture(image: BufferedImage, mipmap: Boolean, format: TextureFormat): TextureHandle =
        resolve().createTexture(image, mipmap, format)
    override fun createTexture(
        width: Int,
        height: Int,
        data: ByteBuffer?,
        format: TextureFormat,
        mipmap: Boolean
    ): TextureHandle = resolve().createTexture(width, height, data, format, mipmap)
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
        format: RenderTargetFormat
    ): RenderTargetHandle = resolve().createRenderTarget(width, height, useDepth, format)
    override fun createRenderTarget(
        width: Int,
        height: Int,
        useDepth: Boolean,
        format: RenderTargetFormat,
        filter: RenderTargetFilter
    ): RenderTargetHandle = resolve().createRenderTarget(width, height, useDepth, format, filter)
    override fun beginRenderTarget(target: RenderTargetHandle, clearColor: FloatArray?) =
        resolve().beginRenderTarget(target, clearColor)
    override fun endRenderTarget() = resolve().endRenderTarget()
    override fun blend(enabled: Boolean) = resolve().blend(enabled)
    override fun blendFunction(function: BlendFunction) = resolve().blendFunction(function)
    override fun depthTest(enabled: Boolean) = resolve().depthTest(enabled)
    override fun depthWrite(enabled: Boolean) = resolve().depthWrite(enabled)
    override fun depthCompare(compare: DepthCompare) = resolve().depthCompare(compare)
    override fun cull(enabled: Boolean) = resolve().cull(enabled)
    override fun scissor(x: Int, y: Int, width: Int, height: Int) = resolve().scissor(x, y, width, height)
    override fun disableScissor() = resolve().disableScissor()
    override fun clearColor(red: Float, green: Float, blue: Float, alpha: Float) =
        resolve().clearColor(red, green, blue, alpha)
    override fun clearDepth(depth: Double) = resolve().clearDepth(depth)
    override fun pushDebugGroup(label: String) = resolve().pushDebugGroup(label)
    override fun popDebugGroup() = resolve().popDebugGroup()
    override fun invalidatePipelineCache() {
        resolved?.invalidatePipelineCache()
    }
    override fun close() {
        resolved?.close()
    }
    override fun hasContext(): Boolean = resolved?.hasContext() ?: false
}
