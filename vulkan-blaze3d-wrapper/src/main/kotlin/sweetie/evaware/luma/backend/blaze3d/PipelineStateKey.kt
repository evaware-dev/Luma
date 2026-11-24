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

    private const val TOPOLOGY_MASK = 0x3
    private const val BLEND_ENABLED_BIT = 1 shl 2
    private const val DEPTH_ENABLED_BIT = 1 shl 3
    private const val DEPTH_WRITE_BIT = 1 shl 4
    private const val CULL_ENABLED_BIT = 1 shl 5
    private const val DEPTH_FUNC_SHIFT = 6
    private const val DEPTH_FUNC_MASK = 0x7
    private const val SRC_COLOR_SHIFT = 9
    private const val DST_COLOR_SHIFT = 13
    private const val SRC_ALPHA_SHIFT = 17
    private const val DST_ALPHA_SHIFT = 21
    private const val BLEND_FACTOR_MASK = 0xF
    private const val COLOR_OP_SHIFT = 25
    private const val ALPHA_OP_SHIFT = 28
    private const val BLEND_OP_MASK = 0x7

    const val DEPTH_ATTACHMENT_BIT = Int.MIN_VALUE

    val DEFAULT_STATE_BITS: Int = packState(
        blendEnabled = true,
        blendFunction = BlendFunction.TRANSLUCENT,
        depthEnabled = false,
        depthWrite = false,
        depthFunc = DepthCompare.ALWAYS,
        cullEnabled = false
    )

    fun packState(
        blendEnabled: Boolean,
        blendFunction: BlendFunction,
        depthEnabled: Boolean,
        depthWrite: Boolean,
        depthFunc: DepthCompare,
        cullEnabled: Boolean
    ): Int {
        val effectiveDepthWrite = depthEnabled && depthWrite
        val effectiveDepthFunc = if (depthEnabled) depthFunc.ordinal else DepthCompare.ALWAYS.ordinal
        var bits = 0
        if (blendEnabled) bits = bits or BLEND_ENABLED_BIT
        if (depthEnabled) bits = bits or DEPTH_ENABLED_BIT
        if (effectiveDepthWrite) bits = bits or DEPTH_WRITE_BIT
        if (cullEnabled) bits = bits or CULL_ENABLED_BIT
        bits = bits or (effectiveDepthFunc shl DEPTH_FUNC_SHIFT)
        bits = bits or (blendFunction.sourceColor.ordinal shl SRC_COLOR_SHIFT)
        bits = bits or (blendFunction.destinationColor.ordinal shl DST_COLOR_SHIFT)
        bits = bits or (blendFunction.sourceAlpha.ordinal shl SRC_ALPHA_SHIFT)
        bits = bits or (blendFunction.destinationAlpha.ordinal shl DST_ALPHA_SHIFT)
        bits = bits or (blendFunction.colorOp.ordinal shl COLOR_OP_SHIFT)
        bits = bits or (blendFunction.alphaOp.ordinal shl ALPHA_OP_SHIFT)
        return bits
    }

    fun pack(
        topology: PrimitiveType,
        blendEnabled: Boolean,
        blendFunction: BlendFunction,
        depthEnabled: Boolean,
        depthWrite: Boolean,
        depthFunc: DepthCompare,
        cullEnabled: Boolean
    ): Int = packState(blendEnabled, blendFunction, depthEnabled, depthWrite, depthFunc, cullEnabled) or topology.ordinal

    fun unpackTopology(key: Int): PrimitiveType = PRIMITIVE_TYPES[key and TOPOLOGY_MASK]

    fun unpackBlendEnabled(key: Int): Boolean = (key and BLEND_ENABLED_BIT) != 0

    fun unpackDepthEnabled(key: Int): Boolean = (key and DEPTH_ENABLED_BIT) != 0

    fun unpackDepthWrite(key: Int): Boolean = (key and DEPTH_WRITE_BIT) != 0

    fun unpackCullEnabled(key: Int): Boolean = (key and CULL_ENABLED_BIT) != 0

    fun unpackDepthFunc(key: Int): DepthCompare = DEPTH_COMPARES[(key ushr DEPTH_FUNC_SHIFT) and DEPTH_FUNC_MASK]

    fun unpackBlendFunction(key: Int): BlendFunction {
        val srcColor = BLEND_FACTORS[(key ushr SRC_COLOR_SHIFT) and BLEND_FACTOR_MASK]
        val dstColor = BLEND_FACTORS[(key ushr DST_COLOR_SHIFT) and BLEND_FACTOR_MASK]
        val srcAlpha = BLEND_FACTORS[(key ushr SRC_ALPHA_SHIFT) and BLEND_FACTOR_MASK]
        val dstAlpha = BLEND_FACTORS[(key ushr DST_ALPHA_SHIFT) and BLEND_FACTOR_MASK]
        val colorOp = BLEND_OPS[(key ushr COLOR_OP_SHIFT) and BLEND_OP_MASK]
        val alphaOp = BLEND_OPS[(key ushr ALPHA_OP_SHIFT) and BLEND_OP_MASK]
        return BlendFunction(srcColor, dstColor, colorOp, srcAlpha, dstAlpha, alphaOp)
    }

    fun packScissorXY(x: Int, y: Int): Long =
        (x.toLong() shl 32) or (y.toLong() and 0xFFFF_FFFFL)

    fun packScissorWH(width: Int, height: Int): Long =
        (width.toLong() shl 32) or (height.toLong() and 0xFFFF_FFFFL)

    fun unpackHigh(packed: Long): Int = (packed ushr 32).toInt()

    fun unpackLow(packed: Long): Int = packed.toInt()
}
