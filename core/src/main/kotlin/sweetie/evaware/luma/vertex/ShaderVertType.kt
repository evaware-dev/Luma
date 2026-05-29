package sweetie.evaware.luma.vertex

import org.lwjgl.opengl.GL11

@Suppress("unused")
enum class ShaderVertType(val glType: Int, val byteSize: Int) {
    FLOAT(GL11.GL_FLOAT, Float.SIZE_BYTES)
}
