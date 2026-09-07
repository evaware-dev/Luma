package sweetie.evaware.luma.api

import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.VertexLayout

interface RenderBackend {
    fun beginFrame()
    fun endFrame()
    fun createProgram(vertexSource: String, fragmentSource: String, layout: VertexLayout): ProgramHandle
    fun bindProgram(program: ProgramHandle)
    fun createTexture(image: BufferedImage, mipmap: Boolean): TextureHandle
    fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage)
    fun bindTexture(texture: TextureHandle, unit: Int)
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
    fun invalidatePipelineCache() {}
    fun close()
    fun hasContext(): Boolean
}

interface ProgramHandle : AutoCloseable

interface TextureHandle : AutoCloseable {
    val width: Int
    val height: Int
}
