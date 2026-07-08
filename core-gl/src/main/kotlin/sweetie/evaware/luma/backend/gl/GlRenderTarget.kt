package sweetie.evaware.luma.backend.gl

import java.nio.ByteBuffer
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.TextureHandle

class GlRenderTarget(
    val fbo: Int,
    override val width: Int,
    override val height: Int,
    val color: GlTexture,
    private val depthRenderbuffer: Int
) : RenderTargetHandle {
    override val colorTexture: TextureHandle get() = color

    override fun close() {
        GL30.glDeleteFramebuffers(fbo)
        if (depthRenderbuffer != 0) {
            GL30.glDeleteRenderbuffers(depthRenderbuffer)
        }
        color.close()
    }

    companion object {
        fun create(
            width: Int,
            height: Int,
            useDepth: Boolean,
            format: RenderTargetFormat
        ): GlRenderTarget {
            val internalFormat = when (format) {
                RenderTargetFormat.RGBA8 -> GL11.GL_RGBA8
                RenderTargetFormat.RGBA16F -> GL30.GL_RGBA16F
            }
            val pixelType = when (format) {
                RenderTargetFormat.RGBA8 -> GL11.GL_UNSIGNED_BYTE
                RenderTargetFormat.RGBA16F -> GL11.GL_FLOAT
            }

            val textureId = GL11.glGenTextures()
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL14.GL_CLAMP_TO_EDGE)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL14.GL_CLAMP_TO_EDGE)
            GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0,
                GL11.GL_RGBA, pixelType, null as ByteBuffer?
            )
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0)

            val fboId = GL30.glGenFramebuffers()
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId)
            GL30.glFramebufferTexture2D(
                GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, textureId, 0
            )

            var depthRenderbuffer = 0
            if (useDepth) {
                depthRenderbuffer = GL30.glGenRenderbuffers()
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthRenderbuffer)
                GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL14.GL_DEPTH_COMPONENT24, width, height)
                GL30.glFramebufferRenderbuffer(
                    GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depthRenderbuffer
                )
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0)
            }

            val status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0)

            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                if (depthRenderbuffer != 0) GL30.glDeleteRenderbuffers(depthRenderbuffer)
                GL30.glDeleteFramebuffers(fboId)
                GL11.glDeleteTextures(textureId)
                error("Framebuffer status error: " + Integer.toString(status, 16))
            }

            val glTexture = GlTexture(textureId, width, height)
            return GlRenderTarget(fboId, width, height, glTexture, depthRenderbuffer)
        }
    }
}
