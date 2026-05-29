package sweetie.evaware.renderutil.renderers

import org.lwjgl.opengl.GL11
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.matrix.MatrixControl
import sweetie.evaware.luma.scissor.ScissorControl
import sweetie.evaware.luma.shader.Shader
import sweetie.evaware.luma.texture.TextureAtlas
import sweetie.evaware.luma.uniform.Int1Uniform
import sweetie.evaware.luma.uniform.Mat4Uniform
import sweetie.evaware.renderutil.RenderStats
import sweetie.evaware.renderutil.api.BatchRenderer
import sweetie.evaware.renderutil.helper.ColorUtil

internal class UberBatch : BatchRenderer, AutoCloseable {
    private val shader = Shader(
        "assets/luma-renderer/shaders/core/uber.frag",
        "assets/luma-renderer/shaders/core/uber.vert"
    ).drawMode(GL11.GL_TRIANGLES)

    private val uMatrix: Mat4Uniform
    private val uTexture: Int1Uniform
    private var scissorVersion = Int.MIN_VALUE
    private var scissorMinX = 0f
    private var scissorMinY = 0f
    private var scissorMaxX = 0f
    private var scissorMaxY = 0f

    init {
        with(shader) {
            vertices.float(2, 0)
            vertices.float(2, 1)
            vertices.float(4, 2)
            vertices.float(4, 3)
            uMatrix = uniforms.mat4("uMatrix")
            uTexture = uniforms.int1("uTexture")
        }
    }

    override fun load() {
        shader.load()
    }

    override fun hasPending() = shader.vertices.hasVertices()

    fun rect(x: Float, y: Float, width: Float, height: Float, color: Int) {
        cacheScissor()
        quad(TextureAtlas.whiteRegion(), x, y, width, height, color)
    }

    fun texture(id: String, x: Float, y: Float, width: Float, height: Float, color: Int) {
        cacheScissor()
        quad(TextureAtlas.region(id), x, y, width, height, color)
    }

    override fun flush() {
        if (!hasPending()) return

        shader.attach()
        shader.uniforms.int1(uTexture, 0)
        Luma.applyGameMatrix(shader.uniforms, uMatrix)
        Luma.bindTexture(TextureAtlas.texture())
        RenderStats.markBatch()
        Luma.drawShader(shader)
        RenderStats.markDrawCall()
        shader.detach()
    }

    override fun close() {
        shader.close()
    }

    private fun quad(
        region: TextureAtlas.Region,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Int
    ) {
        val red = ColorUtil.redf(color)
        val green = ColorUtil.greenf(color)
        val blue = ColorUtil.bluef(color)
        val alpha = ColorUtil.alphaf(color)
        putQuad(
            x,
            y,
            x + width,
            y + height,
            region.uOffset,
            region.vOffset,
            region.uOffset + region.uScale,
            region.vOffset + region.vScale,
            red,
            green,
            blue,
            alpha
        )
    }

    private fun putQuad(
        minX: Float,
        minY: Float,
        maxX: Float,
        maxY: Float,
        minU: Float,
        minV: Float,
        maxU: Float,
        maxV: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) {
        putVertex(minX, minY, minU, minV, red, green, blue, alpha)
        putVertex(minX, maxY, minU, maxV, red, green, blue, alpha)
        putVertex(maxX, maxY, maxU, maxV, red, green, blue, alpha)
        putVertex(minX, minY, minU, minV, red, green, blue, alpha)
        putVertex(maxX, maxY, maxU, maxV, red, green, blue, alpha)
        putVertex(maxX, minY, maxU, minV, red, green, blue, alpha)
    }

    private fun putVertex(
        x: Float,
        y: Float,
        u: Float,
        v: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) {
        shader.vertices
            .vec2(MatrixControl.transformX(x, y), MatrixControl.transformY(x, y))
            .vec2(u, v)
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
