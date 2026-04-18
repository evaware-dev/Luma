package sweetie.evaware.renderutil.renderers

import kotlin.math.min
import org.lwjgl.opengl.GL11
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.matrix.MatrixControl
import sweetie.evaware.luma.scissor.ScissorControl
import sweetie.evaware.luma.shader.Shader
import sweetie.evaware.luma.uniform.Mat4Uniform
import sweetie.evaware.renderutil.RenderStats
import sweetie.evaware.renderutil.api.BatchRenderer
import sweetie.evaware.renderutil.helper.ColorUtil

internal class RectQuadsRenderer : BatchRenderer, AutoCloseable {
    private val shader = Shader(
        "assets/luma-renderer/shaders/core/rect_quad.frag",
        "assets/luma-renderer/shaders/core/rect_quad.vert"
    ).drawMode(GL11.GL_TRIANGLES)

    private val uMatrix: Mat4Uniform
    private var scissorVersion = Int.MIN_VALUE
    private var scissorMinX = 0f
    private var scissorMinY = 0f
    private var scissorMaxX = 0f
    private var scissorMaxY = 0f

    init {
        with(shader) {
            vertices.float(2, 0)
            vertices.float(2, 1)
            vertices.float(2, 2)
            vertices.float(4, 3)
            vertices.float(4, 4)
            vertices.float(4, 5)
            uMatrix = uniforms.mat4("uMatrix")
        }
    }

    override fun load() {
        shader.load()
    }

    override fun hasPending() = shader.vertices.hasVertices()

    fun rect(x: Float, y: Float, width: Float, height: Float, color: Int, radius: Float) {
        rect(x, y, width, height, color, radius, radius, radius, radius)
    }

    fun rect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Int,
        topLeftRadius: Float,
        topRightRadius: Float,
        bottomRightRadius: Float,
        bottomLeftRadius: Float
    ) {
        val maxRadius = min(width, height) * 0.5f
        val minX = x
        val minY = y
        val maxX = x + width
        val maxY = y + height
        val topLeft = topLeftRadius.coerceIn(0f, maxRadius)
        val topRight = topRightRadius.coerceIn(0f, maxRadius)
        val bottomRight = bottomRightRadius.coerceIn(0f, maxRadius)
        val bottomLeft = bottomLeftRadius.coerceIn(0f, maxRadius)
        val red = ColorUtil.redf(color)
        val green = ColorUtil.greenf(color)
        val blue = ColorUtil.bluef(color)
        val alpha = ColorUtil.alphaf(color)

        cacheScissor()

        putVertex(minX, minY, 0f, 0f, width, height, topLeft, topRight, bottomRight, bottomLeft, red, green, blue, alpha)
        putVertex(minX, maxY, 0f, height, width, height, topLeft, topRight, bottomRight, bottomLeft, red, green, blue, alpha)
        putVertex(maxX, maxY, width, height, width, height, topLeft, topRight, bottomRight, bottomLeft, red, green, blue, alpha)
        putVertex(minX, minY, 0f, 0f, width, height, topLeft, topRight, bottomRight, bottomLeft, red, green, blue, alpha)
        putVertex(maxX, maxY, width, height, width, height, topLeft, topRight, bottomRight, bottomLeft, red, green, blue, alpha)
        putVertex(maxX, minY, width, 0f, width, height, topLeft, topRight, bottomRight, bottomLeft, red, green, blue, alpha)
    }

    override fun flush() {
        if (!hasPending()) return
        shader.attach()
        Luma.applyGameMatrix(shader.uniforms, uMatrix)
        RenderStats.markBatch()
        Luma.drawShader(shader)
        RenderStats.markDrawCall()
        shader.detach()
    }

    override fun close() {
        shader.close()
    }

    private fun putVertex(
        x: Float,
        y: Float,
        localX: Float,
        localY: Float,
        width: Float,
        height: Float,
        topLeftRadius: Float,
        topRightRadius: Float,
        bottomRightRadius: Float,
        bottomLeftRadius: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) {
        shader.vertices
            .vec2(MatrixControl.transformX(x, y), MatrixControl.transformY(x, y))
            .vec2(localX, localY)
            .vec2(width, height)
            .vec4(topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius)
            .vec4(red, green, blue, alpha)
            .vec4(scissorMinX, scissorMinY, scissorMaxX, scissorMaxY)
    }

    private fun cacheScissor() {
        val version = ScissorControl.version()
        if (version == scissorVersion) return
        scissorVersion = version
        scissorMinX = ScissorControl.minX()
        scissorMinY = ScissorControl.minY()
        scissorMaxX = ScissorControl.maxX()
        scissorMaxY = ScissorControl.maxY()
    }
}
