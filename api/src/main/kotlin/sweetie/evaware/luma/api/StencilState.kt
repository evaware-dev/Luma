package sweetie.evaware.luma.api

enum class StencilCompare {
    ALWAYS,
    LESS,
    LESS_OR_EQUAL,
    EQUAL,
    NOT_EQUAL,
    GREATER_OR_EQUAL,
    GREATER,
    NEVER
}

enum class StencilOp {
    KEEP,
    ZERO,
    REPLACE,
    INCR,
    INCR_WRAP,
    DECR,
    DECR_WRAP,
    INVERT
}
