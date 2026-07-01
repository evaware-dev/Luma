package sweetie.evaware.renderutil.renderers

import kotlin.math.min
import sweetie.evaware.luma.matrix.MatrixControl
import sweetie.evaware.luma.shader.BaseShader
import sweetie.evaware.luma.uniform.Mat4Uniform
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.renderutil.api.BatchRenderer
import sweetie.evaware.renderutil.helper.ColorUtil
import sweetie.evaware.renderutil.helper.ScissorCache

class RoundedRectShader : BaseShader("rounded_rect.frag", "rounded_rect.vert") {
    lateinit var uMatrix: Mat4Uniform

    override fun setupLayout() {
        drawMode(PrimitiveType.QUADS)
        vertices.float(2, 0)
        vertices.float(2, 1)
        vertices.float(4, 2)
        vertices.float(4, 3)
        vertices.float(4, 4)
        uMatrix = uniforms.mat4("uMatrix")
    }
}

class RoundedRectBatch : BatchRenderer, AutoCloseable {
    private val shader = RoundedRectShader()
    private val scissor = ScissorCache()

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
        val topLeft = topLeftRadius.coerceIn(0f, maxRadius)
        val topRight = topRightRadius.coerceIn(0f, maxRadius)
        val bottomRight = bottomRightRadius.coerceIn(0f, maxRadius)
        val bottomLeft = bottomLeftRadius.coerceIn(0f, maxRadius)
        val red = ColorUtil.redf(color)
        val green = ColorUtil.greenf(color)
        val blue = ColorUtil.bluef(color)
        val alpha = ColorUtil.alphaf(color)

        scissor.update(this)

        repeat(4) {
            shader.vertices
                .vec2(x, y)
                .vec2(width, height)
                .vec4(topLeft, topRight, bottomRight, bottomLeft)
                .vec4(red, green, blue, alpha)
                .vec4(scissor.minX, scissor.minY, scissor.maxX, scissor.maxY)
        }
    }

    override fun flush() {
        if (!hasPending()) return
        shader.attach()
        shader.uniforms.mat4(shader.uMatrix, MatrixControl.current())
        shader.draw()
    }

    override fun close() {
        shader.close()
    }
}
