package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.AddressMode
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.TextureHandle
import java.util.OptionalDouble
import java.util.function.Supplier

class VulkanRenderTarget(
    val gpuTexture: GpuTexture,
    val colorView: GpuTextureView,
    private val colorAsTexture: VulkanTexture,
    override val width: Int,
    override val height: Int
) : RenderTargetHandle {
    override val colorTexture: TextureHandle get() = colorAsTexture

    override fun close() {
        colorAsTexture.close()
    }

    companion object {
        fun create(width: Int, height: Int, format: RenderTargetFormat): VulkanRenderTarget {
            val device = RenderSystem.getDevice()
            val gpuFormat = when (format) {
                RenderTargetFormat.RGBA8 -> GpuFormat.RGBA8_UNORM
                RenderTargetFormat.RGBA16F -> GpuFormat.RGBA16_FLOAT
            }

            val texture = device.createTexture(
                Supplier { "luma_texture" },
                12,
                gpuFormat,
                width,
                height,
                1,
                1
            )
            val textureView = device.createTextureView(texture)
            val sampler = device.createSampler(
                AddressMode.CLAMP_TO_EDGE,
                AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR,
                FilterMode.LINEAR,
                1,
                OptionalDouble.empty()
            )

            val vulkanTexture = VulkanTexture(texture, textureView, sampler, width, height)
            return VulkanRenderTarget(texture, textureView, vulkanTexture, width, height)
        }
    }
}
