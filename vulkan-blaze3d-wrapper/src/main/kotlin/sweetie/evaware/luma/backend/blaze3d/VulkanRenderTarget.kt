package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.AddressMode
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.GpuSampler
import sweetie.evaware.luma.LumaNames
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.api.RenderTargetFilter
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
        if (depthView != null || depthTexture != null) {
            VulkanResourceRetirement.defer {
                depthView?.close()
                depthTexture?.close()
            }
        }
    }

    companion object {
        private const val COLOR_USAGE = GpuTexture.USAGE_COPY_DST or
            GpuTexture.USAGE_RENDER_ATTACHMENT or GpuTexture.USAGE_TEXTURE_BINDING
        private const val DEPTH_USAGE = GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_COPY_SRC or
            GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_RENDER_ATTACHMENT

        fun create(
            width: Int,
            height: Int,
            useDepth: Boolean,
            format: RenderTargetFormat
        ): VulkanRenderTarget = create(width, height, useDepth, format, RenderTargetFilter.LINEAR)

        fun create(
            width: Int,
            height: Int,
            useDepth: Boolean,
            format: RenderTargetFormat,
            filter: RenderTargetFilter
        ): VulkanRenderTarget {
            val device = RenderSystem.getDevice()
            val gpuFormat = when (format) {
                RenderTargetFormat.RGBA8 -> GpuFormat.RGBA8_UNORM
                RenderTargetFormat.RGBA16F -> GpuFormat.RGBA16_FLOAT
            }

            var texture: GpuTexture? = null
            var textureView: GpuTextureView? = null
            var sampler: GpuSampler? = null
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
                sampler = device.createSampler(
                    AddressMode.CLAMP_TO_EDGE,
                    AddressMode.CLAMP_TO_EDGE,
                    filter.toGpuFilter(),
                    filter.toGpuFilter(),
                    1,
                    OptionalDouble.empty()
                )

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

                val colorTexture = VulkanTexture(texture, textureView, sampler, width, height)
                return VulkanRenderTarget(
                    texture,
                    textureView,
                    colorTexture,
                    depthTexture,
                    depthView,
                    width,
                    height
                )
            } catch (throwable: Throwable) {
                depthView?.close()
                depthTexture?.close()
                sampler?.close()
                textureView?.close()
                texture?.close()
                throw throwable
            }
        }

        private fun RenderTargetFilter.toGpuFilter(): FilterMode = when (this) {
            RenderTargetFilter.NEAREST -> FilterMode.NEAREST
            RenderTargetFilter.LINEAR -> FilterMode.LINEAR
        }
    }
}
