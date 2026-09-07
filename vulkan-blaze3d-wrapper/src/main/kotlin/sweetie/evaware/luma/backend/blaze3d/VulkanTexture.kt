package sweetie.evaware.luma.backend.blaze3d

import java.awt.image.BufferedImage
import java.util.OptionalDouble
import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.AddressMode
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import sweetie.evaware.luma.LumaNames
import sweetie.evaware.luma.api.RenderTargetFilter
import sweetie.evaware.luma.api.TextureHandle

class VulkanTexture internal constructor(
    val gpuTexture: GpuTexture,
    val view: GpuTextureView,
    val sampler: GpuSampler,
    override val width: Int,
    override val height: Int,
    private val uploads: TextureUploadQueue?,
    private val ownsTexture: Boolean = true,
    private val ownsView: Boolean = true,
    private val ownsSampler: Boolean = false
) : TextureHandle {
    private var closed = false

    fun update(x: Int, y: Int, image: BufferedImage) {
        check(!closed) { "Texture is closed" }
        checkNotNull(uploads) { "Borrowed textures cannot be updated" }.enqueue(gpuTexture, image, x, y)
    }

    fun bind(unit: Int) {
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
        fun borrow(view: GpuTextureView, filter: RenderTargetFilter): VulkanTexture {
            val gpuFilter = when (filter) {
                RenderTargetFilter.NEAREST -> FilterMode.NEAREST
                RenderTargetFilter.LINEAR -> FilterMode.LINEAR
            }
            val sampler = RenderSystem.getDevice().createSampler(
                AddressMode.CLAMP_TO_EDGE,
                AddressMode.CLAMP_TO_EDGE,
                gpuFilter,
                gpuFilter,
                1,
                OptionalDouble.empty()
            )
            return VulkanTexture(
                view.texture(),
                view,
                sampler,
                view.getWidth(0),
                view.getHeight(0),
                null,
                false,
                false,
                true
            )
        }

        internal fun create(image: BufferedImage, sampler: GpuSampler, uploads: TextureUploadQueue): VulkanTexture {
            val device = RenderSystem.getDevice()
            val w = image.width
            val h = image.height
            val format = GpuFormat.RGBA8_UNORM

            var texture: GpuTexture? = null
            var view: GpuTextureView? = null
            try {
                texture = device.createTexture(
                    { LumaNames.TEXTURE },
                    GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_TEXTURE_BINDING,
                    format,
                    w,
                    h,
                    1,
                    1
                )
                view = device.createTextureView(texture)

                uploads.enqueue(texture, image, 0, 0)

                return VulkanTexture(texture, view, sampler, w, h, uploads)
            } catch (t: Throwable) {
                view?.close()
                texture?.close()
                throw t
            }
        }
    }
}
