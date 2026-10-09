package sweetie.evaware.luma.backend.blaze3d

import sweetie.evaware.luma.LumaNames
import sweetie.evaware.luma.api.RenderTargetFilter
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.backend.blaze3d.preset.PresetFormat
import sweetie.evaware.luma.backend.blaze3d.preset.PresetGpuSampler
import sweetie.evaware.luma.backend.blaze3d.preset.PresetGpuTexture
import sweetie.evaware.luma.backend.blaze3d.preset.PresetGpuTextureView
import sweetie.evaware.luma.backend.blaze3d.preset.VulkanPreset
import sweetie.evaware.luma.backend.blaze3d.preset.VulkanPresets

class VulkanRenderTarget(
    val gpuTexture: PresetGpuTexture,
    val colorView: PresetGpuTextureView,
    private val colorAsTexture: VulkanTexture,
    val gpuDepthTexture: PresetGpuTexture?,
    val depthView: PresetGpuTextureView?,
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
        private const val COLOR_USAGE = 1 or 2 or 4 or 8
        private const val DEPTH_USAGE = 1 or 2 or 4 or 8

        internal fun create(
            width: Int,
            height: Int,
            useDepth: Boolean,
            format: RenderTargetFormat,
            sampler: PresetGpuSampler,
            uploads: TextureUploadQueue,
            preset: VulkanPreset = VulkanPresets.active
        ): VulkanRenderTarget {
            val presetFormat = when (format) {
                RenderTargetFormat.RGBA8 -> PresetFormat.RGBA8_UNORM
                RenderTargetFormat.RGBA16F -> PresetFormat.RGBA16_FLOAT
            }

            var texture: PresetGpuTexture? = null
            var textureView: PresetGpuTextureView? = null
            var depthTexture: PresetGpuTexture? = null
            var depthView: PresetGpuTextureView? = null
            try {
                texture = preset.createTexture(
                    { LumaNames.TEXTURE },
                    COLOR_USAGE,
                    presetFormat,
                    width,
                    height
                )
                textureView = preset.createTextureView(texture)
                if (useDepth) {
                    depthTexture = preset.createTexture(
                        { LumaNames.DEPTH_TEXTURE },
                        DEPTH_USAGE,
                        PresetFormat.D32_FLOAT,
                        width,
                        height
                    )
                    depthView = preset.createTextureView(depthTexture)
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
            colorView: Any,
            depthView: Any? = null,
            filter: RenderTargetFilter = RenderTargetFilter.NEAREST,
            preset: VulkanPreset = VulkanPresets.active
        ): VulkanRenderTarget {
            val color = VulkanTexture.borrow(colorView, filter, preset)
            var depth: VulkanTexture? = null
            try {
                depth = depthView?.let { VulkanTexture.borrow(it, RenderTargetFilter.NEAREST, preset) }
                val wrappedColor = color.view
                val wrappedDepth = depth?.view
                return VulkanRenderTarget(
                    wrappedColor.texture,
                    wrappedColor,
                    color,
                    wrappedDepth?.texture,
                    wrappedDepth,
                    depth,
                    wrappedColor.getWidth(0),
                    wrappedColor.getHeight(0),
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
