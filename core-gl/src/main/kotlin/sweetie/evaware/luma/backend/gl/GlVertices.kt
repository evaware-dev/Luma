package sweetie.evaware.luma.backend.gl

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
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
