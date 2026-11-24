package sweetie.evaware.luma.backend.blaze3d

import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.LumaNames
import sweetie.evaware.luma.api.RenderTargetFilter
import sweetie.evaware.luma.api.TextureFormat
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
    private val ownsSampler: Boolean = false,
    private var retirement: VulkanRetirementQueue? = null,
    override val format: TextureFormat = TextureFormat.RGBA8
) : TextureHandle {
    private var closed = false

    internal fun requireOpen() = check(!closed) { "Texture is closed" }

    internal fun attachRetirement(queue: VulkanRetirementQueue) {
        if (retirement == null) {
            retirement = queue
        }
    }

    fun update(x: Int, y: Int, image: BufferedImage) {
        requireOpen()
        checkNotNull(uploads) { "Borrowed textures cannot be updated" }.enqueue(gpuTexture, image, x, y, format)
    }

    fun update(x: Int, y: Int, width: Int, height: Int, data: ByteBuffer) {
        requireOpen()
        checkNotNull(uploads) { "Borrowed textures cannot be updated" }.enqueueRaw(
            gpuTexture,
            data,
            format,
            x,
            y,
            width,
            height
        )
    }

    override fun close() {
        if (closed) return
        closed = true
        if (!ownsSampler && !ownsView && !ownsTexture) return
        val queue = retirement
        if (queue != null) {
            if (ownsSampler) queue.defer(sampler)
            if (ownsView) queue.defer(view)
            if (ownsTexture) queue.defer(gpuTexture)
        } else {
            var failure: Throwable? = null
            if (ownsSampler) {
                try {
                    sampler.close()
                } catch (throwable: Throwable) {
                    failure = throwable
                }
            }
            if (ownsView) {
                try {
                    view.close()
                } catch (throwable: Throwable) {
                    if (failure == null) failure = throwable else failure.addSuppressed(throwable)
                }
            }
            if (ownsTexture) {
                try {
                    gpuTexture.close()
                } catch (throwable: Throwable) {
                    if (failure == null) failure = throwable else failure.addSuppressed(throwable)
                }
            }
            if (failure != null) throw failure
        }
    }

    companion object {
        internal fun TextureFormat.toPresetFormat(): PresetFormat = when (this) {
            TextureFormat.RGBA8 -> PresetFormat.RGBA8_UNORM
            TextureFormat.RG8 -> PresetFormat.RG8_UNORM
            TextureFormat.R8 -> PresetFormat.R8_UNORM
            TextureFormat.RGBA16F -> PresetFormat.RGBA16_FLOAT
        }

        internal fun activeBackendRetirement(preset: VulkanPreset): VulkanRetirementQueue? {
            if (!Luma.isBackendInitialized) return null
            val active = Luma.backend as? Backend ?: return null
            return if (active.preset === preset) active.retirement else null
        }

        fun borrow(
            view: Any,
            filter: RenderTargetFilter = RenderTargetFilter.NEAREST,
            preset: VulkanPreset = VulkanPresets.active
        ): VulkanTexture = borrow(view, filter, preset, activeBackendRetirement(preset))

        internal fun borrow(
            view: Any,
            filter: RenderTargetFilter,
            preset: VulkanPreset,
            retirement: VulkanRetirementQueue?
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
                true,
                retirement
            )
        }

        internal fun create(
            image: BufferedImage,
            sampler: PresetGpuSampler,
            uploads: TextureUploadQueue,
            preset: VulkanPreset,
            retirement: VulkanRetirementQueue,
            format: TextureFormat = TextureFormat.RGBA8
        ): VulkanTexture {
            require(format.supportsBufferedImageUpload) {
                "BufferedImage upload is not supported for format $format; upload raw ByteBuffer data instead"
            }
            val w = image.width
            val h = image.height
            val texture = preset.createTexture(
                { LumaNames.TEXTURE },
                5,
                format.toPresetFormat(),
                w,
                h
            )
            var view: PresetGpuTextureView? = null
            try {
                view = preset.createTextureView(texture)
                uploads.enqueue(texture, image, 0, 0, format)
                return VulkanTexture(texture, view, sampler, w, h, uploads, retirement = retirement, format = format)
            } catch (t: Throwable) {
                view?.close()
                texture.close()
                throw t
            }
        }

        internal fun createRaw(
            width: Int,
            height: Int,
            data: ByteBuffer?,
            format: TextureFormat,
            sampler: PresetGpuSampler,
            uploads: TextureUploadQueue,
            preset: VulkanPreset,
            retirement: VulkanRetirementQueue
        ): VulkanTexture {
            require(width > 0 && height > 0) { "Texture dimensions must be positive: ${width}x${height}" }
            if (data != null) {
                val requiredBytes = format.byteSize(width, height)
                require(data.remaining() >= requiredBytes) {
                    "Texture data has ${data.remaining()} bytes, expected at least $requiredBytes bytes for $format ${width}x${height}"
                }
            }
            val texture = preset.createTexture(
                { LumaNames.TEXTURE },
                5,
                format.toPresetFormat(),
                width,
                height
            )
            var view: PresetGpuTextureView? = null
            try {
                view = preset.createTextureView(texture)
                if (data != null) {
                    uploads.enqueueRaw(texture, data, format, 0, 0, width, height)
                }
                return VulkanTexture(texture, view, sampler, width, height, uploads, retirement = retirement, format = format)
            } catch (t: Throwable) {
                view?.close()
                texture.close()
                throw t
            }
        }
    }
}
