package sweetie.evaware.luma.backend.gl

import java.nio.ByteBuffer
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.SamplerState
import sweetie.evaware.luma.api.TextureFilter
import sweetie.evaware.luma.api.TextureFormat
import sweetie.evaware.luma.api.TextureHandle

class GlRenderTarget(
    val fbo: Int,
    override val width: Int,
    override val height: Int,
    val color: GlTexture,
    val depth: GlTexture?,
    private val ownsFramebuffer: Boolean = true,
    override val format: RenderTargetFormat = RenderTargetFormat.RGBA8,
    override val hasStencil: Boolean = false
) : RenderTargetHandle {
    override val colorTexture: TextureHandle get() = color
    override val depthTexture: TextureHandle? get() = depth
    private var closed = false

    override fun close() {
        if (closed) return
        closed = true
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
            depth: GlTexture? = null,
            format: RenderTargetFormat = RenderTargetFormat.RGBA8,
            hasStencil: Boolean = false
        ): GlRenderTarget = GlRenderTarget(fbo, width, height, color, depth, false, format, hasStencil)

        @JvmOverloads
        fun create(
            width: Int,
            height: Int,
            useDepth: Boolean = false,
            format: RenderTargetFormat = RenderTargetFormat.RGBA8,
            sampler: SamplerState = SamplerState.LINEAR_CLAMP,
            useStencil: Boolean = false,
            colorSamplerId: Int = 0,
            ownsColorSampler: Boolean = colorSamplerId == 0,
            depthSamplerId: Int = 0,
            ownsDepthSampler: Boolean = depthSamplerId == 0
        ): GlRenderTarget {
            require(width > 0 && height > 0) { "Render target dimensions must be positive: ${width}x${height}" }
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
            val colorTexFormat = when (format) {
                RenderTargetFormat.RGBA8 -> TextureFormat.RGBA8
                RenderTargetFormat.RGBA16F -> TextureFormat.RGBA16F
            }

            var textureId = 0
            var fboId = 0
            var depthTextureId = 0
            var resolvedColorSamplerId = colorSamplerId
            var resolvedOwnsColorSampler = ownsColorSampler
            var resolvedDepthSamplerId = depthSamplerId
            var resolvedOwnsDepthSampler = ownsDepthSampler
            try {
                if (resolvedColorSamplerId == 0) {
                    resolvedColorSamplerId = GlSamplerCache.createOwnedSampler(sampler, mipmap = false)
                    resolvedOwnsColorSampler = true
                }
                textureId = GL11.glGenTextures()
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
                val glMinFilter = if (sampler.minFilter == TextureFilter.NEAREST) GL11.GL_NEAREST else GL11.GL_LINEAR
                val glMagFilter = if (sampler.magFilter == TextureFilter.NEAREST) GL11.GL_NEAREST else GL11.GL_LINEAR
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, glMinFilter)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, glMagFilter)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
                GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0,
                    GL11.GL_RGBA, pixelType, null as ByteBuffer?
                )

                fboId = GL30.glGenFramebuffers()
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId)
                GL30.glFramebufferTexture2D(
                    GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, textureId, 0
                )

                if (useDepth || useStencil) {
                    depthTextureId = GL11.glGenTextures()
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTextureId)
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
                    if (useStencil) {
                        GL11.glTexImage2D(
                            GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH24_STENCIL8, width, height, 0,
                            GL30.GL_DEPTH_STENCIL, GL30.GL_UNSIGNED_INT_24_8, null as ByteBuffer?
                        )
                        GL30.glFramebufferTexture2D(
                            GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL11.GL_TEXTURE_2D, depthTextureId, 0
                        )
                    } else {
                        GL11.glTexImage2D(
                            GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, width, height, 0,
                            GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, null as ByteBuffer?
                        )
                        GL30.glFramebufferTexture2D(
                            GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthTextureId, 0
                        )
                    }
                    if (resolvedDepthSamplerId == 0) {
                        resolvedDepthSamplerId = GlSamplerCache.createOwnedSampler(SamplerState.NEAREST_CLAMP, mipmap = false)
                        resolvedOwnsDepthSampler = true
                    }
                }

                val status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)
                if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                    error("Framebuffer status error: " + Integer.toString(status, 16))
                }

                val glTexture = GlTexture(
                    textureId = textureId,
                    width = width,
                    height = height,
                    mipmap = false,
                    samplerId = resolvedColorSamplerId,
                    ownsTexture = true,
                    format = colorTexFormat,
                    samplerState = sampler,
                    ownsSampler = resolvedOwnsColorSampler
                )
                val glDepth = depthTextureId.takeIf { it != 0 }?.let {
                    GlTexture(
                        textureId = it,
                        width = width,
                        height = height,
                        mipmap = false,
                        samplerId = resolvedDepthSamplerId,
                        ownsTexture = true,
                        format = TextureFormat.RGBA8,
                        samplerState = SamplerState.NEAREST_CLAMP,
                        ownsSampler = resolvedOwnsDepthSampler
                    )
                }
                return GlRenderTarget(fboId, width, height, glTexture, glDepth, true, format, useStencil)
            } catch (failure: Throwable) {
                if (depthTextureId != 0) GL11.glDeleteTextures(depthTextureId)
                if (fboId != 0) GL30.glDeleteFramebuffers(fboId)
                if (textureId != 0) GL11.glDeleteTextures(textureId)
                if (resolvedOwnsColorSampler && resolvedColorSamplerId != 0) {
                    org.lwjgl.opengl.GL33.glDeleteSamplers(resolvedColorSamplerId)
                }
                if (resolvedOwnsDepthSampler && resolvedDepthSamplerId != 0) {
                    org.lwjgl.opengl.GL33.glDeleteSamplers(resolvedDepthSamplerId)
                }
                throw failure
            } finally {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture)
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer)
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer)
            }
        }
    }
}
