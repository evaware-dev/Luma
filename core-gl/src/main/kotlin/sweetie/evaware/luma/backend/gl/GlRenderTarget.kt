package sweetie.evaware.luma.backend.gl

import java.nio.ByteBuffer
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.api.RenderTargetFilter

class GlRenderTarget(
    val fbo: Int,
    override val width: Int,
    override val height: Int,
    val color: GlTexture,
    val depth: GlTexture?,
    private val ownsFramebuffer: Boolean = true
) : RenderTargetHandle {
    override val colorTexture: TextureHandle get() = color
    override val depthTexture: TextureHandle? get() = depth

    override fun close() {
        if (ownsFramebuffer) GL30.glDeleteFramebuffers(fbo)
        depth?.close()
        color.close()
    }

    companion object {
        fun borrow(
            fbo: Int,
            width: Int,
            height: Int,
            color: GlTexture,
            depth: GlTexture? = null
        ): GlRenderTarget = GlRenderTarget(fbo, width, height, color, depth, false)

        fun create(
            width: Int,
            height: Int,
            useDepth: Boolean,
            format: RenderTargetFormat
        ): GlRenderTarget = create(width, height, useDepth, format, RenderTargetFilter.NEAREST)

        fun create(
            width: Int,
            height: Int,
            useDepth: Boolean,
            format: RenderTargetFormat,
            filter: RenderTargetFilter
        ): GlRenderTarget {
            val previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
            val previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
            val previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
            val internalFormat = when (format) {
                RenderTargetFormat.RGBA8 -> GL11.GL_RGBA8
                RenderTargetFormat.RGBA16F -> GL30.GL_RGBA16F
            }
            val pixelType = when (format) {
                RenderTargetFormat.RGBA8 -> GL11.GL_UNSIGNED_BYTE
                RenderTargetFormat.RGBA16F -> GL11.GL_FLOAT
            }

            var textureId = 0
            var fboId = 0
            var depthTextureId = 0
            try {
                textureId = GL11.glGenTextures()
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
                val glFilter = when (filter) {
                    RenderTargetFilter.NEAREST -> GL11.GL_NEAREST
                    RenderTargetFilter.LINEAR -> GL11.GL_LINEAR
                }
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, glFilter)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, glFilter)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL14.GL_CLAMP_TO_EDGE)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL14.GL_CLAMP_TO_EDGE)
                GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0,
                    GL11.GL_RGBA, pixelType, null as ByteBuffer?
                )

                fboId = GL30.glGenFramebuffers()
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId)
                GL30.glFramebufferTexture2D(
                    GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, textureId, 0
                )

                if (useDepth) {
                    depthTextureId = GL11.glGenTextures()
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTextureId)
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL14.GL_CLAMP_TO_EDGE)
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL14.GL_CLAMP_TO_EDGE)
                    GL11.glTexImage2D(
                        GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, width, height, 0,
                        GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, null as ByteBuffer?
                    )
                    GL30.glFramebufferTexture2D(
                        GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthTextureId, 0
                    )
                }

                val status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)
                if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                    if (depthTextureId != 0) GL11.glDeleteTextures(depthTextureId)
                    GL30.glDeleteFramebuffers(fboId)
                    GL11.glDeleteTextures(textureId)
                    error("Framebuffer status error: " + Integer.toString(status, 16))
                }

                val glTexture = GlTexture(textureId, width, height)
                val glDepth = depthTextureId.takeIf { it != 0 }?.let { GlTexture(it, width, height) }
                return GlRenderTarget(fboId, width, height, glTexture, glDepth)
            } finally {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture)
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer)
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer)
            }
        }
    }
}
