package sweetie.evaware.luma.backend.blaze3d

import sweetie.evaware.luma.LumaNames
import java.awt.image.DataBufferInt
import java.nio.ByteBuffer
import com.mojang.blaze3d.systems.GpuDevice

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.AddressMode
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.systems.RenderSystem
import org.lwjgl.system.MemoryUtil
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

    fun update(x: Int, y: Int, image: BufferedImage) {
        val w = image.width
        val h = image.height
        TextureUploadQueue.enqueue(gpuTexture, rgbaBuffer(image), x, y, w, h)
    }

    fun bind(unit: Int) {
    }

    override fun close() {
        view.close()
        gpuTexture.close()
        sampler.close()
    }

    companion object {
        private fun rgbaBuffer(image: BufferedImage): ByteBuffer {
            val w = image.width
            val h = image.height
            val pixels = (image.raster.dataBuffer as? DataBufferInt)?.data
                ?: image.getRGB(0, 0, w, h, null, 0, w)
            return MemoryUtil.memAlloc(w * h * 4).also { buffer ->
                for (p in pixels) {
                    buffer.put((p ushr 16 and 0xFF).toByte())
                    buffer.put((p ushr 8 and 0xFF).toByte())
                    buffer.put((p and 0xFF).toByte())
                    buffer.put((p ushr 24 and 0xFF).toByte())
                }
                buffer.flip()
            }
        }

        private fun uploadSync(
            device: GpuDevice,
            target: GpuTexture,
            buffer: ByteBuffer,
            x: Int, y: Int, w: Int, h: Int
        ) {
            val encoder = device.createCommandEncoder()
            encoder.writeToTexture(target, buffer, 0, 0, x, y, w, h)
            val fence = encoder.createFence()
            encoder.submit()
            fence.awaitCompletion(10000000000L)
            fence.close()
        }

        fun create(image: BufferedImage, mipmap: Boolean): VulkanTexture {
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

                val buffer = rgbaBuffer(image)
                try {
                    uploadSync(device, texture, buffer, 0, 0, w, h)
                } finally {
                    MemoryUtil.memFree(buffer)
                }

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
