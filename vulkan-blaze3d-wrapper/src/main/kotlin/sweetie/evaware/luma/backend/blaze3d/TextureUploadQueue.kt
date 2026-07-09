package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.systems.CommandEncoder
import com.mojang.blaze3d.textures.GpuTexture
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

internal object TextureUploadQueue {
    private class Pending(
        val texture: GpuTexture,
        val buffer: ByteBuffer,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    )

    private val pending = ArrayList<Pending>()
    private val inFlight = ArrayList<ByteBuffer>()

    @Synchronized
    fun enqueue(texture: GpuTexture, buffer: ByteBuffer, x: Int, y: Int, width: Int, height: Int) {
        pending.add(Pending(texture, buffer, x, y, width, height))
    }

    @Synchronized
    fun hasPending(): Boolean = pending.isNotEmpty()

    @Synchronized
    fun record(encoder: CommandEncoder) {
        if (pending.isEmpty()) return
        for (upload in pending) {
            encoder.writeToTexture(
                upload.texture,
                upload.buffer,
                0,
                0,
                upload.x,
                upload.y,
                upload.width,
                upload.height
            )
            inFlight.add(upload.buffer)
        }
        pending.clear()
    }

    @Synchronized
    fun freeSubmitted() {
        if (inFlight.isEmpty()) return
        for (buffer in inFlight) {
            MemoryUtil.memFree(buffer)
        }
        inFlight.clear()
    }
}
