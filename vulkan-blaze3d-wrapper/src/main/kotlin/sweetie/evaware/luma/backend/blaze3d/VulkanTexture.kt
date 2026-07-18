package sweetie.evaware.luma.backend.blaze3d

import sweetie.evaware.luma.LumaNames

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.AddressMode
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.systems.RenderSystem
import sweetie.evaware.luma.api.TextureHandle
import java.awt.image.BufferedImage
import java.util.OptionalDouble

class VulkanTexture(
    val gpuTexture: GpuTexture,
    val view: GpuTextureView,
    val sampler: GpuSampler,
    override val width: Int,
    override val height: Int
) : TextureHandle {
    private var closed = false

    fun update(x: Int, y: Int, image: BufferedImage) {
        check(!closed) { "Texture is closed" }
        TextureUploadQueue.enqueue(gpuTexture, image, x, y)
    }

    fun bind(unit: Int) {
    }

    override fun close() {
        if (closed) return
        closed = true
        VulkanResourceRetirement.defer {
            view.close()
            gpuTexture.close()
            sampler.close()
        }
    }

    companion object {
        fun create(image: BufferedImage): VulkanTexture {
            val device = RenderSystem.getDevice()
            val w = image.width
            val h = image.height
            val format = GpuFormat.RGBA8_UNORM

            var texture: GpuTexture? = null
            var view: GpuTextureView? = null
            var sampler: GpuSampler? = null
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

                TextureUploadQueue.uploadNow(device, texture, image)

                sampler = device.createSampler(
                    AddressMode.CLAMP_TO_EDGE,
                    AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR,
                    FilterMode.LINEAR,
                    1,
                    OptionalDouble.empty()
                )

                return VulkanTexture(texture, view, sampler, w, h)
            } catch (t: Throwable) {
                view?.close()
                texture?.close()
                sampler?.close()
                throw t
            }
        }
    }
}
