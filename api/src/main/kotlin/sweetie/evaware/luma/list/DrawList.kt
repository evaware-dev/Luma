package sweetie.evaware.luma.list

import java.awt.image.BufferedImage
import java.lang.foreign.MemorySegment
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.util.Arrays
import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.*
import sweetie.evaware.luma.shader.translator.ShaderReflection
import sweetie.evaware.luma.uniform.*
import sweetie.evaware.luma.vertex.VertexCursor
import sweetie.evaware.luma.vertex.VertexInputLayout
import sweetie.evaware.luma.vertex.VertexLayout

/**
 * Passive, reusable, zero-allocation recorded command list.
 *
 * Never wraps [Luma.backend] permanently. Only intercepts [Luma.backend] for the duration of
 * [record] (always restored in `finally`), storing commands in compact SoA arrays, vertex floats
 * in a reusable off-heap [FloatBuffer], and uniform snapshots in flat primitive arrays.
 *
 * Calling [record] again resets and reuses existing buffers/arrays with zero reallocation in
 * steady state. [replay] submits recorded commands to any [RenderBackend] with 0 B/op heap
 * allocations in steady state.
 */
class DrawList(
    initialCommandCapacity: Int = DEFAULT_COMMAND_CAPACITY,
    initialVertexFloats: Int = DEFAULT_VERTEX_FLOATS
) : AutoCloseable {
    init {
        require(initialCommandCapacity > 0) { "initialCommandCapacity must be positive: $initialCommandCapacity" }
        require(initialVertexFloats > 0) { "initialVertexFloats must be positive: $initialVertexFloats" }
    }

    private var ops = ByteArray(initialCommandCapacity)
    private var opCount = 0

    private var intArgs = IntArray(initialCommandCapacity * 4)
    private var intCount = 0

    private var objArgs = arrayOfNulls<Any>(initialCommandCapacity)
    private var objCount = 0

    private var vertexBuffer: FloatBuffer? = MemoryUtil.memAllocFloat(initialVertexFloats)
    private var vertexSegment: MemorySegment? = MemorySegment.ofBuffer(vertexBuffer!!)
    private var vertexBaseAddress: Long = MemoryUtil.memAddress(vertexBuffer!!)
    private var vertexFloats = 0
    private val replayCursor = VertexCursor()

    // Uniform snapshot storage (flat primitive payloads + reusable per-schema replay ShaderUniforms)
    private var schemas = arrayOfNulls<UniformSchema>(4)
    private var schemaCount = 0
    private var snapshotSchema = IntArray(16)
    private var snapshotFloatOffset = IntArray(16)
    private var snapshotIntOffset = IntArray(16)
    private var snapshotCount = 0
    private var uniformFloats = FloatArray(64)
    private var uniformFloatCount = 0
    private var uniformInts = IntArray(16)
    private var uniformIntCount = 0

    // Transient deduplication cache active only during record()
    private var lastSnapshotSource: ShaderUniforms? = null
    private var lastSnapshotRevision = 0
    private var lastSnapshotIndex = -1
    private var lastSchemaIndex = -1

    private val replayClearColor = FloatArray(4)
    private val recordingBackend = RecordingBackend()

    private var recording = false
    private var closed = false

    val isClosed: Boolean get() = closed
    val isEmpty: Boolean get() = opCount == 0
    val commandCount: Int get() = opCount
    val vertexFloatCount: Int get() = vertexFloats

    inline fun record(delegate: RenderBackend = Luma.backend, action: () -> Unit) {
        val previous = beginRecord(delegate)
        var failed = false
        try {
            action()
        } catch (failure: Throwable) {
            failed = true
            throw failure
        } finally {
            endRecord(previous, delegate, failed)
        }
    }

    @PublishedApi
    internal fun beginRecord(delegate: RenderBackend): RenderBackend? {
        requireOpen()
        check(!recording) { "DrawList is already recording" }
        resetForRecord()
        val previous = if (Luma.isBackendInitialized) Luma.backend else null
        recording = true
        recordingBackend.attach(delegate)
        Luma.backend = recordingBackend
        return previous
    }

    @PublishedApi
    internal fun endRecord(previous: RenderBackend?, delegate: RenderBackend, failed: Boolean) {
        Luma.backend = previous ?: delegate
        recordingBackend.detach()
        recording = false
        lastSnapshotSource = null
        lastSnapshotRevision = 0
        lastSnapshotIndex = -1
        lastSchemaIndex = -1
        if (failed) {
            resetForRecord()
        }
    }

    fun clear() {
        requireOpen()
        check(!recording) { "Cannot clear DrawList while recording" }
        resetForRecord()
    }

    fun replay(backend: RenderBackend = Luma.backend) {
        requireOpen()
        check(!recording) { "Cannot replay DrawList while recording" }
        val count = opCount
        if (count == 0) return

        val opArr = ops
        val ints = intArgs
        val objs = objArgs
        val srcSeg = vertexSegment!!
        val baseAddr = vertexBaseAddress
        val cursor = replayCursor

        var i = 0
        var o = 0
        for (opIdx in 0 until count) {
            when (opArr[opIdx]) {
                OP_BIND_PROGRAM -> {
                    backend.bindProgram(objs[o++] as ProgramHandle)
                }
                OP_BIND_TEXTURE -> {
                    val tex = objs[o++] as TextureHandle
                    val unit = ints[i++]
                    backend.bindTexture(tex, unit)
                }
                OP_SET_TEXTURE_SAMPLER -> {
                    val tex = objs[o++] as TextureHandle
                    val sampler = objs[o++] as SamplerState
                    backend.setTextureSampler(tex, sampler)
                }
                OP_GENERATE_MIPMAPS -> {
                    backend.generateMipmaps(objs[o++] as TextureHandle)
                }
                OP_BIND_VERTEX_BUFFER -> {
                    val buffer = objs[o++] as VertexBufferHandle
                    val binding = ints[i++]
                    val offsetBytes = unpackLong(ints[i++], ints[i++])
                    backend.bindVertexBuffer(binding, buffer, offsetBytes)
                }
                OP_BIND_INDEX_BUFFER -> {
                    backend.bindIndexBuffer(objs[o++] as IndexBufferHandle)
                }
                OP_DRAW_IMMEDIATE -> {
                    val program = objs[o++] as ProgramHandle
                    val floatOffset = ints[i++]
                    val floatCount = ints[i++]
                    val vertexCount = ints[i++]
                    val snapshotIdx = ints[i++]
                    val primitive = PRIMITIVE_TYPES[ints[i++]]
                    val uniforms = resolveReplayUniforms(snapshotIdx)
                    backend.beginVertices(program, vertexCount, cursor)
                    val copyBytes = floatCount.toLong() shl 2
                    var committed = false
                    try {
                        cursor.copyFrom(srcSeg, floatOffset.toLong() shl 2, baseAddr, copyBytes)
                        committed = true
                        val token = cursor.token
                        cursor.reset()
                        cursor.token = token
                        try {
                            backend.endVertices(program, cursor, vertexCount, uniforms, primitive)
                        } finally {
                            cursor.token = 0L
                        }
                    } finally {
                        if (!committed) {
                            val token = cursor.token
                            cursor.reset()
                            cursor.token = token
                            try {
                                backend.endVertices(program, cursor, 0, uniforms, primitive)
                            } finally {
                                cursor.token = 0L
                            }
                        }
                    }
                }
                OP_DRAW_DIRECT -> {
                    val program = objs[o++] as ProgramHandle
                    val snapshotIdx = ints[i++]
                    val primitive = PRIMITIVE_TYPES[ints[i++]]
                    val firstVertex = ints[i++]
                    val vertexCount = ints[i++]
                    val instanceCount = ints[i++]
                    val firstInstance = ints[i++]
                    val uniforms = resolveReplayUniforms(snapshotIdx)
                    backend.draw(program, uniforms, primitive, firstVertex, vertexCount, instanceCount, firstInstance)
                }
                OP_DRAW_INDEXED -> {
                    val program = objs[o++] as ProgramHandle
                    val snapshotIdx = ints[i++]
                    val primitive = PRIMITIVE_TYPES[ints[i++]]
                    val firstIndex = ints[i++]
                    val indexCount = ints[i++]
                    val vertexOffset = ints[i++]
                    val instanceCount = ints[i++]
                    val firstInstance = ints[i++]
                    val uniforms = resolveReplayUniforms(snapshotIdx)
                    backend.drawIndexed(
                        program,
                        uniforms,
                        primitive,
                        firstIndex,
                        indexCount,
                        vertexOffset,
                        instanceCount,
                        firstInstance
                    )
                }
                OP_BEGIN_RENDER_TARGET -> {
                    val target = objs[o++] as RenderTargetHandle
                    val hasClear = ints[i++] != 0
                    if (hasClear) {
                        val clear = replayClearColor
                        clear[0] = java.lang.Float.intBitsToFloat(ints[i++])
                        clear[1] = java.lang.Float.intBitsToFloat(ints[i++])
                        clear[2] = java.lang.Float.intBitsToFloat(ints[i++])
                        clear[3] = java.lang.Float.intBitsToFloat(ints[i++])
                        backend.beginRenderTarget(target, clear)
                    } else {
                        backend.beginRenderTarget(target, null)
                    }
                }
                OP_END_RENDER_TARGET -> {
                    backend.endRenderTarget()
                }
                OP_BLIT_RENDER_TARGET -> {
                    val source = objs[o++] as RenderTargetHandle
                    val destination = objs[o++] as RenderTargetHandle?
                    val srcX = ints[i++]
                    val srcY = ints[i++]
                    val srcW = ints[i++]
                    val srcH = ints[i++]
                    val dstX = ints[i++]
                    val dstY = ints[i++]
                    val dstW = ints[i++]
                    val dstH = ints[i++]
                    val packed = ints[i++]
                    val filter = TEXTURE_FILTERS[packed and 0x3]
                    val copyColor = (packed and BLIT_COPY_COLOR_BIT) != 0
                    val copyDepth = (packed and BLIT_COPY_DEPTH_BIT) != 0
                    backend.blitRenderTarget(
                        source, destination, srcX, srcY, srcW, srcH, dstX, dstY, dstW, dstH,
                        filter, copyColor, copyDepth
                    )
                }
                OP_BLEND -> backend.blend(ints[i++] != 0)
                OP_BLEND_FUNCTION -> backend.blendFunction(objs[o++] as BlendFunction)
                OP_COLOR_MASK -> {
                    val mask = ints[i++]
                    backend.colorMask(
                        (mask and 1) != 0,
                        (mask and 2) != 0,
                        (mask and 4) != 0,
                        (mask and 8) != 0
                    )
                }
                OP_DEPTH_TEST -> backend.depthTest(ints[i++] != 0)
                OP_DEPTH_WRITE -> backend.depthWrite(ints[i++] != 0)
                OP_DEPTH_COMPARE -> backend.depthCompare(DEPTH_COMPARES[ints[i++]])
                OP_STENCIL_TEST -> backend.stencilTest(ints[i++] != 0)
                OP_STENCIL_FUNC -> {
                    val compare = STENCIL_COMPARES[ints[i++]]
                    val ref = ints[i++]
                    val readMask = ints[i++]
                    backend.stencilFunc(compare, ref, readMask)
                }
                OP_STENCIL_OP -> {
                    val packed = ints[i++]
                    backend.stencilOp(
                        STENCIL_OPS[packed and 0xFF],
                        STENCIL_OPS[(packed ushr 8) and 0xFF],
                        STENCIL_OPS[(packed ushr 16) and 0xFF]
                    )
                }
                OP_STENCIL_MASK -> backend.stencilMask(ints[i++])
                OP_CULL -> backend.cull(ints[i++] != 0)
                OP_SCISSOR -> {
                    val x = ints[i++]
                    val y = ints[i++]
                    val width = ints[i++]
                    val height = ints[i++]
                    backend.scissor(x, y, width, height)
                }
                OP_DISABLE_SCISSOR -> backend.disableScissor()
                OP_CLEAR_COLOR -> {
                    backend.clearColor(
                        java.lang.Float.intBitsToFloat(ints[i++]),
                        java.lang.Float.intBitsToFloat(ints[i++]),
                        java.lang.Float.intBitsToFloat(ints[i++]),
                        java.lang.Float.intBitsToFloat(ints[i++])
                    )
                }
                OP_CLEAR_DEPTH -> {
                    val bits = unpackLong(ints[i++], ints[i++])
                    backend.clearDepth(java.lang.Double.longBitsToDouble(bits))
                }
                OP_CLEAR_STENCIL -> backend.clearStencil(ints[i++])
                OP_PUSH_DEBUG_GROUP -> backend.pushDebugGroup(objs[o++] as String)
                OP_POP_DEBUG_GROUP -> backend.popDebugGroup()
            }
        }
    }

    override fun close() {
        if (closed) return
        check(!recording) { "Cannot close DrawList while recording" }
        closed = true
        resetForRecord()
        for (i in 0 until schemaCount) {
            schemas[i] = null
        }
        schemaCount = 0
        vertexSegment = null
        vertexBaseAddress = 0L
        vertexBuffer?.let {
            vertexBuffer = null
            MemoryUtil.memFree(it)
        }
    }

    private fun requireOpen() {
        check(!closed) { "DrawList is closed" }
    }

    private fun resetForRecord() {
        if (objCount > 0) {
            Arrays.fill(objArgs, 0, objCount, null)
        }
        opCount = 0
        intCount = 0
        objCount = 0
        vertexFloats = 0
        snapshotCount = 0
        uniformFloatCount = 0
        uniformIntCount = 0
        lastSnapshotSource = null
        lastSnapshotRevision = 0
        lastSnapshotIndex = -1
        lastSchemaIndex = -1
        for (i in 0 until schemaCount) {
            schemas[i]?.lastAppliedSnapshot = -1
        }
    }

    private fun emitOp(op: Byte) {
        if (opCount == ops.size) {
            ops = ops.copyOf(nextCapacity(opCount + 1, ops.size))
        }
        ops[opCount++] = op
    }

    private fun emitObj(value: Any?) {
        if (objCount == objArgs.size) {
            objArgs = objArgs.copyOf(nextCapacity(objCount + 1, objArgs.size))
        }
        objArgs[objCount++] = value
    }

    private fun emitInt(a: Int) {
        ensureInts(1)
        intArgs[intCount++] = a
    }

    private fun emitLong(value: Long) {
        ensureInts(2)
        val idx = intCount
        intArgs[idx] = (value ushr 32).toInt()
        intArgs[idx + 1] = value.toInt()
        intCount = idx + 2
    }

    private fun ensureInts(additional: Int) {
        val required = intCount + additional
        if (required > intArgs.size) {
            intArgs = intArgs.copyOf(nextCapacity(required, intArgs.size))
        }
    }

    private fun reserveVertexFloats(additionalFloats: Int): Int {
        val start = vertexFloats
        val required = Math.addExact(start, additionalFloats)
        var dst = vertexBuffer!!
        if (required > dst.capacity()) {
            dst = MemoryUtil.memRealloc(dst, nextCapacity(required, dst.capacity()))
            dst.clear()
            vertexBuffer = dst
            vertexSegment = MemorySegment.ofBuffer(dst)
            vertexBaseAddress = MemoryUtil.memAddress(dst)
        }
        return start
    }

    private fun snapshotUniforms(uniforms: ShaderUniforms): Int {
        val entries = uniforms.registry.entries
        if (entries.isEmpty()) return -1

        val revision = uniforms.registry.revision
        if (uniforms === lastSnapshotSource && revision == lastSnapshotRevision && lastSnapshotIndex >= 0) {
            return lastSnapshotIndex
        }

        val schemaIdx = findOrCreateSchema(uniforms, entries)
        val schema = schemas[schemaIdx]!!

        val floatOff = uniformFloatCount
        val intOff = uniformIntCount
        val requiredFloats = floatOff + schema.floatStride
        if (requiredFloats > uniformFloats.size) {
            uniformFloats = uniformFloats.copyOf(nextCapacity(requiredFloats, uniformFloats.size))
        }
        val requiredInts = intOff + schema.intStride
        if (requiredInts > uniformInts.size) {
            uniformInts = uniformInts.copyOf(nextCapacity(requiredInts, uniformInts.size))
        }

        val fArr = uniformFloats
        val iArr = uniformInts
        var fPos = floatOff
        var iPos = intOff
        for (idx in entries.indices) {
            when (val h = entries[idx].handle) {
                is Float1Uniform -> {
                    fArr[fPos++] = h.value
                }
                is Float2Uniform -> {
                    fArr[fPos++] = h.first
                    fArr[fPos++] = h.second
                }
                is Float3Uniform -> {
                    fArr[fPos++] = h.first
                    fArr[fPos++] = h.second
                    fArr[fPos++] = h.third
                }
                is Float4Uniform -> {
                    fArr[fPos++] = h.first
                    fArr[fPos++] = h.second
                    fArr[fPos++] = h.third
                    fArr[fPos++] = h.fourth
                }
                is Int1Uniform -> {
                    iArr[iPos++] = h.value
                }
                is Mat4Uniform -> {
                    val m = h.value
                    fArr[fPos++] = m.m00()
                    fArr[fPos++] = m.m01()
                    fArr[fPos++] = m.m02()
                    fArr[fPos++] = m.m03()
                    fArr[fPos++] = m.m10()
                    fArr[fPos++] = m.m11()
                    fArr[fPos++] = m.m12()
                    fArr[fPos++] = m.m13()
                    fArr[fPos++] = m.m20()
                    fArr[fPos++] = m.m21()
                    fArr[fPos++] = m.m22()
                    fArr[fPos++] = m.m23()
                    fArr[fPos++] = m.m30()
                    fArr[fPos++] = m.m31()
                    fArr[fPos++] = m.m32()
                    fArr[fPos++] = m.m33()
                }
            }
        }
        uniformFloatCount = fPos
        uniformIntCount = iPos

        val snapIdx = snapshotCount
        if (snapIdx == snapshotSchema.size) {
            val newCap = nextCapacity(snapIdx + 1, snapshotSchema.size)
            snapshotSchema = snapshotSchema.copyOf(newCap)
            snapshotFloatOffset = snapshotFloatOffset.copyOf(newCap)
            snapshotIntOffset = snapshotIntOffset.copyOf(newCap)
        }
        snapshotSchema[snapIdx] = schemaIdx
        snapshotFloatOffset[snapIdx] = floatOff
        snapshotIntOffset[snapIdx] = intOff
        snapshotCount = snapIdx + 1

        lastSnapshotSource = uniforms
        lastSnapshotRevision = revision
        lastSnapshotIndex = snapIdx
        lastSchemaIndex = schemaIdx
        return snapIdx
    }

    private fun findOrCreateSchema(
        uniforms: ShaderUniforms,
        entries: ArrayList<UniformRegistry.Entry>
    ): Int {
        if (uniforms === lastSnapshotSource && lastSchemaIndex >= 0) {
            val cached = schemas[lastSchemaIndex]
            if (cached != null && cached.matches(entries)) return lastSchemaIndex
        }
        for (i in 0 until schemaCount) {
            if (schemas[i]!!.matches(entries)) return i
        }
        val idx = schemaCount
        if (idx == schemas.size) {
            schemas = schemas.copyOf(nextCapacity(idx + 1, schemas.size))
        }
        schemas[idx] = UniformSchema(entries)
        schemaCount = idx + 1
        return idx
    }

    private fun resolveReplayUniforms(snapshotIdx: Int): ShaderUniforms {
        if (snapshotIdx < 0) return EMPTY_UNIFORMS
        val schema = schemas[snapshotSchema[snapshotIdx]]!!
        if (schema.lastAppliedSnapshot == snapshotIdx) {
            return schema.replayUniforms
        }
        schema.applySnapshot(
            uniformFloats,
            snapshotFloatOffset[snapshotIdx],
            uniformInts,
            snapshotIntOffset[snapshotIdx]
        )
        schema.lastAppliedSnapshot = snapshotIdx
        return schema.replayUniforms
    }

    private class UniformSchema(entries: ArrayList<UniformRegistry.Entry>) {
        val names = Array(entries.size) { entries[it].name }
        val types = ByteArray(entries.size) { typeCode(entries[it].handle) }
        val floatStride: Int
        val intStride: Int
        val replayUniforms = ShaderUniforms()
        val replayHandles: Array<UniformHandle>
        var lastAppliedSnapshot = -1

        init {
            var floats = 0
            var ints = 0
            val handles = arrayOfNulls<UniformHandle>(entries.size)
            for (i in entries.indices) {
                val name = names[i]
                handles[i] = when (types[i]) {
                    TYPE_FLOAT1 -> {
                        floats += 1
                        replayUniforms.registry.registerFloat1(name)
                    }
                    TYPE_FLOAT2 -> {
                        floats += 2
                        replayUniforms.registry.registerFloat2(name)
                    }
                    TYPE_FLOAT3 -> {
                        floats += 3
                        replayUniforms.registry.registerFloat3(name)
                    }
                    TYPE_FLOAT4 -> {
                        floats += 4
                        replayUniforms.registry.registerFloat4(name)
                    }
                    TYPE_INT1 -> {
                        ints += 1
                        replayUniforms.registry.registerInt1(name)
                    }
                    TYPE_MAT4 -> {
                        floats += 16
                        replayUniforms.registry.registerMat4(name)
                    }
                    else -> error("Unknown uniform type code: ${types[i]}")
                }
            }
            @Suppress("UNCHECKED_CAST")
            replayHandles = handles as Array<UniformHandle>
            floatStride = floats
            intStride = ints
        }

        fun matches(entries: ArrayList<UniformRegistry.Entry>): Boolean {
            if (entries.size != names.size) return false
            for (i in names.indices) {
                val entry = entries[i]
                if (types[i] != typeCode(entry.handle) || names[i] != entry.name) return false
            }
            return true
        }

        fun applySnapshot(floats: FloatArray, floatOffset: Int, ints: IntArray, intOffset: Int) {
            var f = floatOffset
            var ii = intOffset
            val handles = replayHandles
            val typeArr = types
            for (i in handles.indices) {
                when (typeArr[i]) {
                    TYPE_FLOAT1 -> {
                        (handles[i] as Float1Uniform).value = floats[f++]
                    }
                    TYPE_FLOAT2 -> {
                        val h = handles[i] as Float2Uniform
                        h.first = floats[f++]
                        h.second = floats[f++]
                    }
                    TYPE_FLOAT3 -> {
                        val h = handles[i] as Float3Uniform
                        h.first = floats[f++]
                        h.second = floats[f++]
                        h.third = floats[f++]
                    }
                    TYPE_FLOAT4 -> {
                        val h = handles[i] as Float4Uniform
                        h.first = floats[f++]
                        h.second = floats[f++]
                        h.third = floats[f++]
                        h.fourth = floats[f++]
                    }
                    TYPE_INT1 -> {
                        (handles[i] as Int1Uniform).value = ints[ii++]
                    }
                    TYPE_MAT4 -> {
                        val h = handles[i] as Mat4Uniform
                        val m = h.value
                        val m00 = floats[f++]; val m01 = floats[f++]; val m02 = floats[f++]; val m03 = floats[f++]
                        val m10 = floats[f++]; val m11 = floats[f++]; val m12 = floats[f++]; val m13 = floats[f++]
                        val m20 = floats[f++]; val m21 = floats[f++]; val m22 = floats[f++]; val m23 = floats[f++]
                        val m30 = floats[f++]; val m31 = floats[f++]; val m32 = floats[f++]; val m33 = floats[f++]
                        if (
                            m.m00() != m00 || m.m01() != m01 || m.m02() != m02 || m.m03() != m03 ||
                            m.m10() != m10 || m.m11() != m11 || m.m12() != m12 || m.m13() != m13 ||
                            m.m20() != m20 || m.m21() != m21 || m.m22() != m22 || m.m23() != m23 ||
                            m.m30() != m30 || m.m31() != m31 || m.m32() != m32 || m.m33() != m33
                        ) {
                            m.set(
                                m00, m01, m02, m03,
                                m10, m11, m12, m13,
                                m20, m21, m22, m23,
                                m30, m31, m32, m33
                            )
                            h.isDirty = true
                        }
                    }
                }
            }
        }
    }

    private inner class RecordingBackend : RenderBackend {
        private var delegate: RenderBackend? = null
        private var depthTestEnabled = false
        private var depthWriteEnabled = false
        private var depthCompareMode = DepthCompare.ALWAYS
        private var maskR = true
        private var maskG = true
        private var maskB = true
        private var maskA = true

        fun attach(target: RenderBackend) {
            delegate = target
            depthTestEnabled = target.isDepthTestEnabled
            depthWriteEnabled = target.isDepthWriteEnabled
            depthCompareMode = target.currentDepthCompare
            maskR = target.colorMaskRed
            maskG = target.colorMaskGreen
            maskB = target.colorMaskBlue
            maskA = target.colorMaskAlpha
        }

        fun detach() {
            delegate = null
        }

        private fun requireDelegate(): RenderBackend =
            checkNotNull(delegate) { "DrawList is not currently recording" }

        // State queries track recorded mutations so helpers like ClipMask compose accurately
        override val supportsStencil: Boolean get() = requireDelegate().supportsStencil
        override val supportsGpuTimestamps: Boolean get() = requireDelegate().supportsGpuTimestamps
        override val isDepthTestEnabled: Boolean get() = depthTestEnabled
        override val isDepthWriteEnabled: Boolean get() = depthWriteEnabled
        override val currentDepthCompare: DepthCompare get() = depthCompareMode
        override val colorMaskRed: Boolean get() = maskR
        override val colorMaskGreen: Boolean get() = maskG
        override val colorMaskBlue: Boolean get() = maskB
        override val colorMaskAlpha: Boolean get() = maskA

        // Frame lifecycle is not recordable inside a DrawList
        override fun beginFrame() {
            throw IllegalStateException("Cannot call beginFrame() while recording a DrawList")
        }

        override fun endFrame() {
            throw IllegalStateException("Cannot call endFrame() while recording a DrawList")
        }

        override fun close() {
            throw IllegalStateException("Cannot call close() on Luma.backend while recording a DrawList")
        }

        override fun hasContext(): Boolean = requireDelegate().hasContext()

        // Resource creation / uploads delegate cleanly to the underlying backend
        override fun createProgram(
            vertexSource: String,
            fragmentSource: String,
            layouts: VertexInputLayout,
            reflection: ShaderReflection?
        ): ProgramHandle = requireDelegate().createProgram(vertexSource, fragmentSource, layouts, reflection)

        override fun createProgram(
            vertexSource: String,
            fragmentSource: String,
            layout: VertexLayout,
            reflection: ShaderReflection?
        ): ProgramHandle = requireDelegate().createProgram(vertexSource, fragmentSource, layout, reflection)

        override fun invalidatePipelineCache() = requireDelegate().invalidatePipelineCache()

        override fun createTexture(
            image: BufferedImage,
            mipmap: Boolean,
            format: TextureFormat,
            sampler: SamplerState
        ): TextureHandle = requireDelegate().createTexture(image, mipmap, format, sampler)

        override fun createTexture(
            width: Int,
            height: Int,
            data: ByteBuffer?,
            format: TextureFormat,
            mipmap: Boolean,
            sampler: SamplerState
        ): TextureHandle = requireDelegate().createTexture(width, height, data, format, mipmap, sampler)

        override fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage) =
            requireDelegate().updateTexture(texture, x, y, image)

        override fun updateTexture(
            texture: TextureHandle,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            data: ByteBuffer
        ) = requireDelegate().updateTexture(texture, x, y, width, height, data)

        override fun createVertexBuffer(sizeBytes: Long, usage: BufferUsage): VertexBufferHandle =
            requireDelegate().createVertexBuffer(sizeBytes, usage)

        override fun createIndexBuffer(sizeBytes: Long, indexType: IndexType, usage: BufferUsage): IndexBufferHandle =
            requireDelegate().createIndexBuffer(sizeBytes, indexType, usage)

        override fun updateVertexBuffer(buffer: VertexBufferHandle, offsetBytes: Long, data: ByteBuffer) =
            requireDelegate().updateVertexBuffer(buffer, offsetBytes, data)

        override fun updateIndexBuffer(buffer: IndexBufferHandle, offsetBytes: Long, data: ByteBuffer) =
            requireDelegate().updateIndexBuffer(buffer, offsetBytes, data)

        override fun createRenderTarget(
            width: Int,
            height: Int,
            useDepth: Boolean,
            format: RenderTargetFormat,
            sampler: SamplerState,
            useStencil: Boolean
        ): RenderTargetHandle = requireDelegate().createRenderTarget(width, height, useDepth, format, sampler, useStencil)

        override fun createTimestampPool(capacity: Int): GpuTimestampPool =
            requireDelegate().createTimestampPool(capacity)

        override fun writeTimestamp(pool: GpuTimestampPool, queryIndex: Int) {
            throw IllegalStateException("Cannot call writeTimestamp() directly while recording a DrawList")
        }

        // Recorded commands
        override fun bindProgram(program: ProgramHandle) {
            emitOp(OP_BIND_PROGRAM)
            emitObj(program)
        }

        override fun bindTexture(texture: TextureHandle, unit: Int) {
            require(unit >= 0) { "Texture unit must be non-negative: $unit" }
            emitOp(OP_BIND_TEXTURE)
            emitObj(texture)
            emitInt(unit)
        }

        override fun setTextureSampler(texture: TextureHandle, sampler: SamplerState) {
            emitOp(OP_SET_TEXTURE_SAMPLER)
            emitObj(texture)
            emitObj(sampler)
        }

        override fun generateMipmaps(texture: TextureHandle) {
            emitOp(OP_GENERATE_MIPMAPS)
            emitObj(texture)
        }

        override fun bindVertexBuffer(binding: Int, buffer: VertexBufferHandle, offsetBytes: Long) {
            require(binding >= 0) { "Vertex binding must be non-negative: $binding" }
            require(offsetBytes >= 0L) { "Vertex buffer offset must be non-negative: $offsetBytes" }
            emitOp(OP_BIND_VERTEX_BUFFER)
            emitObj(buffer)
            emitInt(binding)
            emitLong(offsetBytes)
        }

        override fun bindIndexBuffer(buffer: IndexBufferHandle) {
            emitOp(OP_BIND_INDEX_BUFFER)
            emitObj(buffer)
        }

        override fun beginVertices(
            program: ProgramHandle,
            vertexCount: Int,
            cursor: VertexCursor
        ) {
            val strideBytes = program.vertexStrideBytes
            require(strideBytes > 0 && (strideBytes and 3) == 0) {
                "ProgramHandle must report a positive float-aligned vertexStrideBytes for DrawList recording: $strideBytes"
            }
            val strideFloats = strideBytes ushr 2
            val floatCount = Math.multiplyExact(vertexCount, strideFloats)
            val startFloat = reserveVertexFloats(floatCount)
            cursor.init(
                baseAddress = vertexBaseAddress + (startFloat.toLong() shl 2),
                byteLength = floatCount.toLong() shl 2,
                strideBytes = strideBytes,
                token = startFloat.toLong()
            )
        }

        override fun endVertices(
            program: ProgramHandle,
            cursor: VertexCursor,
            vertexCount: Int,
            uniforms: ShaderUniforms,
            primitiveType: PrimitiveType
        ) {
            if (vertexCount <= 0) return
            val strideFloats = program.vertexStrideBytes ushr 2
            val floats = vertexCount * strideFloats
            val floatOffset = cursor.token.toInt()
            vertexFloats = floatOffset + floats
            val snapshotIdx = snapshotUniforms(uniforms)
            val primOrd = primitiveType.ordinal

            // O(1) tail-merge consecutive identical immediate draws with contiguous vertex data
            if (opCount > 0 && ops[opCount - 1] == OP_DRAW_IMMEDIATE) {
                val prevIntBase = intCount - 5
                val prevFloatOffset = intArgs[prevIntBase]
                val prevFloatCount = intArgs[prevIntBase + 1]
                val prevVertexCount = intArgs[prevIntBase + 2]
                if (
                    objArgs[objCount - 1] === program &&
                    intArgs[prevIntBase + 3] == snapshotIdx &&
                    intArgs[prevIntBase + 4] == primOrd &&
                    prevFloatOffset + prevFloatCount == floatOffset &&
                    (primitiveType != PrimitiveType.QUADS || ((prevVertexCount and 3) == 0 && (vertexCount and 3) == 0)) &&
                    (primitiveType != PrimitiveType.LINES || ((prevVertexCount and 1) == 0 && (vertexCount and 1) == 0))
                ) {
                    intArgs[prevIntBase + 1] = prevFloatCount + floats
                    intArgs[prevIntBase + 2] = prevVertexCount + vertexCount
                    return
                }
            }

            emitOp(OP_DRAW_IMMEDIATE)
            emitObj(program)
            ensureInts(5)
            val idx = intCount
            intArgs[idx] = floatOffset
            intArgs[idx + 1] = floats
            intArgs[idx + 2] = vertexCount
            intArgs[idx + 3] = snapshotIdx
            intArgs[idx + 4] = primOrd
            intCount = idx + 5
        }

        override fun draw(
            program: ProgramHandle,
            uniforms: ShaderUniforms,
            primitiveType: PrimitiveType,
            firstVertex: Int,
            vertexCount: Int,
            instanceCount: Int,
            firstInstance: Int
        ) {
            require(firstVertex >= 0 && vertexCount >= 0 && instanceCount >= 0 && firstInstance >= 0)
            val snapshotIdx = snapshotUniforms(uniforms)
            emitOp(OP_DRAW_DIRECT)
            emitObj(program)
            ensureInts(6)
            val idx = intCount
            intArgs[idx] = snapshotIdx
            intArgs[idx + 1] = primitiveType.ordinal
            intArgs[idx + 2] = firstVertex
            intArgs[idx + 3] = vertexCount
            intArgs[idx + 4] = instanceCount
            intArgs[idx + 5] = firstInstance
            intCount = idx + 6
        }

        override fun drawIndexed(
            program: ProgramHandle,
            uniforms: ShaderUniforms,
            primitiveType: PrimitiveType,
            firstIndex: Int,
            indexCount: Int,
            vertexOffset: Int,
            instanceCount: Int,
            firstInstance: Int
        ) {
            require(firstIndex >= 0 && indexCount >= 0 && instanceCount >= 0 && firstInstance >= 0)
            val snapshotIdx = snapshotUniforms(uniforms)
            emitOp(OP_DRAW_INDEXED)
            emitObj(program)
            ensureInts(7)
            val idx = intCount
            intArgs[idx] = snapshotIdx
            intArgs[idx + 1] = primitiveType.ordinal
            intArgs[idx + 2] = firstIndex
            intArgs[idx + 3] = indexCount
            intArgs[idx + 4] = vertexOffset
            intArgs[idx + 5] = instanceCount
            intArgs[idx + 6] = firstInstance
            intCount = idx + 7
        }

        override fun beginRenderTarget(target: RenderTargetHandle, clearColor: FloatArray?) {
            if (clearColor != null) {
                require(clearColor.size >= 4) { "Clear color must contain four components" }
            }
            emitOp(OP_BEGIN_RENDER_TARGET)
            emitObj(target)
            if (clearColor != null) {
                ensureInts(5)
                val idx = intCount
                intArgs[idx] = 1
                intArgs[idx + 1] = java.lang.Float.floatToRawIntBits(clearColor[0])
                intArgs[idx + 2] = java.lang.Float.floatToRawIntBits(clearColor[1])
                intArgs[idx + 3] = java.lang.Float.floatToRawIntBits(clearColor[2])
                intArgs[idx + 4] = java.lang.Float.floatToRawIntBits(clearColor[3])
                intCount = idx + 5
            } else {
                emitInt(0)
            }
        }

        override fun endRenderTarget() {
            emitOp(OP_END_RENDER_TARGET)
        }

        override fun blitRenderTarget(
            source: RenderTargetHandle,
            destination: RenderTargetHandle?,
            srcX: Int,
            srcY: Int,
            srcWidth: Int,
            srcHeight: Int,
            dstX: Int,
            dstY: Int,
            dstWidth: Int,
            dstHeight: Int,
            filter: TextureFilter,
            copyColor: Boolean,
            copyDepth: Boolean
        ) {
            emitOp(OP_BLIT_RENDER_TARGET)
            emitObj(source)
            emitObj(destination)
            ensureInts(9)
            val idx = intCount
            intArgs[idx] = srcX
            intArgs[idx + 1] = srcY
            intArgs[idx + 2] = srcWidth
            intArgs[idx + 3] = srcHeight
            intArgs[idx + 4] = dstX
            intArgs[idx + 5] = dstY
            intArgs[idx + 6] = dstWidth
            intArgs[idx + 7] = dstHeight
            var packed = filter.ordinal and 0x3
            if (copyColor) packed = packed or BLIT_COPY_COLOR_BIT
            if (copyDepth) packed = packed or BLIT_COPY_DEPTH_BIT
            intArgs[idx + 8] = packed
            intCount = idx + 9
        }

        override fun blend(enabled: Boolean) {
            emitOp(OP_BLEND)
            emitInt(if (enabled) 1 else 0)
        }

        override fun blendFunction(function: BlendFunction) {
            emitOp(OP_BLEND_FUNCTION)
            emitObj(function)
        }

        override fun colorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) {
            maskR = red
            maskG = green
            maskB = blue
            maskA = alpha
            var mask = 0
            if (red) mask = mask or 1
            if (green) mask = mask or 2
            if (blue) mask = mask or 4
            if (alpha) mask = mask or 8
            emitOp(OP_COLOR_MASK)
            emitInt(mask)
        }

        override fun depthTest(enabled: Boolean) {
            depthTestEnabled = enabled
            emitOp(OP_DEPTH_TEST)
            emitInt(if (enabled) 1 else 0)
        }

        override fun depthWrite(enabled: Boolean) {
            depthWriteEnabled = enabled
            emitOp(OP_DEPTH_WRITE)
            emitInt(if (enabled) 1 else 0)
        }

        override fun depthCompare(compare: DepthCompare) {
            depthCompareMode = compare
            emitOp(OP_DEPTH_COMPARE)
            emitInt(compare.ordinal)
        }

        override fun stencilTest(enabled: Boolean) {
            emitOp(OP_STENCIL_TEST)
            emitInt(if (enabled) 1 else 0)
        }

        override fun stencilFunc(compare: StencilCompare, ref: Int, readMask: Int) {
            emitOp(OP_STENCIL_FUNC)
            ensureInts(3)
            val idx = intCount
            intArgs[idx] = compare.ordinal
            intArgs[idx + 1] = ref
            intArgs[idx + 2] = readMask
            intCount = idx + 3
        }

        override fun stencilOp(stencilFail: StencilOp, depthFail: StencilOp, pass: StencilOp) {
            emitOp(OP_STENCIL_OP)
            emitInt(
                (stencilFail.ordinal and 0xFF) or
                    ((depthFail.ordinal and 0xFF) shl 8) or
                    ((pass.ordinal and 0xFF) shl 16)
            )
        }

        override fun stencilMask(writeMask: Int) {
            emitOp(OP_STENCIL_MASK)
            emitInt(writeMask)
        }

        override fun cull(enabled: Boolean) {
            emitOp(OP_CULL)
            emitInt(if (enabled) 1 else 0)
        }

        override fun scissor(x: Int, y: Int, width: Int, height: Int) {
            emitOp(OP_SCISSOR)
            ensureInts(4)
            val idx = intCount
            intArgs[idx] = x
            intArgs[idx + 1] = y
            intArgs[idx + 2] = width
            intArgs[idx + 3] = height
            intCount = idx + 4
        }

        override fun disableScissor() {
            emitOp(OP_DISABLE_SCISSOR)
        }

        override fun clearColor(red: Float, green: Float, blue: Float, alpha: Float) {
            emitOp(OP_CLEAR_COLOR)
            ensureInts(4)
            val idx = intCount
            intArgs[idx] = java.lang.Float.floatToRawIntBits(red)
            intArgs[idx + 1] = java.lang.Float.floatToRawIntBits(green)
            intArgs[idx + 2] = java.lang.Float.floatToRawIntBits(blue)
            intArgs[idx + 3] = java.lang.Float.floatToRawIntBits(alpha)
            intCount = idx + 4
        }

        override fun clearDepth(depth: Double) {
            emitOp(OP_CLEAR_DEPTH)
            emitLong(java.lang.Double.doubleToRawLongBits(depth))
        }

        override fun clearStencil(value: Int) {
            emitOp(OP_CLEAR_STENCIL)
            emitInt(value)
        }

        override fun pushDebugGroup(label: String) {
            emitOp(OP_PUSH_DEBUG_GROUP)
            emitObj(label)
        }

        override fun popDebugGroup() {
            emitOp(OP_POP_DEBUG_GROUP)
        }
    }

    private companion object {
        const val DEFAULT_COMMAND_CAPACITY = 64
        const val DEFAULT_VERTEX_FLOATS = 1024

        const val OP_BIND_PROGRAM: Byte = 1
        const val OP_BIND_TEXTURE: Byte = 2
        const val OP_SET_TEXTURE_SAMPLER: Byte = 3
        const val OP_GENERATE_MIPMAPS: Byte = 4
        const val OP_BIND_VERTEX_BUFFER: Byte = 5
        const val OP_BIND_INDEX_BUFFER: Byte = 6
        const val OP_DRAW_IMMEDIATE: Byte = 7
        const val OP_DRAW_DIRECT: Byte = 8
        const val OP_DRAW_INDEXED: Byte = 9
        const val OP_BEGIN_RENDER_TARGET: Byte = 10
        const val OP_END_RENDER_TARGET: Byte = 11
        const val OP_BLIT_RENDER_TARGET: Byte = 12
        const val OP_BLEND: Byte = 13
        const val OP_BLEND_FUNCTION: Byte = 14
        const val OP_COLOR_MASK: Byte = 15
        const val OP_DEPTH_TEST: Byte = 16
        const val OP_DEPTH_WRITE: Byte = 17
        const val OP_DEPTH_COMPARE: Byte = 18
        const val OP_STENCIL_TEST: Byte = 19
        const val OP_STENCIL_FUNC: Byte = 20
        const val OP_STENCIL_OP: Byte = 21
        const val OP_STENCIL_MASK: Byte = 22
        const val OP_CULL: Byte = 23
        const val OP_SCISSOR: Byte = 24
        const val OP_DISABLE_SCISSOR: Byte = 25
        const val OP_CLEAR_COLOR: Byte = 26
        const val OP_CLEAR_DEPTH: Byte = 27
        const val OP_CLEAR_STENCIL: Byte = 28
        const val OP_PUSH_DEBUG_GROUP: Byte = 29
        const val OP_POP_DEBUG_GROUP: Byte = 30

        const val BLIT_COPY_COLOR_BIT = 1 shl 2
        const val BLIT_COPY_DEPTH_BIT = 1 shl 3

        const val TYPE_FLOAT1: Byte = 1
        const val TYPE_FLOAT2: Byte = 2
        const val TYPE_FLOAT3: Byte = 3
        const val TYPE_FLOAT4: Byte = 4
        const val TYPE_INT1: Byte = 5
        const val TYPE_MAT4: Byte = 6

        val PRIMITIVE_TYPES = PrimitiveType.entries.toTypedArray()
        val DEPTH_COMPARES = DepthCompare.entries.toTypedArray()
        val STENCIL_COMPARES = StencilCompare.entries.toTypedArray()
        val STENCIL_OPS = StencilOp.entries.toTypedArray()
        val TEXTURE_FILTERS = TextureFilter.entries.toTypedArray()

        val EMPTY_UNIFORMS = ShaderUniforms()

        fun typeCode(handle: UniformHandle): Byte = when (handle) {
            is Float1Uniform -> TYPE_FLOAT1
            is Float2Uniform -> TYPE_FLOAT2
            is Float3Uniform -> TYPE_FLOAT3
            is Float4Uniform -> TYPE_FLOAT4
            is Int1Uniform -> TYPE_INT1
            is Mat4Uniform -> TYPE_MAT4
        }

        fun unpackLong(high: Int, low: Int): Long =
            (high.toLong() shl 32) or (low.toLong() and 0xFFFF_FFFFL)

        fun nextCapacity(required: Int, current: Int): Int {
            var cap = current.coerceAtLeast(1)
            while (cap < required) {
                val doubled = cap shl 1
                if (doubled <= 0) return required
                cap = doubled
            }
            return cap
        }
    }
}
