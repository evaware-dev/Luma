package sweetie.evaware.luma.opengl

import org.lwjgl.opengl.GL11
import sweetie.evaware.luma.wrapper.api.DrawMode
import sweetie.evaware.luma.wrapper.vertex.ShaderVertType

internal object OpenGlMappings {
    fun drawMode(drawMode: DrawMode) = when (drawMode) {
        DrawMode.TRIANGLES -> GL11.GL_TRIANGLES
        DrawMode.TRIANGLE_STRIP -> GL11.GL_TRIANGLE_STRIP
        DrawMode.TRIANGLE_FAN -> GL11.GL_TRIANGLE_FAN
        DrawMode.LINES -> GL11.GL_LINES
        DrawMode.LINE_STRIP -> GL11.GL_LINE_STRIP
        DrawMode.POINTS -> GL11.GL_POINTS
    }

    fun fromGl(drawMode: Int) = when (drawMode) {
        GL11.GL_TRIANGLES -> DrawMode.TRIANGLES
        GL11.GL_TRIANGLE_STRIP -> DrawMode.TRIANGLE_STRIP
        GL11.GL_TRIANGLE_FAN -> DrawMode.TRIANGLE_FAN
        GL11.GL_LINES -> DrawMode.LINES
        GL11.GL_LINE_STRIP -> DrawMode.LINE_STRIP
        GL11.GL_POINTS -> DrawMode.POINTS
        else -> error("Unsupported GL draw mode: $drawMode")
    }

    fun vertexType(type: ShaderVertType) = when (type) {
        ShaderVertType.FLOAT -> GL11.GL_FLOAT
    }
}
