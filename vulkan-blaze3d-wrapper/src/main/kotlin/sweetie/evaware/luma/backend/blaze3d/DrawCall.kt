package sweetie.evaware.luma.backend.blaze3d

import java.util.Arrays
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.backend.blaze3d.preset.PresetGpuSampler
import sweetie.evaware.luma.backend.blaze3d.preset.PresetTimestampQueryPool

internal class DrawCallRecorder(
    private val minRetainedCapacity: Int = DEFAULT_MIN_RETAINED_CAPACITY,
    private val lowWaterFramesThreshold: Int = DEFAULT_LOW_WATER_FRAMES
) {
    // Hot Structure-of-Arrays (SoA) fields
    var flags = ByteArray(minRetainedCapacity)
        private set
    var programs = arrayOfNulls<Program>(minRetainedCapacity)
        private set
    var targets = arrayOfNulls<VulkanRenderTarget>(minRetainedCapacity)
        private set
    var targetPassIds = IntArray(minRetainedCapacity)
        private set
    var pipelineKeys = LongArray(minRetainedCapacity)
        private set
    var vertexOffsets = LongArray(minRetainedCapacity)
        private set
    var vertexBytes = LongArray(minRetainedCapacity)
        private set
    var vertexCounts = IntArray(minRetainedCapacity)
        private set
    var uboOffsets = LongArray(minRetainedCapacity)
        private set
    var uboBytes = LongArray(minRetainedCapacity)
        private set
    var scissorXY = LongArray(minRetainedCapacity)
        private set
    var scissorWH = LongArray(minRetainedCapacity)
        private set
    var textureMasks = IntArray(minRetainedCapacity)
        private set
    var texture0 = arrayOfNulls<TextureHandle>(minRetainedCapacity)
        private set
    var sampler0 = arrayOfNulls<PresetGpuSampler>(minRetainedCapacity)
        private set

    // Cold/lazy arrays allocated only when features are used
    var overflowTextures: Array<Array<TextureHandle?>?>? = null
        private set
    var overflowSamplers: Array<Array<PresetGpuSampler?>?>? = null
        private set

    var directVertexBuffer0: Array<VulkanVertexBuffer?>? = null
        private set
    var directVertexOffset0: LongArray? = null
        private set
    var overflowDirectVertexBuffers: Array<Array<VulkanVertexBuffer?>?>? = null
        private set
    var overflowDirectVertexOffsets: Array<LongArray?>? = null
        private set
    var directVertexBindingsMasks: IntArray? = null
        private set
    var directIndexBuffers: Array<VulkanIndexBuffer?>? = null
        private set
    var directFirstVertex: IntArray? = null
        private set
    var directFirstIndex: IntArray? = null
        private set
    var directBaseVertex: IntArray? = null
        private set
    var directInstanceCount: IntArray? = null
        private set
    var directFirstInstance: IntArray? = null
        private set

    var clearRed: FloatArray? = null
        private set
    var clearGreen: FloatArray? = null
        private set
    var clearBlue: FloatArray? = null
        private set
    var clearAlpha: FloatArray? = null
        private set
    var clearDepth: DoubleArray? = null
        private set

    var blitSources: Array<VulkanRenderTarget?>? = null
        private set
    var blitSrcX: IntArray? = null
        private set
    var blitSrcY: IntArray? = null
        private set
    var blitSrcW: IntArray? = null
        private set
    var blitSrcH: IntArray? = null
        private set
    var blitDstX: IntArray? = null
        private set
    var blitDstY: IntArray? = null
        private set
    var blitDstW: IntArray? = null
        private set
    var blitDstH: IntArray? = null
        private set
    var blitFlags: ByteArray? = null
        private set

    var debugGroupLabels: Array<String?>? = null
        private set
    var timestampPools: Array<PresetTimestampQueryPool?>? = null
        private set
    var timestampIndices: IntArray? = null
        private set

    private var hasAnyTextureThisFrame = false
    private var hasOverflowTexturesThisFrame = false
    private var hasDirectThisFrame = false
    private var hasOverflowDirectThisFrame = false
    private var hasBlitsThisFrame = false
    private var hasDebugGroupsThisFrame = false
    private var hasTimestampsThisFrame = false
    private var lowWaterFrames = 0
    private var peakSinceTrim = 0

    var size = 0
        private set

    internal val capacity: Int
        get() = flags.size

    fun obtain(): Int {
        val index = size
        if (index == flags.size) grow()
        size = index + 1
        return index
    }

    fun recordImmediate(
        program: Program,
        target: VulkanRenderTarget?,
        targetPassId: Int,
        pipelineKey: Long,
        vertexOffset: Long,
        vertexBytes: Long,
        vertexCount: Int,
        uboOffset: Long,
        uboBytes: Long,
        scissorXY: Long,
        scissorWH: Long,
        textureMask: Int,
        boundTextures: Array<TextureHandle?>
    ): Int {
        val index = obtain()
        flags[index] = 0
        programs[index] = program
        targets[index] = target
        targetPassIds[index] = targetPassId
        pipelineKeys[index] = pipelineKey
        vertexOffsets[index] = vertexOffset
        this.vertexBytes[index] = vertexBytes
        vertexCounts[index] = vertexCount
        uboOffsets[index] = uboOffset
        this.uboBytes[index] = uboBytes
        this.scissorXY[index] = scissorXY
        this.scissorWH[index] = scissorWH
        storeTextures(index, textureMask, boundTextures)
        return index
    }

    fun recordImmediateSingleTexture(
        program: Program,
        target: VulkanRenderTarget?,
        targetPassId: Int,
        pipelineKey: Long,
        vertexOffset: Long,
        vertexBytes: Long,
        vertexCount: Int,
        uboOffset: Long,
        uboBytes: Long,
        scissorXY: Long,
        scissorWH: Long,
        texture0: TextureHandle?
    ): Int {
        val index = obtain()
        flags[index] = 0
        programs[index] = program
        targets[index] = target
        targetPassIds[index] = targetPassId
        pipelineKeys[index] = pipelineKey
        vertexOffsets[index] = vertexOffset
        this.vertexBytes[index] = vertexBytes
        vertexCounts[index] = vertexCount
        uboOffsets[index] = uboOffset
        this.uboBytes[index] = uboBytes
        this.scissorXY[index] = scissorXY
        this.scissorWH[index] = scissorWH
        if (texture0 != null) {
            textureMasks[index] = 1
            this.texture0[index] = texture0
            this.sampler0[index] = (texture0 as? VulkanTexture)?.sampler
            hasAnyTextureThisFrame = true
        } else {
            textureMasks[index] = 0
        }
        return index
    }

    fun recordDirect(
        program: Program,
        target: VulkanRenderTarget?,
        targetPassId: Int,
        pipelineKey: Long,
        uboOffset: Long,
        uboBytes: Long,
        scissorXY: Long,
        scissorWH: Long,
        textureMask: Int,
        boundTextures: Array<TextureHandle?>,
        boundVertexBuffers: Array<VulkanVertexBuffer?>,
        boundVertexOffsets: LongArray,
        boundVertexBindingsMask: Int,
        indexBuffer: VulkanIndexBuffer?,
        indexed: Boolean,
        firstVertex: Int,
        firstIndex: Int,
        vertexCount: Int,
        baseVertex: Int,
        instanceCount: Int,
        firstInstance: Int
    ): Int {
        val index = obtain()
        ensureDirectArrays()
        flags[index] = if (indexed) (FLAG_DIRECT or FLAG_INDEXED).toByte() else FLAG_DIRECT.toByte()
        programs[index] = program
        targets[index] = target
        targetPassIds[index] = targetPassId
        pipelineKeys[index] = pipelineKey
        vertexOffsets[index] = 0L
        vertexBytes[index] = 0L
        vertexCounts[index] = vertexCount
        uboOffsets[index] = uboOffset
        this.uboBytes[index] = uboBytes
        this.scissorXY[index] = scissorXY
        this.scissorWH[index] = scissorWH
        storeTextures(index, textureMask, boundTextures)

        directVertexBindingsMasks!![index] = boundVertexBindingsMask
        if ((boundVertexBindingsMask and 1) != 0) {
            directVertexBuffer0!![index] = boundVertexBuffers[0]
            directVertexOffset0!![index] = boundVertexOffsets[0]
        } else {
            directVertexBuffer0!![index] = null
            directVertexOffset0!![index] = 0L
        }
        var extraMask = boundVertexBindingsMask and 1.inv()
        if (extraMask != 0) {
            ensureOverflowDirectArrays()
            val vbTable = overflowDirectVertexBuffers!!
            val voTable = overflowDirectVertexOffsets!!
            var slotBuffers = vbTable[index]
            var slotOffsets = voTable[index]
            if (slotBuffers == null) {
                slotBuffers = arrayOfNulls(VERTEX_BINDINGS)
                slotOffsets = LongArray(VERTEX_BINDINGS)
                vbTable[index] = slotBuffers
                voTable[index] = slotOffsets
            }
            while (extraMask != 0) {
                val slot = Integer.numberOfTrailingZeros(extraMask)
                slotBuffers[slot] = boundVertexBuffers[slot]
                slotOffsets!![slot] = boundVertexOffsets[slot]
                extraMask = extraMask and (extraMask - 1)
            }
            hasOverflowDirectThisFrame = true
        }
        directIndexBuffers!![index] = indexBuffer
        directFirstVertex!![index] = firstVertex
        directFirstIndex!![index] = firstIndex
        directBaseVertex!![index] = baseVertex
        directInstanceCount!![index] = instanceCount
        directFirstInstance!![index] = firstInstance
        hasDirectThisFrame = true
        return index
    }

    fun recordClear(
        target: VulkanRenderTarget?,
        targetPassId: Int,
        color: Boolean,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        depth: Boolean,
        depthValue: Double
    ): Int {
        val index = obtain()
        ensureClearArrays()
        var clearFlags = 0
        if (color) clearFlags = clearFlags or FLAG_CLEAR_COLOR
        if (depth) clearFlags = clearFlags or FLAG_CLEAR_DEPTH
        flags[index] = clearFlags.toByte()
        programs[index] = null
        targets[index] = target
        targetPassIds[index] = targetPassId
        textureMasks[index] = 0
        clearRed!![index] = red
        clearGreen!![index] = green
        clearBlue!![index] = blue
        clearAlpha!![index] = alpha
        clearDepth!![index] = depthValue
        return index
    }

    fun recordBlit(
        source: VulkanRenderTarget,
        destination: VulkanRenderTarget,
        targetPassId: Int,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int,
        dstX: Int,
        dstY: Int,
        dstWidth: Int,
        dstHeight: Int,
        linearFilter: Boolean,
        copyColor: Boolean,
        copyDepth: Boolean,
        nativeCopy: Boolean,
        vertexOffset: Long
    ): Int {
        val index = obtain()
        ensureBlitArrays()
        flags[index] = FLAG_BLIT.toByte()
        programs[index] = null
        targets[index] = destination
        targetPassIds[index] = targetPassId
        textureMasks[index] = 0
        vertexOffsets[index] = vertexOffset
        blitSources!![index] = source
        blitSrcX!![index] = srcX
        blitSrcY!![index] = srcY
        blitSrcW!![index] = srcWidth
        blitSrcH!![index] = srcHeight
        blitDstX!![index] = dstX
        blitDstY!![index] = dstY
        blitDstW!![index] = dstWidth
        blitDstH!![index] = dstHeight
        var packed = 0
        if (linearFilter) packed = packed or BLIT_FLAG_LINEAR
        if (copyColor) packed = packed or BLIT_FLAG_COLOR
        if (copyDepth) packed = packed or BLIT_FLAG_DEPTH
        if (nativeCopy) packed = packed or BLIT_FLAG_NATIVE
        blitFlags!![index] = packed.toByte()
        hasBlitsThisFrame = true
        return index
    }

    fun recordDebugGroupPush(label: String, targetPassId: Int = -1): Int {
        val index = obtain()
        ensureDebugGroupLabels()[index] = label
        flags[index] = FLAG_DEBUG_PUSH.toByte()
        programs[index] = null
        targets[index] = null
        targetPassIds[index] = targetPassId
        textureMasks[index] = 0
        hasDebugGroupsThisFrame = true
        return index
    }

    fun recordDebugGroupPop(targetPassId: Int = -1): Int {
        val index = obtain()
        flags[index] = FLAG_DEBUG_POP.toByte()
        programs[index] = null
        targets[index] = null
        targetPassIds[index] = targetPassId
        textureMasks[index] = 0
        hasDebugGroupsThisFrame = true
        return index
    }

    fun recordTimestamp(pool: PresetTimestampQueryPool, queryIndex: Int, targetPassId: Int = -1): Int {
        val index = obtain()
        ensureTimestampArrays()
        flags[index] = FLAG_TIMESTAMP.toByte()
        programs[index] = null
        targets[index] = null
        targetPassIds[index] = targetPassId
        textureMasks[index] = 0
        timestampPools!![index] = pool
        timestampIndices!![index] = queryIndex
        hasTimestampsThisFrame = true
        return index
    }

    fun sameTexturesAt(index: Int, boundTextures: Array<TextureHandle?>, boundMask: Int): Boolean {
        if (textureMasks[index] != boundMask) return false
        if (boundMask == 0) return true
        val t0 = boundTextures[0]
        if (texture0[index] !== t0) return false
        if (sampler0[index] !== (t0 as? VulkanTexture)?.sampler) return false
        var extra = boundMask and 1.inv()
        if (extra == 0) return true
        val slotOverflow = overflowTextures?.get(index) ?: return false
        val samplerOverflow = overflowSamplers?.get(index) ?: return false
        while (extra != 0) {
            val bit = Integer.numberOfTrailingZeros(extra)
            val tex = boundTextures[bit]
            if (slotOverflow[bit] !== tex) return false
            if (samplerOverflow[bit] !== (tex as? VulkanTexture)?.sampler) return false
            extra = extra and (extra - 1)
        }
        return true
    }

    private fun storeTextures(index: Int, textureMask: Int, boundTextures: Array<TextureHandle?>) {
        textureMasks[index] = textureMask
        if (textureMask == 0) return
        hasAnyTextureThisFrame = true
        val t0 = boundTextures[0]
        texture0[index] = t0
        sampler0[index] = (t0 as? VulkanTexture)?.sampler
        var extra = textureMask and 1.inv()
        if (extra == 0) return
        val table = ensureOverflowTextures()
        val samplerTable = ensureOverflowSamplers()
        var slotArray = table[index]
        var samplerArray = samplerTable[index]
        if (slotArray == null) {
            slotArray = arrayOfNulls(TEXTURE_UNITS)
            samplerArray = arrayOfNulls(TEXTURE_UNITS)
            table[index] = slotArray
            samplerTable[index] = samplerArray
        }
        while (extra != 0) {
            val bit = Integer.numberOfTrailingZeros(extra)
            val tex = boundTextures[bit]
            slotArray[bit] = tex
            samplerArray!![bit] = (tex as? VulkanTexture)?.sampler
            extra = extra and (extra - 1)
        }
        hasOverflowTexturesThisFrame = true
    }

    fun reset() {
        val previousSize = size
        if (previousSize > 0) {
            Arrays.fill(programs, 0, previousSize, null)
            Arrays.fill(targets, 0, previousSize, null)
            if (hasAnyTextureThisFrame) {
                Arrays.fill(texture0, 0, previousSize, null)
                Arrays.fill(sampler0, 0, previousSize, null)
            }
            if (hasOverflowTexturesThisFrame) {
                val overflow = overflowTextures
                val overflowSamp = overflowSamplers
                if (overflow != null) {
                    for (i in 0 until previousSize) {
                        var extra = textureMasks[i] and 1.inv()
                        if (extra != 0) {
                            val arr = overflow[i]
                            val sArr = overflowSamp?.get(i)
                            if (arr != null) {
                                while (extra != 0) {
                                    val bit = Integer.numberOfTrailingZeros(extra)
                                    arr[bit] = null
                                    if (sArr != null) sArr[bit] = null
                                    extra = extra and (extra - 1)
                                }
                            }
                        }
                    }
                }
            }
            if (hasDirectThisFrame) {
                directVertexBuffer0?.let { Arrays.fill(it, 0, previousSize, null) }
                directIndexBuffers?.let { Arrays.fill(it, 0, previousSize, null) }
                if (hasOverflowDirectThisFrame) {
                    val vbTable = overflowDirectVertexBuffers
                    val vbMasks = directVertexBindingsMasks
                    if (vbTable != null && vbMasks != null) {
                        for (i in 0 until previousSize) {
                            if ((flags[i].toInt() and FLAG_DIRECT) != 0) {
                                var extra = vbMasks[i] and 1.inv()
                                if (extra != 0) {
                                    val arr = vbTable[i]
                                    if (arr != null) {
                                        while (extra != 0) {
                                            val slot = Integer.numberOfTrailingZeros(extra)
                                            arr[slot] = null
                                            extra = extra and (extra - 1)
                                        }
                                    }
                                    vbMasks[i] = 0
                                }
                            }
                        }
                    }
                }
            }
            if (hasBlitsThisFrame) {
                blitSources?.let { Arrays.fill(it, 0, previousSize, null) }
            }
            if (hasDebugGroupsThisFrame) {
                debugGroupLabels?.let { Arrays.fill(it, 0, previousSize, null) }
            }
            if (hasTimestampsThisFrame) {
                timestampPools?.let { Arrays.fill(it, 0, previousSize, null) }
            }
        }
        size = 0
        hasAnyTextureThisFrame = false
        hasOverflowTexturesThisFrame = false
        hasDirectThisFrame = false
        hasOverflowDirectThisFrame = false
        hasBlitsThisFrame = false
        hasDebugGroupsThisFrame = false
        hasTimestampsThisFrame = false

        val currentCap = flags.size
        if (currentCap > minRetainedCapacity) {
            if (previousSize > peakSinceTrim) peakSinceTrim = previousSize
            if (previousSize <= (currentCap ushr 2)) {
                lowWaterFrames++
                if (lowWaterFrames >= lowWaterFramesThreshold) {
                    val targetSize = maxOf(minRetainedCapacity, nextPowerOfTwo(peakSinceTrim shl 1))
                        .coerceAtMost(currentCap)
                    if (targetSize < currentCap) {
                        resizeArrays(targetSize)
                    }
                    lowWaterFrames = 0
                    peakSinceTrim = 0
                }
            } else {
                lowWaterFrames = 0
                peakSinceTrim = 0
            }
        } else {
            lowWaterFrames = 0
            peakSinceTrim = 0
        }
    }

    private fun grow() {
        val newCap = maxOf(minRetainedCapacity, flags.size shl 1)
        resizeArrays(newCap)
    }

    private fun resizeArrays(newCap: Int) {
        flags = flags.copyOf(newCap)
        programs = programs.copyOf(newCap)
        targets = targets.copyOf(newCap)
        targetPassIds = targetPassIds.copyOf(newCap)
        pipelineKeys = pipelineKeys.copyOf(newCap)
        vertexOffsets = vertexOffsets.copyOf(newCap)
        vertexBytes = vertexBytes.copyOf(newCap)
        vertexCounts = vertexCounts.copyOf(newCap)
        uboOffsets = uboOffsets.copyOf(newCap)
        uboBytes = uboBytes.copyOf(newCap)
        scissorXY = scissorXY.copyOf(newCap)
        scissorWH = scissorWH.copyOf(newCap)
        textureMasks = textureMasks.copyOf(newCap)
        texture0 = texture0.copyOf(newCap)
        sampler0 = sampler0.copyOf(newCap)

        overflowTextures = overflowTextures?.copyOf(newCap)
        overflowSamplers = overflowSamplers?.copyOf(newCap)
        if (directVertexBuffer0 != null) {
            directVertexBuffer0 = directVertexBuffer0!!.copyOf(newCap)
            directVertexOffset0 = directVertexOffset0!!.copyOf(newCap)
            directVertexBindingsMasks = directVertexBindingsMasks!!.copyOf(newCap)
            directIndexBuffers = directIndexBuffers!!.copyOf(newCap)
            directFirstVertex = directFirstVertex!!.copyOf(newCap)
            directFirstIndex = directFirstIndex!!.copyOf(newCap)
            directBaseVertex = directBaseVertex!!.copyOf(newCap)
            directInstanceCount = directInstanceCount!!.copyOf(newCap)
            directFirstInstance = directFirstInstance!!.copyOf(newCap)
        }
        if (overflowDirectVertexBuffers != null) {
            overflowDirectVertexBuffers = overflowDirectVertexBuffers!!.copyOf(newCap)
            overflowDirectVertexOffsets = overflowDirectVertexOffsets!!.copyOf(newCap)
        }
        if (clearRed != null) {
            clearRed = clearRed!!.copyOf(newCap)
            clearGreen = clearGreen!!.copyOf(newCap)
            clearBlue = clearBlue!!.copyOf(newCap)
            clearAlpha = clearAlpha!!.copyOf(newCap)
            clearDepth = clearDepth!!.copyOf(newCap)
        }
        if (blitSources != null) {
            blitSources = blitSources!!.copyOf(newCap)
            blitSrcX = blitSrcX!!.copyOf(newCap)
            blitSrcY = blitSrcY!!.copyOf(newCap)
            blitSrcW = blitSrcW!!.copyOf(newCap)
            blitSrcH = blitSrcH!!.copyOf(newCap)
            blitDstX = blitDstX!!.copyOf(newCap)
            blitDstY = blitDstY!!.copyOf(newCap)
            blitDstW = blitDstW!!.copyOf(newCap)
            blitDstH = blitDstH!!.copyOf(newCap)
            blitFlags = blitFlags!!.copyOf(newCap)
        }
        debugGroupLabels = debugGroupLabels?.copyOf(newCap)
        if (timestampPools != null) {
            timestampPools = timestampPools!!.copyOf(newCap)
            timestampIndices = timestampIndices!!.copyOf(newCap)
        }
    }

    private fun ensureTimestampArrays() {
        if (timestampPools != null) return
        val cap = flags.size
        timestampPools = arrayOfNulls(cap)
        timestampIndices = IntArray(cap)
    }

    private fun ensureDebugGroupLabels(): Array<String?> {
        debugGroupLabels?.let { return it }
        return arrayOfNulls<String>(flags.size).also { debugGroupLabels = it }
    }

    private fun ensureOverflowTextures(): Array<Array<TextureHandle?>?> {
        overflowTextures?.let { return it }
        return arrayOfNulls<Array<TextureHandle?>?>(flags.size).also { overflowTextures = it }
    }

    private fun ensureOverflowSamplers(): Array<Array<PresetGpuSampler?>?> {
        overflowSamplers?.let { return it }
        return arrayOfNulls<Array<PresetGpuSampler?>?>(flags.size).also { overflowSamplers = it }
    }

    private fun ensureDirectArrays() {
        if (directVertexBuffer0 != null) return
        val cap = flags.size
        directVertexBuffer0 = arrayOfNulls(cap)
        directVertexOffset0 = LongArray(cap)
        directVertexBindingsMasks = IntArray(cap)
        directIndexBuffers = arrayOfNulls(cap)
        directFirstVertex = IntArray(cap)
        directFirstIndex = IntArray(cap)
        directBaseVertex = IntArray(cap)
        directInstanceCount = IntArray(cap)
        directFirstInstance = IntArray(cap)
    }

    private fun ensureOverflowDirectArrays() {
        if (overflowDirectVertexBuffers != null) return
        val cap = flags.size
        overflowDirectVertexBuffers = arrayOfNulls(cap)
        overflowDirectVertexOffsets = arrayOfNulls(cap)
    }

    private fun ensureClearArrays() {
        if (clearRed != null) return
        val cap = flags.size
        clearRed = FloatArray(cap)
        clearGreen = FloatArray(cap)
        clearBlue = FloatArray(cap)
        clearAlpha = FloatArray(cap)
        clearDepth = DoubleArray(cap)
    }

    private fun ensureBlitArrays() {
        if (blitSources != null) return
        val cap = flags.size
        blitSources = arrayOfNulls(cap)
        blitSrcX = IntArray(cap)
        blitSrcY = IntArray(cap)
        blitSrcW = IntArray(cap)
        blitSrcH = IntArray(cap)
        blitDstX = IntArray(cap)
        blitDstY = IntArray(cap)
        blitDstW = IntArray(cap)
        blitDstH = IntArray(cap)
        blitFlags = ByteArray(cap)
    }

    private fun nextPowerOfTwo(value: Int): Int {
        var cap = 1
        while (cap < value) cap = cap shl 1
        return cap
    }

    companion object {
        const val TEXTURE_UNITS = 16
        const val VERTEX_BINDINGS = 16
        const val DEFAULT_MIN_RETAINED_CAPACITY = 256
        const val DEFAULT_LOW_WATER_FRAMES = 60

        const val FLAG_DIRECT = 1 shl 0
        const val FLAG_INDEXED = 1 shl 1
        const val FLAG_CLEAR_COLOR = 1 shl 2
        const val FLAG_CLEAR_DEPTH = 1 shl 3
        const val FLAG_CLEAR_MASK = FLAG_CLEAR_COLOR or FLAG_CLEAR_DEPTH
        const val FLAG_DEBUG_PUSH = 1 shl 4
        const val FLAG_DEBUG_POP = 1 shl 5
        const val FLAG_BLIT = 1 shl 6
        const val FLAG_TIMESTAMP = 1 shl 7
        const val FLAG_SPECIAL_MASK = FLAG_CLEAR_MASK or FLAG_DEBUG_PUSH or FLAG_DEBUG_POP or FLAG_BLIT or FLAG_TIMESTAMP

        const val BLIT_FLAG_LINEAR = 1 shl 0
        const val BLIT_FLAG_COLOR = 1 shl 1
        const val BLIT_FLAG_DEPTH = 1 shl 2
        const val BLIT_FLAG_NATIVE = 1 shl 3
    }
}
