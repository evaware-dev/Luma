package sweetie.evaware.luma.backend.gl

import java.lang.foreign.MemorySegment
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
import sweetie.evaware.luma.vertex.VertexCursor
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
    internal val strideBytes = (layout.strideFloats * Float.SIZE_BYTES).coerceAtLeast(Float.SIZE_BYTES)
    private val canUseRingOffsets = !layout.instanced || supportsBaseInstance

    private var persistentMapped = false
    private var persistentDisabled = false
    private var mappedAddress = 0L
    private var mappedSegment: MemorySegment? = null
    private var regionBytes = 0
    private var currentRegion = 0
    private var regionWriteOffsetBytes = 0
    private val regionFences = LongArray(RING_REGIONS)

    // Reusable off-heap scratch buffer used only when persistent coherent mapping is unavailable
    private var fallbackBuffer: ByteBuffer? = null
    private var fallbackSegment: MemorySegment? = null
    private var fallbackAddress = 0L
    private var fallbackCapacity = 0

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

    internal fun canAppendContiguouslyBytes(bytes: Int): Boolean {
        if (bytes <= 0) return true
        if (regionBytes == 0) return false
        if (!canUseRingOffsets && regionWriteOffsetBytes > 0) return false
        return regionWriteOffsetBytes + bytes <= regionBytes
    }

    internal inline fun beginVertices(
        vertexCount: Int,
        cursor: VertexCursor,
        onArrayBufferBound: (Int) -> Unit
    ) {
        val bytes = Math.multiplyExact(vertexCount, strideBytes)
        if (supportsPersistent && canUseRingOffsets && !persistentDisabled) {
            if (!persistentMapped || bytes > regionBytes) {
                if (!allocatePersistentRing(bytes, onArrayBufferBound)) {
                    persistentDisabled = true
                }
            }
            if (persistentMapped) {
                if (regionWriteOffsetBytes + bytes > regionBytes) {
                    advancePersistentRegion()
                }
                val absoluteOffsetBytes = currentRegion.toLong() * regionBytes + regionWriteOffsetBytes
                cursor.init(
                    baseAddress = mappedAddress + absoluteOffsetBytes,
                    byteLength = bytes.toLong(),
                    strideBytes = strideBytes,
                    token = absoluteOffsetBytes / strideBytes,
                    segment = mappedSegment,
                    segmentBaseAddress = mappedAddress
                )
                return
            }
        }

        ensureFallbackCapacity(bytes)
        cursor.init(
            baseAddress = fallbackAddress,
            byteLength = bytes.toLong(),
            strideBytes = strideBytes,
            token = FALLBACK_TOKEN,
            segment = fallbackSegment,
            segmentBaseAddress = fallbackAddress
        )
    }

    internal inline fun endVertices(
        vertexCount: Int,
        cursor: VertexCursor,
        onArrayBufferBound: (Int) -> Unit
    ): Int {
        val bytes = vertexCount * strideBytes
        val token = cursor.token
        if (token != FALLBACK_TOKEN) {
            regionWriteOffsetBytes += bytes
            return token.toInt()
        }
        return uploadFallbackScratch(bytes, onArrayBufferBound)
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
        mappedSegment = MemorySegment.ofBuffer(mapped)
        regionBytes = targetRegionBytes
        capacityFloats = (totalBytes / Float.SIZE_BYTES).toInt()
        currentRegion = 0
        regionWriteOffsetBytes = 0
        persistentMapped = true
        return true
    }

    private fun ensureFallbackCapacity(requiredBytes: Int) {
        if (requiredBytes <= fallbackCapacity && fallbackBuffer != null) return
        var newCap = maxOf(fallbackCapacity, INITIAL_FALLBACK_BYTES)
        while (newCap < requiredBytes) {
            newCap = Math.multiplyExact(newCap, 2)
        }
        fallbackBuffer?.let { MemoryUtil.memFree(it) }
        val allocated = MemoryUtil.memAlloc(newCap).order(ByteOrder.nativeOrder())
        fallbackBuffer = allocated
        fallbackSegment = MemorySegment.ofBuffer(allocated)
        fallbackAddress = MemoryUtil.memAddress(allocated)
        fallbackCapacity = newCap
    }

    private inline fun uploadFallbackScratch(
        bytes: Int,
        onArrayBufferBound: (Int) -> Unit
    ): Int {
        val scratch = checkNotNull(fallbackBuffer) { "Fallback vertex scratch buffer is not allocated" }
        onArrayBufferBound(vbo)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo)

        if (regionBytes == 0 || bytes > regionBytes) {
            regionBytes = computeRegionBytes(bytes)
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, regionBytes.toLong(), GL15.GL_STREAM_DRAW)
            capacityFloats = regionBytes / Float.SIZE_BYTES
            regionWriteOffsetBytes = 0
        } else if (!canUseRingOffsets || regionWriteOffsetBytes + bytes > regionBytes) {
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, regionBytes.toLong(), GL15.GL_STREAM_DRAW)
            regionWriteOffsetBytes = 0
        }

        val writeOffset = regionWriteOffsetBytes
        scratch.position(0).limit(bytes)
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, writeOffset.toLong(), scratch)
        scratch.clear()
        regionWriteOffsetBytes = writeOffset + bytes
        return writeOffset / strideBytes
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
        mappedSegment = null
        fallbackBuffer?.let {
            fallbackBuffer = null
            fallbackSegment = null
            fallbackAddress = 0L
            fallbackCapacity = 0
            MemoryUtil.memFree(it)
        }
        GL15.glDeleteBuffers(vbo)
        if (quadIndexBuffer != 0) {
            GL15.glDeleteBuffers(quadIndexBuffer)
        }
        GL30.glDeleteVertexArrays(vao)
    }

    internal companion object {
        const val RING_REGIONS = 3
        const val INITIAL_REGION_BYTES = 128 * 1024
        const val INITIAL_FALLBACK_BYTES = 16 * 1024
        const val FALLBACK_TOKEN = -1L
    }
}
