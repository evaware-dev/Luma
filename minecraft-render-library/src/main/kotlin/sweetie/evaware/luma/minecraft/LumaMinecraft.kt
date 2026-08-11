package sweetie.evaware.luma.minecraft

import sweetie.evaware.luma.GraphicsBackend
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.RenderBackend
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.api.RenderTargetFilter
import sweetie.evaware.luma.shader.translator.DefaultShaderTranslator
import sweetie.evaware.luma.shader.translator.ShaderTarget
import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.VertexLayout
import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import sweetie.evaware.luma.backend.gl.Backend as GlBackend
import sweetie.evaware.luma.backend.blaze3d.Backend as VulkanBackend

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
    override fun bindProgram(program: ProgramHandle) = delegate.bindProgram(program)
    override fun createTexture(image: BufferedImage, mipmap: Boolean): TextureHandle = delegate.createTexture(image, mipmap)
    override fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage) = delegate.updateTexture(texture, x, y, image)
    override fun bindTexture(texture: TextureHandle, unit: Int) = delegate.bindTexture(texture, unit)
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
    override fun depthTest(enabled: Boolean) = delegate.depthTest(enabled)
    override fun cull(enabled: Boolean) = delegate.cull(enabled)
    override fun close() { if (lazyDelegate.isInitialized()) delegate.close() }
    override fun hasContext(): Boolean = lazyDelegate.isInitialized() && delegate.hasContext()
}
