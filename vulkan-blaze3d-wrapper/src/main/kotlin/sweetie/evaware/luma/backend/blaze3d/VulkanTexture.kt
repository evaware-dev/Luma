package sweetie.evaware.luma.backend.blaze3d

import java.awt.image.BufferedImage
import sweetie.evaware.luma.LumaNames
import sweetie.evaware.luma.api.RenderTargetFilter
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.backend.blaze3d.preset.PresetAddressMode
import sweetie.evaware.luma.backend.blaze3d.preset.PresetFilterMode
import sweetie.evaware.luma.backend.blaze3d.preset.PresetFormat
import sweetie.evaware.luma.backend.blaze3d.preset.PresetGpuSampler
import sweetie.evaware.luma.backend.blaze3d.preset.PresetGpuTexture
import sweetie.evaware.luma.backend.blaze3d.preset.PresetGpuTextureView
import sweetie.evaware.luma.backend.blaze3d.preset.VulkanPreset
import sweetie.evaware.luma.backend.blaze3d.preset.VulkanPresets

class VulkanTexture internal constructor(
    val gpuTexture: PresetGpuTexture,
    val view: PresetGpuTextureView,
    val sampler: PresetGpuSampler,
    override val width: Int,
    override val height: Int,
    private val uploads: TextureUploadQueue?,
    private val ownsTexture: Boolean = true,
    private val ownsView: Boolean = true,
    private val ownsSampler: Boolean = false
) : TextureHandle {
    private var closed = false

    internal fun requireOpen() = check(!closed) { "Texture is closed" }

    fun update(x: Int, y: Int, image: BufferedImage) {
        requireOpen()
        checkNotNull(uploads) { "Borrowed textures cannot be updated" }.enqueue(gpuTexture, image, x, y)
    }

    override fun close() {
        if (closed) return
        closed = true
        VulkanResourceRetirement.defer {
            if (ownsSampler) sampler.close()
            if (ownsView) view.close()
            if (ownsTexture) gpuTexture.close()
        }
    }

    companion object {
        fun borrow(
            view: Any,
            filter: RenderTargetFilter = RenderTargetFilter.NEAREST,
            preset: VulkanPreset = VulkanPresets.active
        ): VulkanTexture {
            val wrapped = preset.wrapTextureView(view)
            val presetFilter = when (filter) {
                RenderTargetFilter.NEAREST -> PresetFilterMode.NEAREST
                RenderTargetFilter.LINEAR -> PresetFilterMode.LINEAR
            }
            val sampler = preset.createSampler(
                PresetAddressMode.CLAMP_TO_EDGE,
                presetFilter,
                1
            )
            return VulkanTexture(
                wrapped.texture,
                wrapped,
                sampler,
                wrapped.getWidth(0),
                wrapped.getHeight(0),
                null,
                false,
                false,
                true
            )
        }

        internal fun create(
            image: BufferedImage,
            sampler: PresetGpuSampler,
            uploads: TextureUploadQueue,
            preset: VulkanPreset = VulkanPresets.active
        ): VulkanTexture {
            val w = image.width
            val h = image.height
            val format = PresetFormat.RGBA8_UNORM

            val texture = preset.createTexture(
                { LumaNames.TEXTURE },
                5,
                format,
                w,
                h
            )
            var view: PresetGpuTextureView? = null
            try {
                view = preset.createTextureView(texture)
                uploads.enqueue(texture, image, 0, 0)
                return VulkanTexture(texture, view, sampler, w, h, uploads)
            } catch (t: Throwable) {
                view?.close()
                texture.close()
                throw t
            }
        }
    }
}
