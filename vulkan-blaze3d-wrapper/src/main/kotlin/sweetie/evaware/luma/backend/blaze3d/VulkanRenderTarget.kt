package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.GpuSampler
import java.util.function.Supplier
import sweetie.evaware.luma.LumaNames
import sweetie.evaware.luma.api.RenderTargetFilter
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.TextureHandle

class VulkanRenderTarget(
    val gpuTexture: GpuTexture,
    val colorView: GpuTextureView,
    private val colorAsTexture: VulkanTexture,
    val gpuDepthTexture: GpuTexture?,
    val depthView: GpuTextureView?,
    private val depthAsTexture: VulkanTexture?,
    override val width: Int,
    override val height: Int,
    private val ownsAttachments: Boolean = true
) : RenderTargetHandle {
    override val colorTexture: TextureHandle get() = colorAsTexture
    override val depthTexture: TextureHandle? get() = depthAsTexture
    private var closed = false

    internal fun requireOpen() = check(!closed) { "Render target is closed" }

    override fun close() {
        if (closed) return
        closed = true
        colorAsTexture.close()
        depthAsTexture?.close()
        if (ownsAttachments && (depthView != null || gpuDepthTexture != null)) {
            VulkanResourceRetirement.defer {
                depthView?.close()
                gpuDepthTexture?.close()
            }
        }
    }

    companion object {
        private const val COLOR_USAGE = GpuTexture.USAGE_COPY_DST or
            GpuTexture.USAGE_COPY_SRC or
            GpuTexture.USAGE_RENDER_ATTACHMENT or GpuTexture.USAGE_TEXTURE_BINDING
        private const val DEPTH_USAGE = GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_COPY_SRC or
            GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_RENDER_ATTACHMENT

        internal fun create(
            width: Int,
            height: Int,
            useDepth: Boolean,
            format: RenderTargetFormat,
            sampler: GpuSampler,
            uploads: TextureUploadQueue
        ): VulkanRenderTarget {
            val device = RenderSystem.getDevice()
            val gpuFormat = when (format) {
                RenderTargetFormat.RGBA8 -> GpuFormat.RGBA8_UNORM
                RenderTargetFormat.RGBA16F -> GpuFormat.RGBA16_FLOAT
            }

            var texture: GpuTexture? = null
            var textureView: GpuTextureView? = null
            var depthTexture: GpuTexture? = null
            var depthView: GpuTextureView? = null
            try {
                texture = device.createTexture(
                    Supplier { LumaNames.TEXTURE },
                    COLOR_USAGE,
                    gpuFormat,
                    width,
                    height,
                    1,
                    1
                )
                textureView = device.createTextureView(texture)
                if (useDepth) {
                    depthTexture = device.createTexture(
                        Supplier { LumaNames.DEPTH_TEXTURE },
                        DEPTH_USAGE,
                        GpuFormat.D32_FLOAT,
                        width,
                        height,
                        1,
                        1
                    )
                    depthView = device.createTextureView(depthTexture)
                }

                val colorTexture = VulkanTexture(texture, textureView, sampler, width, height, uploads)
                val depthAsTexture = if (depthTexture != null && depthView != null) {
                    VulkanTexture(depthTexture, depthView, sampler, width, height, null, false, false)
                } else {
                    null
                }
                return VulkanRenderTarget(
                    texture,
                    textureView,
                    colorTexture,
                    depthTexture,
                    depthView,
                    depthAsTexture,
                    width,
                    height
                )
            } catch (throwable: Throwable) {
                depthView?.close()
                depthTexture?.close()
                textureView?.close()
                texture?.close()
                throw throwable
            }
        }

        fun borrow(
            colorView: GpuTextureView,
            depthView: GpuTextureView? = null,
            filter: RenderTargetFilter = RenderTargetFilter.NEAREST
        ): VulkanRenderTarget {
            val color = VulkanTexture.borrow(colorView, filter)
            var depth: VulkanTexture? = null
            try {
                depth = depthView?.let { VulkanTexture.borrow(it, RenderTargetFilter.NEAREST) }
                return VulkanRenderTarget(
                    colorView.texture(),
                    colorView,
                    color,
                    depthView?.texture(),
                    depthView,
                    depth,
                    colorView.getWidth(0),
                    colorView.getHeight(0),
                    false
                )
            } catch (failure: Throwable) {
                depth?.close()
                color.close()
                throw failure
            }
        }
    }
}
