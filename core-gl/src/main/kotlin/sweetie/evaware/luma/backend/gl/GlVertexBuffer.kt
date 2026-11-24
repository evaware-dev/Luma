package sweetie.evaware.luma.backend.gl

import java.nio.FloatBuffer
import org.lwjgl.opengl.ARBBaseInstance
import org.lwjgl.opengl.ARBBufferStorage
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL31
import org.lwjgl.opengl.GL32
import org.lwjgl.opengl.GL33
import org.lwjgl.opengl.GL42
import org.lwjgl.opengl.GL44
import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.vertex.VertexLayout

class GlVertexBuffer(val layout: VertexLayout) : AutoCloseable {
    val vao = GL30.glGenVertexArrays()
    var vbo = GL15.glGenBuffers()
        private set
    var capacityFloats = 0
        private set

    private val caps = GL.getCapabilities()
    private val useGl44Storage = caps.OpenGL44
    private val supportsPersistent = useGl44Storage || caps.GL_ARB_buffer_storage
    private val useGl42BaseInstance = caps.OpenGL42
    internal val supportsBaseInstance = useGl42BaseInstance || caps.GL_ARB_base_instance
    private val strideBytes = (layout.strideFloats * Float.SIZE_BYTES).coerceAtLeast(Float.SIZE_BYTES)
    private val canUseRingOffsets = !layout.instanced || supportsBaseInstance

    private var persistentMapped = false
    private var persistentDisabled = false
    private var mappedAddress = 0L
    private var regionBytes = 0
    private var currentRegion = 0
    private var regionWriteOffsetBytes = 0
    private val regionFences = LongArray(RING_REGIONS)

    private var quadIndexBuffer = 0
    private var quadIndexCapacity = 0

    init {
        val previousVertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
        val previousArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
        try {
            GL30.glBindVertexArray(vao)
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo)
            setupVertexAttributes(layout)
        } finally {
            GL30.glBindVertexArray(previousVertexArray)
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousArrayBuffer)
        }
    }

    fun bind() {
        GL30.glBindVertexArray(vao)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo)
    }

    fun upload(vertices: FloatBuffer) {
        uploadStreaming(vertices) {}
    }

    internal fun canAppendContiguously(floatCount: Int): Boolean {
        if (floatCount <= 0) return true
        if (regionBytes == 0) return false
        val alignedBytes = alignToStride(floatCount * Float.SIZE_BYTES)
        if (!canUseRingOffsets && regionWriteOffsetBytes > 0) return false
        return regionWriteOffsetBytes + alignedBytes <= regionBytes
    }

    internal inline fun uploadStreaming(
        vertices: FloatBuffer,
        onArrayBufferBound: (Int) -> Unit
    ): Int {
        val floatCount = vertices.limit()
        if (floatCount <= 0) return 0
        val bytes = floatCount * Float.SIZE_BYTES
        val alignedBytes = alignToStride(bytes)

        if (supportsPersistent && canUseRingOffsets && !persistentDisabled) {
            if (!persistentMapped || alignedBytes > regionBytes) {
                if (allocatePersistentRing(alignedBytes, onArrayBufferBound)) {
                    // Ready in persistentMapped state at region 0, offset 0
                } else {
                    persistentDisabled = true
                }
            }
            if (persistentMapped) {
                if (regionWriteOffsetBytes + alignedBytes > regionBytes) {
                    advancePersistentRegion()
                }
                val absoluteOffsetBytes = currentRegion.toLong() * regionBytes + regionWriteOffsetBytes
                copyFloatsToAddress(vertices, floatCount, mappedAddress + absoluteOffsetBytes)
                regionWriteOffsetBytes += alignedBytes
                return (absoluteOffsetBytes / strideBytes).toInt()
            }
        }

        return uploadFallbackRing(vertices, floatCount, bytes, alignedBytes, onArrayBufferBound)
    }

    internal fun drawInstancedBaseInstance(baseVertexCount: Int, instanceCount: Int, baseInstance: Int) {
        if (baseInstance == 0) {
            GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, baseVertexCount, instanceCount)
        } else if (useGl42BaseInstance) {
            GL42.glDrawArraysInstancedBaseInstance(
                GL11.GL_TRIANGLES,
                0,
                baseVertexCount,
                instanceCount,
                baseInstance
            )
        } else {
            ARBBaseInstance.glDrawArraysInstancedBaseInstance(
                GL11.GL_TRIANGLES,
                0,
                baseVertexCount,
                instanceCount,
                baseInstance
            )
        }
    }

    private inline fun allocatePersistentRing(
        requiredBytes: Int,
        onArrayBufferBound: (Int) -> Unit
    ): Boolean {
        val targetRegionBytes = computeRegionBytes(requiredBytes)
        val totalBytes = targetRegionBytes.toLong() * RING_REGIONS
        val flags = GL30.GL_MAP_WRITE_BIT or GL44.GL_MAP_PERSISTENT_BIT or GL44.GL_MAP_COHERENT_BIT

        val targetVbo = if (persistentMapped) GL15.glGenBuffers() else vbo
        onArrayBufferBound(targetVbo)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, targetVbo)

        if (useGl44Storage) {
            GL44.glBufferStorage(GL15.GL_ARRAY_BUFFER, totalBytes, flags)
        } else {
            ARBBufferStorage.glBufferStorage(GL15.GL_ARRAY_BUFFER, totalBytes, flags)
        }
        val mapped = GL30.glMapBufferRange(GL15.GL_ARRAY_BUFFER, 0L, totalBytes, flags)
        if (mapped == null) {
            if (persistentMapped) {
                GL15.glDeleteBuffers(targetVbo)
                onArrayBufferBound(vbo)
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo)
            } else {
                // Buffer storage is immutable; replace with a fresh mutable VBO for the fallback path
                val replacementVbo = GL15.glGenBuffers()
                onArrayBufferBound(replacementVbo)
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, replacementVbo)
                setupVertexAttributes(layout)
                GL15.glDeleteBuffers(vbo)
                vbo = replacementVbo
            }
            return false
        }

        if (persistentMapped) {
            deleteAllFences()
            setupVertexAttributes(layout)
            val previousVbo = vbo
            vbo = targetVbo
            GL15.glDeleteBuffers(previousVbo)
        }

        mappedAddress = MemoryUtil.memAddress(mapped)
        regionBytes = targetRegionBytes
        capacityFloats = (totalBytes / Float.SIZE_BYTES).toInt()
        currentRegion = 0
        regionWriteOffsetBytes = 0
        persistentMapped = true
        return true
    }

    private fun advancePersistentRegion() {
        val oldFence = regionFences[currentRegion]
        if (oldFence != 0L) GL32.glDeleteSync(oldFence)
        regionFences[currentRegion] = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
        currentRegion = (currentRegion + 1) % RING_REGIONS
        waitAndDeleteFence(currentRegion)
        regionWriteOffsetBytes = 0
    }

    private fun waitAndDeleteFence(region: Int) {
        val fence = regionFences[region]
        if (fence == 0L) return
        var status = GL32.glClientWaitSync(fence, 0, 0L)
        while (status != GL32.GL_ALREADY_SIGNALED && status != GL32.GL_CONDITION_SATISFIED) {
            if (status == GL32.GL_WAIT_FAILED) break
            status = GL32.glClientWaitSync(fence, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, 1_000_000L)
        }
        GL32.glDeleteSync(fence)
        regionFences[region] = 0L
    }

    private fun deleteAllFences() {
        for (i in regionFences.indices) {
            val fence = regionFences[i]
            if (fence != 0L) {
                GL32.glDeleteSync(fence)
                regionFences[i] = 0L
            }
        }
    }

    private inline fun uploadFallbackRing(
        vertices: FloatBuffer,
        floatCount: Int,
        bytes: Int,
        alignedBytes: Int,
        onArrayBufferBound: (Int) -> Unit
    ): Int {
        onArrayBufferBound(vbo)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo)

        if (regionBytes == 0 || alignedBytes > regionBytes) {
            regionBytes = computeRegionBytes(alignedBytes)
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, regionBytes.toLong(), GL15.GL_STREAM_DRAW)
            capacityFloats = regionBytes / Float.SIZE_BYTES
            regionWriteOffsetBytes = 0
        } else if (!canUseRingOffsets || regionWriteOffsetBytes + alignedBytes > regionBytes) {
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, regionBytes.toLong(), GL15.GL_STREAM_DRAW)
            regionWriteOffsetBytes = 0
        }

        val writeOffset = regionWriteOffsetBytes
        val mapped = GL30.glMapBufferRange(
            GL15.GL_ARRAY_BUFFER,
            writeOffset.toLong(),
            bytes.toLong(),
            GL30.GL_MAP_WRITE_BIT or GL30.GL_MAP_UNSYNCHRONIZED_BIT
        )
        if (mapped != null) {
            copyFloatsToAddress(vertices, floatCount, MemoryUtil.memAddress(mapped))
            GL15.glUnmapBuffer(GL15.GL_ARRAY_BUFFER)
        } else {
            vertices.position(0)
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, writeOffset.toLong(), vertices)
        }
        regionWriteOffsetBytes = writeOffset + alignedBytes
        return writeOffset / strideBytes
    }

    private fun copyFloatsToAddress(vertices: FloatBuffer, floatCount: Int, dstAddress: Long) {
        if (vertices.isDirect) {
            MemoryUtil.memCopy(MemoryUtil.memAddress(vertices, 0), dstAddress, floatCount.toLong() * Float.SIZE_BYTES)
        } else {
            var dst = dstAddress
            for (i in 0 until floatCount) {
                MemoryUtil.memPutFloat(dst, vertices.get(i))
                dst += Float.SIZE_BYTES
            }
        }
    }

    private fun alignToStride(bytes: Int): Int {
        val remainder = bytes % strideBytes
        return if (remainder == 0) bytes else Math.addExact(bytes, strideBytes - remainder)
    }

    private fun computeRegionBytes(requiredBytes: Int): Int {
        var cap = maxOf(regionBytes, INITIAL_REGION_BYTES)
        while (cap < requiredBytes) {
            cap = Math.multiplyExact(cap, 2)
        }
        return alignToStride(cap)
    }

    fun bindQuadIndices(vertexCount: Int): Int {
        val quadCount = vertexCount / 4
        val requiredIndices = quadCount * 6

        if (quadIndexBuffer == 0) {
            quadIndexBuffer = GL15.glGenBuffers()
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, quadIndexBuffer)
        }

        if (requiredIndices > quadIndexCapacity) {
            quadIndexCapacity = nextIndexCapacity(requiredIndices)
            val quads = quadIndexCapacity / 6
            val indices = MemoryUtil.memAllocInt(quads * 6)
            try {
                for (quad in 0 until quads) {
                    val base = quad * 4
                    indices.put(base).put(base + 1).put(base + 2)
                    indices.put(base + 2).put(base + 3).put(base)
                }
                indices.flip()
                GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices, GL15.GL_STATIC_DRAW)
            } finally {
                MemoryUtil.memFree(indices)
            }
        }

        return requiredIndices
    }

    private fun setupVertexAttributes(layout: VertexLayout) {
        var offset = 0L
        val stride = layout.strideFloats * Float.SIZE_BYTES
        for (index in 0 until layout.size()) {
            val layoutPos = layout.layoutPos(index)
            GL20.glEnableVertexAttribArray(layoutPos)
            GL20.glVertexAttribPointer(
                layoutPos,
                layout.count(index),
                layout.type(index).glType,
                layout.normalized(index),
                stride,
                offset
            )
            if (layout.instanced) {
                GL33.glVertexAttribDivisor(layoutPos, 1)
            }
            offset += layout.byteSize(index).toLong()
        }
    }

    private fun nextIndexCapacity(required: Int): Int {
        var cap = quadIndexCapacity.coerceAtLeast(6 * 256)
        while (cap < required) {
            cap = cap shl 1
        }
        return cap
    }

    override fun close() {
        deleteAllFences()
        persistentMapped = false
        mappedAddress = 0L
        GL15.glDeleteBuffers(vbo)
        if (quadIndexBuffer != 0) {
            GL15.glDeleteBuffers(quadIndexBuffer)
        }
        GL30.glDeleteVertexArrays(vao)
    }

    private companion object {
        const val RING_REGIONS = 3
        const val INITIAL_REGION_BYTES = 128 * 1024
    }
}
