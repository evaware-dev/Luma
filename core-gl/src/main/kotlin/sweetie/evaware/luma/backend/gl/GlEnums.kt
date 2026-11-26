package sweetie.evaware.luma.backend.gl

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30
import sweetie.evaware.luma.api.BlendFactor
import sweetie.evaware.luma.api.BlendOp
import sweetie.evaware.luma.api.DepthCompare
import sweetie.evaware.luma.api.IndexType
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.StencilCompare
import sweetie.evaware.luma.api.StencilOp
import sweetie.evaware.luma.vertex.ShaderVertType

internal val ShaderVertType.glType: Int
    get() = when (this) {
        ShaderVertType.FLOAT32 -> GL11.GL_FLOAT
        ShaderVertType.FLOAT16 -> GL30.GL_HALF_FLOAT
        ShaderVertType.INT8 -> GL11.GL_BYTE
        ShaderVertType.UINT8 -> GL11.GL_UNSIGNED_BYTE
        ShaderVertType.INT16 -> GL11.GL_SHORT
        ShaderVertType.UINT16 -> GL11.GL_UNSIGNED_SHORT
        ShaderVertType.INT32 -> GL11.GL_INT
        ShaderVertType.UINT32 -> GL11.GL_UNSIGNED_INT
    }

internal object GlEnums {
    fun depthFunction(compare: DepthCompare): Int = when (compare) {
        DepthCompare.ALWAYS -> GL11.GL_ALWAYS
        DepthCompare.LESS -> GL11.GL_LESS
        DepthCompare.LESS_OR_EQUAL -> GL11.GL_LEQUAL
        DepthCompare.EQUAL -> GL11.GL_EQUAL
        DepthCompare.NOT_EQUAL -> GL11.GL_NOTEQUAL
        DepthCompare.GREATER_OR_EQUAL -> GL11.GL_GEQUAL
        DepthCompare.GREATER -> GL11.GL_GREATER
        DepthCompare.NEVER -> GL11.GL_NEVER
    }

    fun stencilFunction(compare: StencilCompare): Int = when (compare) {
        StencilCompare.ALWAYS -> GL11.GL_ALWAYS
        StencilCompare.LESS -> GL11.GL_LESS
        StencilCompare.LESS_OR_EQUAL -> GL11.GL_LEQUAL
        StencilCompare.EQUAL -> GL11.GL_EQUAL
        StencilCompare.NOT_EQUAL -> GL11.GL_NOTEQUAL
        StencilCompare.GREATER_OR_EQUAL -> GL11.GL_GEQUAL
        StencilCompare.GREATER -> GL11.GL_GREATER
        StencilCompare.NEVER -> GL11.GL_NEVER
    }

    fun stencilOperation(op: StencilOp): Int = when (op) {
        StencilOp.KEEP -> GL11.GL_KEEP
        StencilOp.ZERO -> GL11.GL_ZERO
        StencilOp.REPLACE -> GL11.GL_REPLACE
        StencilOp.INCR -> GL11.GL_INCR
        StencilOp.INCR_WRAP -> GL14.GL_INCR_WRAP
        StencilOp.DECR -> GL11.GL_DECR
        StencilOp.DECR_WRAP -> GL14.GL_DECR_WRAP
        StencilOp.INVERT -> GL11.GL_INVERT
    }

    fun primitiveMode(type: PrimitiveType): Int = when (type) {
        PrimitiveType.TRIANGLES -> GL11.GL_TRIANGLES
        PrimitiveType.LINES -> GL11.GL_LINES
        PrimitiveType.QUADS -> GL11.GL_TRIANGLES
    }

    fun indexType(type: IndexType): Int = when (type) {
        IndexType.UINT16 -> GL11.GL_UNSIGNED_SHORT
        IndexType.UINT32 -> GL11.GL_UNSIGNED_INT
    }

    fun blendFactor(factor: BlendFactor): Int = when (factor) {
        BlendFactor.ZERO -> GL11.GL_ZERO
        BlendFactor.ONE -> GL11.GL_ONE
        BlendFactor.SRC_COLOR -> GL11.GL_SRC_COLOR
        BlendFactor.ONE_MINUS_SRC_COLOR -> GL11.GL_ONE_MINUS_SRC_COLOR
        BlendFactor.DST_COLOR -> GL11.GL_DST_COLOR
        BlendFactor.ONE_MINUS_DST_COLOR -> GL11.GL_ONE_MINUS_DST_COLOR
        BlendFactor.SRC_ALPHA -> GL11.GL_SRC_ALPHA
        BlendFactor.ONE_MINUS_SRC_ALPHA -> GL11.GL_ONE_MINUS_SRC_ALPHA
        BlendFactor.DST_ALPHA -> GL11.GL_DST_ALPHA
        BlendFactor.ONE_MINUS_DST_ALPHA -> GL11.GL_ONE_MINUS_DST_ALPHA
        BlendFactor.CONSTANT_COLOR -> GL14.GL_CONSTANT_COLOR
        BlendFactor.ONE_MINUS_CONSTANT_COLOR -> GL14.GL_ONE_MINUS_CONSTANT_COLOR
        BlendFactor.CONSTANT_ALPHA -> GL14.GL_CONSTANT_ALPHA
        BlendFactor.ONE_MINUS_CONSTANT_ALPHA -> GL14.GL_ONE_MINUS_CONSTANT_ALPHA
        BlendFactor.SRC_ALPHA_SATURATE -> GL11.GL_SRC_ALPHA_SATURATE
    }

    fun blendOp(op: BlendOp): Int = when (op) {
        BlendOp.ADD -> GL14.GL_FUNC_ADD
        BlendOp.SUBTRACT -> GL14.GL_FUNC_SUBTRACT
        BlendOp.REVERSE_SUBTRACT -> GL14.GL_FUNC_REVERSE_SUBTRACT
        BlendOp.MIN -> GL14.GL_MIN
        BlendOp.MAX -> GL14.GL_MAX
    }
}
