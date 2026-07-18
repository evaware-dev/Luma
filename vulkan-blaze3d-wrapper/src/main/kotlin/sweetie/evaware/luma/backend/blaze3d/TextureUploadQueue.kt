package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.systems.CommandEncoder
import com.mojang.blaze3d.systems.GpuDevice
import com.mojang.blaze3d.textures.GpuTexture
import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.texture.RgbaTransferBuffer
import java.awt.image.BufferedImage
import java.nio.ByteBuffer

internal object TextureUploadQueue : AutoCloseable {
    private class Pending(
        val texture: GpuTexture,
        val buffer: ByteBuffer,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    )

    private val pending = ArrayList<Pending>()
    private val transfer = RgbaTransferBuffer()

    @Synchronized
    fun enqueue(texture: GpuTexture, image: BufferedImage, x: Int, y: Int) {
        pending += Pending(
            texture = texture,
            buffer = snapshot(image),
            x = x,
            y = y,
            width = image.width,
            height = image.height
        )
    }

    @Synchronized
    fun hasPending(): Boolean = pending.isNotEmpty()

    @Synchronized
    fun record(encoder: CommandEncoder) {
        pending.forEach { upload ->
            write(
                encoder,
                upload.texture,
                upload.buffer,
                upload.x,
                upload.y,
                upload.width,
                upload.height
            )
            VulkanResourceRetirement.defer { MemoryUtil.memFree(upload.buffer) }
        }
        pending.clear()
    }

    @Synchronized
    fun uploadNow(
        device: GpuDevice,
        texture: GpuTexture,
        image: BufferedImage,
        x: Int = 0,
        y: Int = 0
    ) {
        val encoder = device.createCommandEncoder()
        write(encoder, texture, transfer.write(image), x, y, image.width, image.height)
        val fence = encoder.createFence()
        encoder.submit()
        try {
            check(fence.awaitCompletion(UPLOAD_TIMEOUT_NANOS)) { "Timed out while uploading texture" }
        } finally {
            fence.close()
        }
    }

    @Synchronized
    override fun close() {
        pending.forEach { MemoryUtil.memFree(it.buffer) }
        pending.clear()
        transfer.close()
    }

    private fun write(
        encoder: CommandEncoder,
        texture: GpuTexture,
        buffer: ByteBuffer,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) {
        encoder.writeToTexture(
            texture,
            buffer,
            0,
            0,
            x,
            y,
            width,
            height
        )
    }

    private fun snapshot(image: BufferedImage): ByteBuffer {
        val source = transfer.write(image)
        return MemoryUtil.memAlloc(source.remaining()).also { target ->
            target.put(source.duplicate()).flip()
        }
    }

    private const val UPLOAD_TIMEOUT_NANOS = 10_000_000_000L
}
