package sweetie.evaware.luma.backend.blaze3d

import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.api.TextureFormat
import sweetie.evaware.luma.backend.blaze3d.preset.PresetCommandEncoder
import sweetie.evaware.luma.backend.blaze3d.preset.PresetGpuTexture
import sweetie.evaware.luma.texture.RgbaTransferBuffer

internal class TextureUploadQueue(
    private val maxPooledBytes: Long,
    private val retirement: VulkanRetirementQueue
) : AutoCloseable {
    private class Pending {
        var texture: PresetGpuTexture? = null
        var buffer: ByteBuffer? = null
        var x = 0
        var y = 0
        var width = 0
        var height = 0

        fun set(texture: PresetGpuTexture, buffer: ByteBuffer, x: Int, y: Int, width: Int, height: Int): Pending {
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

    private class RetiredBuffers(
        private val owner: TextureUploadQueue
    ) : AutoCloseable {
        val buffers = ArrayList<ByteBuffer>()

        override fun close() {
            owner.recycle(this)
        }
    }

    private val pending = ArrayList<Pending>()
    private val recycledPending = ArrayDeque<Pending>()
    private val recycledBatches = ArrayDeque<RetiredBuffers>()
    private val available = ArrayList<ByteBuffer>()
    private val transfer = RgbaTransferBuffer()
    private var pooledBytes = 0L
    private var closed = false
    @Volatile
    private var pendingFlag = false

    @Synchronized
    fun enqueue(
        texture: PresetGpuTexture,
        image: BufferedImage,
        x: Int,
        y: Int,
        format: TextureFormat = TextureFormat.RGBA8
    ) {
        check(!closed) { "Texture upload queue is closed" }
        require(format.supportsBufferedImageUpload) {
            "BufferedImage upload is not supported for format $format; upload raw ByteBuffer data instead"
        }
        validateRegion(texture, x, y, image.width, image.height)
        val requiredBytes = format.byteSize(image.width, image.height)
        val buffer = acquire(requiredBytes)
        try {
            pending += obtainPending().set(
                texture = texture,
                buffer = transfer.write(image, buffer, format),
                x = x,
                y = y,
                width = image.width,
                height = image.height
            )
            pendingFlag = true
        } catch (failure: Throwable) {
            recycle(buffer)
            throw failure
        }
    }

    @Synchronized
    fun enqueueRaw(
        texture: PresetGpuTexture,
        data: ByteBuffer,
        format: TextureFormat,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) {
        check(!closed) { "Texture upload queue is closed" }
        validateRegion(texture, x, y, width, height)
        val requiredBytes = format.byteSize(width, height)
        require(data.remaining() >= requiredBytes) {
            "Texture data has ${data.remaining()} bytes, expected at least $requiredBytes bytes for $format ${width}x${height}"
        }
        val buffer = acquire(requiredBytes)
        try {
            buffer.clear()
            if (data.isDirect) {
                MemoryUtil.memCopy(
                    MemoryUtil.memAddress(data),
                    MemoryUtil.memAddress(buffer),
                    requiredBytes.toLong()
                )
                buffer.limit(requiredBytes)
            } else {
                val slice = data.duplicate()
                slice.limit(slice.position() + requiredBytes)
                buffer.put(slice)
                buffer.flip()
            }
            pending += obtainPending().set(
                texture = texture,
                buffer = buffer,
                x = x,
                y = y,
                width = width,
                height = height
            )
            pendingFlag = true
        } catch (failure: Throwable) {
            recycle(buffer)
            throw failure
        }
    }

    private fun validateRegion(texture: PresetGpuTexture, x: Int, y: Int, width: Int, height: Int) {
        require(x >= 0 && y >= 0 && width > 0 && height > 0) {
            "Texture update region must have non-negative origin and positive size"
        }
        require(x.toLong() + width <= texture.width && y.toLong() + height <= texture.height) {
            "Texture update region [$x, $y, ${width}x${height}] exceeds texture [${texture.width}x${texture.height}]"
        }
    }

    fun hasPending(): Boolean = pendingFlag

    @Synchronized
    fun record(encoder: PresetCommandEncoder) {
        if (pending.isEmpty()) {
            pendingFlag = false
            return
        }
        val batch = if (recycledBatches.isEmpty()) RetiredBuffers(this) else recycledBatches.removeFirst()
        batch.buffers.ensureCapacity(pending.size)
        for (index in pending.indices) {
            val upload = pending[index]
            write(
                encoder,
                requireNotNull(upload.texture),
                requireNotNull(upload.buffer),
                upload.x,
                upload.y,
                upload.width,
                upload.height
            )
            batch.buffers.add(requireNotNull(upload.buffer))
            upload.reset()
            recycledPending.addLast(upload)
        }
        pending.clear()
        pendingFlag = false
        retirement.defer(batch)
    }

    @Synchronized
    override fun close() {
        closed = true
        pending.forEach {
            MemoryUtil.memFree(requireNotNull(it.buffer))
            it.reset()
        }
        pending.clear()
        pendingFlag = false
        recycledPending.clear()
        recycledBatches.clear()
        available.forEach(MemoryUtil::memFree)
        available.clear()
        pooledBytes = 0
        transfer.close()
    }

    private fun write(
        encoder: PresetCommandEncoder,
        texture: PresetGpuTexture,
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

    @Synchronized
    private fun recycle(batch: RetiredBuffers) {
        for (index in batch.buffers.indices) recycle(batch.buffers[index])
        batch.buffers.clear()
        if (!closed) recycledBatches.addLast(batch)
    }
}
