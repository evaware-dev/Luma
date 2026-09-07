package sweetie.evaware.luma.backend.blaze3d

import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.mojang.blaze3d.systems.CommandEncoder
import com.mojang.blaze3d.textures.GpuTexture
import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.texture.RgbaTransferBuffer

internal class TextureUploadQueue(
    private val maxPooledBytes: Long
) : AutoCloseable {
    private class Pending {
        var texture: GpuTexture? = null
        var buffer: ByteBuffer? = null
        var x = 0
        var y = 0
        var width = 0
        var height = 0

        fun set(texture: GpuTexture, buffer: ByteBuffer, x: Int, y: Int, width: Int, height: Int): Pending {
            this.texture = texture
            this.buffer = buffer
            this.x = x
            this.y = y
            this.width = width
            this.height = height
            return this
        }

        fun reset() {
            texture = null
            buffer = null
            x = 0
            y = 0
            width = 0
            height = 0
        }
    }

    private val pending = ArrayList<Pending>()
    private val recycledPending = ArrayDeque<Pending>()
    private val available = ArrayList<ByteBuffer>()
    private val transfer = RgbaTransferBuffer()
    private var pooledBytes = 0L
    private var closed = false

    @Synchronized
    fun enqueue(texture: GpuTexture, image: BufferedImage, x: Int, y: Int) {
        check(!closed) { "Texture upload queue is closed" }
        val buffer = acquire(Math.multiplyExact(Math.multiplyExact(image.width, image.height), 4))
        pending += obtainPending().set(
            texture = texture,
            buffer = transfer.write(image, buffer),
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
        if (pending.isEmpty()) return
        val recycledBuffers = ArrayList<ByteBuffer>(pending.size)
        pending.forEach { upload ->
            write(
                encoder,
                requireNotNull(upload.texture),
                requireNotNull(upload.buffer),
                upload.x,
                upload.y,
                upload.width,
                upload.height
            )
            recycledBuffers += requireNotNull(upload.buffer)
            upload.reset()
            recycledPending += upload
        }
        pending.clear()
        VulkanResourceRetirement.defer {
            synchronized(this) {
                recycledBuffers.forEach(::recycle)
            }
        }
    }

    @Synchronized
    override fun close() {
        closed = true
        pending.forEach {
            MemoryUtil.memFree(requireNotNull(it.buffer))
            it.reset()
        }
        pending.clear()
        recycledPending.clear()
        available.forEach(MemoryUtil::memFree)
        available.clear()
        pooledBytes = 0
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

    @Synchronized
    private fun acquire(requiredBytes: Int): ByteBuffer {
        val index = available.indexOfFirst { it.capacity() >= requiredBytes }
        if (index >= 0) {
            return available.removeAt(index).also {
                pooledBytes -= it.capacity().toLong()
                it.clear()
                it.limit(requiredBytes)
            }
        }
        return MemoryUtil.memAlloc(requiredBytes).order(ByteOrder.nativeOrder())
    }

    private fun obtainPending(): Pending {
        return if (recycledPending.isEmpty()) Pending() else recycledPending.removeFirst()
    }

    private fun recycle(buffer: ByteBuffer) {
        if (closed) {
            MemoryUtil.memFree(buffer)
            return
        }
        buffer.clear()
        if (pooledBytes + buffer.capacity() <= maxPooledBytes) {
            available += buffer
            pooledBytes += buffer.capacity().toLong()
        } else {
            MemoryUtil.memFree(buffer)
        }
    }
}
