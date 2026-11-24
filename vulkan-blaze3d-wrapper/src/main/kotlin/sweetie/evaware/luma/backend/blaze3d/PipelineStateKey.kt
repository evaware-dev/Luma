package sweetie.evaware.luma.backend.blaze3d

import sweetie.evaware.luma.api.BlendFactor
import sweetie.evaware.luma.api.BlendFunction
import sweetie.evaware.luma.api.BlendOp
import sweetie.evaware.luma.api.DepthCompare
import sweetie.evaware.luma.api.PrimitiveType

internal object PipelineStateKey {
    private val PRIMITIVE_TYPES = PrimitiveType.entries.toTypedArray()
    private val DEPTH_COMPARES = DepthCompare.entries.toTypedArray()
    private val BLEND_FACTORS = BlendFactor.entries.toTypedArray()
    private val BLEND_OPS = BlendOp.entries.toTypedArray()

    const val COLOR_WRITE_RED = 1 shl 0
    const val COLOR_WRITE_GREEN = 1 shl 1
    const val COLOR_WRITE_BLUE = 1 shl 2
    const val COLOR_WRITE_ALPHA = 1 shl 3
    const val DEFAULT_COLOR_WRITE_MASK = COLOR_WRITE_RED or COLOR_WRITE_GREEN or COLOR_WRITE_BLUE or COLOR_WRITE_ALPHA

    private const val TOPOLOGY_MASK = 0x3L
    private const val BLEND_ENABLED_BIT = 1L shl 2
    private const val DEPTH_ENABLED_BIT = 1L shl 3
    private const val DEPTH_WRITE_BIT = 1L shl 4
    private const val CULL_ENABLED_BIT = 1L shl 5
    private const val DEPTH_FUNC_SHIFT = 6
    private const val DEPTH_FUNC_MASK = 0x7L
    private const val SRC_COLOR_SHIFT = 9
    private const val DST_COLOR_SHIFT = 13
    private const val SRC_ALPHA_SHIFT = 17
    private const val DST_ALPHA_SHIFT = 21
    private const val BLEND_FACTOR_MASK = 0xFL
    private const val COLOR_OP_SHIFT = 25
    private const val ALPHA_OP_SHIFT = 28
    private const val BLEND_OP_MASK = 0x7L
    private const val COLOR_WRITE_MASK_SHIFT = 31
    private const val COLOR_WRITE_MASK_BITS = 0xFL

    const val DEPTH_ATTACHMENT_BIT = Long.MIN_VALUE

    val DEFAULT_STATE_BITS: Long = packState(
        blendEnabled = true,
        blendFunction = BlendFunction.TRANSLUCENT,
        depthEnabled = false,
        depthWrite = false,
        depthFunc = DepthCompare.ALWAYS,
        cullEnabled = false,
        colorWriteMask = DEFAULT_COLOR_WRITE_MASK
    )

    fun packState(
        blendEnabled: Boolean,
        blendFunction: BlendFunction,
        depthEnabled: Boolean,
        depthWrite: Boolean,
        depthFunc: DepthCompare,
        cullEnabled: Boolean,
        colorWriteMask: Int = DEFAULT_COLOR_WRITE_MASK
    ): Long {
        val effectiveDepthWrite = depthEnabled && depthWrite
        val effectiveDepthFunc = if (depthEnabled) depthFunc.ordinal else DepthCompare.ALWAYS.ordinal
        var bits = 0L
        if (blendEnabled) bits = bits or BLEND_ENABLED_BIT
        if (depthEnabled) bits = bits or DEPTH_ENABLED_BIT
        if (effectiveDepthWrite) bits = bits or DEPTH_WRITE_BIT
        if (cullEnabled) bits = bits or CULL_ENABLED_BIT
        bits = bits or (effectiveDepthFunc.toLong() shl DEPTH_FUNC_SHIFT)
        bits = bits or (blendFunction.sourceColor.ordinal.toLong() shl SRC_COLOR_SHIFT)
        bits = bits or (blendFunction.destinationColor.ordinal.toLong() shl DST_COLOR_SHIFT)
        bits = bits or (blendFunction.sourceAlpha.ordinal.toLong() shl SRC_ALPHA_SHIFT)
        bits = bits or (blendFunction.destinationAlpha.ordinal.toLong() shl DST_ALPHA_SHIFT)
        bits = bits or (blendFunction.colorOp.ordinal.toLong() shl COLOR_OP_SHIFT)
        bits = bits or (blendFunction.alphaOp.ordinal.toLong() shl ALPHA_OP_SHIFT)
        bits = bits or ((colorWriteMask.toLong() and COLOR_WRITE_MASK_BITS) shl COLOR_WRITE_MASK_SHIFT)
        return bits
    }

    fun pack(
        topology: PrimitiveType,
        blendEnabled: Boolean,
        blendFunction: BlendFunction,
        depthEnabled: Boolean,
        depthWrite: Boolean,
        depthFunc: DepthCompare,
        cullEnabled: Boolean,
        colorWriteMask: Int = DEFAULT_COLOR_WRITE_MASK
    ): Long = packState(
        blendEnabled,
        blendFunction,
        depthEnabled,
        depthWrite,
        depthFunc,
        cullEnabled,
        colorWriteMask
    ) or topology.ordinal.toLong()

    fun unpackTopology(key: Long): PrimitiveType = PRIMITIVE_TYPES[(key and TOPOLOGY_MASK).toInt()]

    fun unpackBlendEnabled(key: Long): Boolean = (key and BLEND_ENABLED_BIT) != 0L

    fun unpackDepthEnabled(key: Long): Boolean = (key and DEPTH_ENABLED_BIT) != 0L

    fun unpackDepthWrite(key: Long): Boolean = (key and DEPTH_WRITE_BIT) != 0L

    fun unpackCullEnabled(key: Long): Boolean = (key and CULL_ENABLED_BIT) != 0L

    fun unpackDepthFunc(key: Long): DepthCompare = DEPTH_COMPARES[((key ushr DEPTH_FUNC_SHIFT) and DEPTH_FUNC_MASK).toInt()]

    fun unpackColorWriteMask(key: Long): Int = ((key ushr COLOR_WRITE_MASK_SHIFT) and COLOR_WRITE_MASK_BITS).toInt()

    fun unpackBlendFunction(key: Long): BlendFunction {
        val srcColor = BLEND_FACTORS[((key ushr SRC_COLOR_SHIFT) and BLEND_FACTOR_MASK).toInt()]
        val dstColor = BLEND_FACTORS[((key ushr DST_COLOR_SHIFT) and BLEND_FACTOR_MASK).toInt()]
        val srcAlpha = BLEND_FACTORS[((key ushr SRC_ALPHA_SHIFT) and BLEND_FACTOR_MASK).toInt()]
        val dstAlpha = BLEND_FACTORS[((key ushr DST_ALPHA_SHIFT) and BLEND_FACTOR_MASK).toInt()]
        val colorOp = BLEND_OPS[((key ushr COLOR_OP_SHIFT) and BLEND_OP_MASK).toInt()]
        val alphaOp = BLEND_OPS[((key ushr ALPHA_OP_SHIFT) and BLEND_OP_MASK).toInt()]
        return BlendFunction(srcColor, dstColor, colorOp, srcAlpha, dstAlpha, alphaOp)
    }

    fun packScissorXY(x: Int, y: Int): Long =
        (x.toLong() shl 32) or (y.toLong() and 0xFFFF_FFFFL)

    fun packScissorWH(width: Int, height: Int): Long =
        (width.toLong() shl 32) or (height.toLong() and 0xFFFF_FFFFL)

    fun unpackHigh(packed: Long): Int = (packed ushr 32).toInt()

    fun unpackLow(packed: Long): Int = packed.toInt()
}
