package sweetie.evaware.luma.backend.blaze3d

import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.LumaNames
import sweetie.evaware.luma.api.SamplerState
import sweetie.evaware.luma.api.TextureFormat
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.backend.blaze3d.VulkanSamplerCache.Companion.toPreset
import sweetie.evaware.luma.backend.blaze3d.preset.PresetFormat
import sweetie.evaware.luma.backend.blaze3d.preset.PresetGpuSampler
import sweetie.evaware.luma.backend.blaze3d.preset.PresetGpuTexture
import sweetie.evaware.luma.backend.blaze3d.preset.PresetGpuTextureView
import sweetie.evaware.luma.backend.blaze3d.preset.VulkanPreset
import sweetie.evaware.luma.backend.blaze3d.preset.VulkanPresets

class VulkanTexture internal constructor(
    val gpuTexture: PresetGpuTexture,
    val view: PresetGpuTextureView,
    sampler: PresetGpuSampler,
    override val width: Int,
    override val height: Int,
    private val uploads: TextureUploadQueue?,
    private val ownsTexture: Boolean = true,
    private val ownsView: Boolean = true,
    private var ownsSampler: Boolean = false,
    private var retirement: VulkanRetirementQueue? = null,
    override val format: TextureFormat = TextureFormat.RGBA8,
    internal var samplerState: SamplerState = SamplerState.LINEAR_CLAMP
) : TextureHandle {
    var sampler: PresetGpuSampler = sampler
        internal set

    private var closed = false

    internal fun requireOpen() = check(!closed) { "Texture is closed" }

    internal fun attachRetirement(queue: VulkanRetirementQueue) {
        if (retirement == null) {
            retirement = queue
        }
    }

    internal fun replaceSampler(
        newSampler: PresetGpuSampler,
        newState: SamplerState,
        newOwnsSampler: Boolean
    ) {
        val oldSampler = sampler
        val oldOwned = ownsSampler
        sampler = newSampler
        samplerState = newState
        ownsSampler = newOwnsSampler
        if (oldOwned && oldSampler !== newSampler) {
            val queue = retirement
            if (queue != null) queue.defer(oldSampler) else oldSampler.close()
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
            sampler: SamplerState = SamplerState.NEAREST_CLAMP,
            preset: VulkanPreset = VulkanPresets.active
        ): VulkanTexture = borrow(view, sampler, preset, activeBackendRetirement(preset))

        internal fun borrow(
            view: Any,
            sampler: SamplerState,
            preset: VulkanPreset,
            retirement: VulkanRetirementQueue?
        ): VulkanTexture {
            val wrapped = preset.wrapTextureView(view)
            val gpuSampler = preset.createSampler(
                addressU = sampler.wrapU.toPreset(),
                addressV = sampler.wrapV.toPreset(),
                minFilter = sampler.minFilter.toPreset(),
                magFilter = sampler.magFilter.toPreset(),
                maxAnisotropy = sampler.maxAnisotropy,
                maxLod = sampler.maxLod
            )
            return VulkanTexture(
                gpuTexture = wrapped.texture,
                view = wrapped,
                sampler = gpuSampler,
                width = wrapped.getWidth(0),
                height = wrapped.getHeight(0),
                uploads = null,
                ownsTexture = false,
                ownsView = false,
                ownsSampler = true,
                retirement = retirement,
                format = TextureFormat.RGBA8,
                samplerState = sampler
            )
        }

        internal fun create(
            image: BufferedImage,
            sampler: PresetGpuSampler,
            samplerState: SamplerState,
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
                return VulkanTexture(
                    texture,
                    view,
                    sampler,
                    w,
                    h,
                    uploads,
                    retirement = retirement,
                    format = format,
                    samplerState = samplerState
                )
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
            samplerState: SamplerState,
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
                return VulkanTexture(
                    texture,
                    view,
                    sampler,
                    width,
                    height,
                    uploads,
                    retirement = retirement,
                    format = format,
                    samplerState = samplerState
                )
            } catch (t: Throwable) {
                view?.close()
                texture.close()
                throw t
            }
        }
    }
}
