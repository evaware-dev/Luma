package sweetie.evaware.luma.backend.gl

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.TextureHandle

class GlRenderTarget(
    val fbo: Int,
    override val width: Int,
    override val height: Int,
    val color: GlTexture
) : RenderTargetHandle {
    override val colorTexture: TextureHandle get() = color

    override fun close() {
        GL30.glDeleteFramebuffers(fbo)
        color.close()
    }

    companion object {
        fun create(width: Int, height: Int): GlRenderTarget {
            val textureId = GL11.glGenTextures()
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL14.GL_CLAMP_TO_EDGE)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL14.GL_CLAMP_TO_EDGE)
            GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, null as java.nio.ByteBuffer?
            )
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0)

            val fboId = GL30.glGenFramebuffers()
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId)
            GL30.glFramebufferTexture2D(
                GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, textureId, 0
            )

            val status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0)

            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                error("Framebuffer status error: " + Integer.toString(status, 16))
            }

            val glTexture = GlTexture(textureId, width, height)
            return GlRenderTarget(fboId, width, height, glTexture)
        }
    }
}
