package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.AddressMode
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import sweetie.evaware.luma.LumaNames
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.TextureHandle
import java.util.OptionalDouble
import java.util.function.Supplier

class VulkanRenderTarget(
    val gpuTexture: GpuTexture,
    val colorView: GpuTextureView,
    private val colorAsTexture: VulkanTexture,
    val depthTexture: GpuTexture?,
    val depthView: GpuTextureView?,
    override val width: Int,
    override val height: Int
) : RenderTargetHandle {
    override val colorTexture: TextureHandle get() = colorAsTexture

    override fun close() {
        colorAsTexture.close()
        depthView?.close()
        depthTexture?.close()
    }

    companion object {
        private const val COLOR_USAGE = GpuTexture.USAGE_RENDER_ATTACHMENT or GpuTexture.USAGE_TEXTURE_BINDING
        private const val DEPTH_USAGE = GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_COPY_SRC or
            GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_RENDER_ATTACHMENT

        fun create(width: Int, height: Int, useDepth: Boolean, format: RenderTargetFormat): VulkanRenderTarget {
            val device = RenderSystem.getDevice()
            val gpuFormat = when (format) {
                RenderTargetFormat.RGBA8 -> GpuFormat.RGBA8_UNORM
                RenderTargetFormat.RGBA16F -> GpuFormat.RGBA16_FLOAT
            }

            val texture = device.createTexture(
                Supplier { LumaNames.TEXTURE },
                COLOR_USAGE,
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

            var depthTexture: GpuTexture? = null
            var depthView: GpuTextureView? = null
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

            val vulkanTexture = VulkanTexture(texture, textureView, sampler, width, height)
            return VulkanRenderTarget(texture, textureView, vulkanTexture, depthTexture, depthView, width, height)
        }
    }
}
