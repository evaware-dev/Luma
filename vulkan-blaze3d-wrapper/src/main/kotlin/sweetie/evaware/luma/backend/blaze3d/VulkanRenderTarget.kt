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

class VulkanRenderTarget internal constructor(
    val gpuTexture: PresetGpuTexture,
    val colorView: PresetGpuTextureView,
    private val colorAsTexture: VulkanTexture,
    val gpuDepthTexture: PresetGpuTexture?,
    val depthView: PresetGpuTextureView?,
    private val depthAsTexture: VulkanTexture?,
    override val width: Int,
    override val height: Int,
    private val ownsAttachments: Boolean,
    private var retirement: VulkanRetirementQueue?
) : RenderTargetHandle {
    constructor(
        gpuTexture: PresetGpuTexture,
        colorView: PresetGpuTextureView,
        colorAsTexture: VulkanTexture,
        gpuDepthTexture: PresetGpuTexture?,
        depthView: PresetGpuTextureView?,
        depthAsTexture: VulkanTexture?,
        width: Int,
        height: Int,
        ownsAttachments: Boolean = true
    ) : this(
        gpuTexture,
        colorView,
        colorAsTexture,
        gpuDepthTexture,
        depthView,
        depthAsTexture,
        width,
        height,
        ownsAttachments,
        null
    )

    override val colorTexture: TextureHandle get() = colorAsTexture
    override val depthTexture: TextureHandle? get() = depthAsTexture
    private var closed = false

    internal fun requireOpen() = check(!closed) { "Render target is closed" }

    internal fun attachRetirement(queue: VulkanRetirementQueue) {
        if (retirement == null) {
            retirement = queue
        }
        colorAsTexture.attachRetirement(queue)
        depthAsTexture?.attachRetirement(queue)
    }

    override fun close() {
        if (closed) return
        closed = true
        colorAsTexture.close()
        depthAsTexture?.close()
        if (ownsAttachments && (depthView != null || gpuDepthTexture != null)) {
            val queue = retirement
            if (queue != null) {
                if (depthView != null) queue.defer(depthView)
                if (gpuDepthTexture != null) queue.defer(gpuDepthTexture)
            } else {
                var failure: Throwable? = null
                if (depthView != null) {
                    try {
                        depthView.close()
                    } catch (throwable: Throwable) {
                        failure = throwable
                    }
                }
                if (gpuDepthTexture != null) {
                    try {
                        gpuDepthTexture.close()
                    } catch (throwable: Throwable) {
                        if (failure == null) failure = throwable else failure.addSuppressed(throwable)
                    }
                }
                if (failure != null) throw failure
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
            preset: VulkanPreset,
            retirement: VulkanRetirementQueue
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

                val colorTexture = VulkanTexture(texture, textureView, sampler, width, height, uploads, retirement = retirement)
                val depthAsTexture = if (depthTexture != null && depthView != null) {
                    VulkanTexture(depthTexture, depthView, sampler, width, height, null, false, false, false, retirement)
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
                    height,
                    true,
                    retirement
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
        ): VulkanRenderTarget = borrow(colorView, depthView, filter, preset, VulkanTexture.activeBackendRetirement(preset))

        internal fun borrow(
            colorView: Any,
            depthView: Any?,
            filter: RenderTargetFilter,
            preset: VulkanPreset,
            retirement: VulkanRetirementQueue?
        ): VulkanRenderTarget {
            val color = VulkanTexture.borrow(colorView, filter, preset, retirement)
            var depth: VulkanTexture? = null
            try {
                depth = depthView?.let { VulkanTexture.borrow(it, RenderTargetFilter.NEAREST, preset, retirement) }
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
                    false,
                    retirement
                )
            } catch (failure: Throwable) {
                depth?.close()
                color.close()
                throw failure
            }
        }
    }
}
