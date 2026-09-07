package sweetie.evaware.luma.api

enum class BlendFactor {
    ZERO,
    ONE,
    SRC_COLOR,
    ONE_MINUS_SRC_COLOR,
    DST_COLOR,
    ONE_MINUS_DST_COLOR,
    SRC_ALPHA,
    ONE_MINUS_SRC_ALPHA,
    DST_ALPHA,
    ONE_MINUS_DST_ALPHA,
    CONSTANT_COLOR,
    ONE_MINUS_CONSTANT_COLOR,
    CONSTANT_ALPHA,
    ONE_MINUS_CONSTANT_ALPHA,
    SRC_ALPHA_SATURATE
}

enum class BlendOp {
    ADD,
    SUBTRACT,
    REVERSE_SUBTRACT,
    MIN,
    MAX
}

data class BlendFunction(
    val sourceColor: BlendFactor,
    val destinationColor: BlendFactor,
    val colorOp: BlendOp = BlendOp.ADD,
    val sourceAlpha: BlendFactor = sourceColor,
    val destinationAlpha: BlendFactor = destinationColor,
    val alphaOp: BlendOp = colorOp
) {
    companion object {
        @JvmField
        val TRANSLUCENT = BlendFunction(
            BlendFactor.SRC_ALPHA,
            BlendFactor.ONE_MINUS_SRC_ALPHA,
            sourceAlpha = BlendFactor.ONE,
            destinationAlpha = BlendFactor.ONE_MINUS_SRC_ALPHA
        )

        @JvmField
        val PREMULTIPLIED_ALPHA = BlendFunction(
            BlendFactor.ONE,
            BlendFactor.ONE_MINUS_SRC_ALPHA,
            sourceAlpha = BlendFactor.ONE,
            destinationAlpha = BlendFactor.ONE_MINUS_SRC_ALPHA
        )

        @JvmField
        val OVERLAY = BlendFunction(
            BlendFactor.SRC_ALPHA,
            BlendFactor.ONE,
            sourceAlpha = BlendFactor.ONE,
            destinationAlpha = BlendFactor.ZERO
        )
    }
}
