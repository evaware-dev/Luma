package sweetie.evaware.renderutil.renderers

import sweetie.evaware.luma.matrix.MatrixControl
import sweetie.evaware.luma.scissor.ScissorControl
import sweetie.evaware.luma.shader.BaseShader
import sweetie.evaware.luma.texture.TextureAtlas
import sweetie.evaware.luma.uniform.Mat4Uniform
import sweetie.evaware.luma.uniform.Int1Uniform
import sweetie.evaware.renderutil.api.BatchRenderer
import sweetie.evaware.renderutil.helper.ColorUtil
import org.lwjgl.opengl.GL11

class TextureRectShader : BaseShader("texture_rect.frag", "texture_rect.vert") {
    lateinit var uMatrix: Mat4Uniform
    lateinit var uTexture: Int1Uniform

    override fun setupLayout() {
        drawMode(GL11.GL_TRIANGLES)
        vertices.float(2, 0)
        vertices.float(2, 1)
        vertices.float(4, 2)
        vertices.float(4, 3)
        uMatrix = uniforms.mat4("uMatrix")
        uTexture = uniforms.int1("uTexture")
    }
}

class TextureRectBatch : BatchRenderer, AutoCloseable {
    private val shader = TextureRectShader()

    private var scissorVersion = Int.MIN_VALUE
    private var scissorMinX = 0f
    private var scissorMinY = 0f
    private var scissorMaxX = 0f
    private var scissorMaxY = 0f

    override fun load() {
        shader.load()
    }

    override fun hasPending() = shader.vertices.hasVertices()

    fun texture(id: String, x: Float, y: Float, width: Float, height: Float, color: Int) {
        cacheScissor()
        quad(TextureAtlas.region(id), x, y, width, height, color)
    }

    override fun flush() {
        if (!hasPending()) return

        shader.attach()
        shader.uniforms.int1(shader.uTexture, 0)
        shader.uniforms.mat4(shader.uMatrix, MatrixControl.current())
        TextureAtlas.texture().bind(0)
        shader.draw()
    }

    override fun close() {
        shader.close()
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun quad(
        region: TextureAtlas.Region,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Int
    ) {
        putQuad(
            x,
            y,
            x + width,
            y + height,
            region.uOffset,
            region.vOffset,
            region.uOffset + region.uScale,
            region.vOffset + region.vScale,
            color
        )
    }

    private fun putQuad(
        minX: Float, minY: Float,
        maxX: Float, maxY: Float,
        minU: Float, minV: Float,
        maxU: Float, maxV: Float,
        color: Int
    ) {
        val r = ColorUtil.redf(color)
        val g = ColorUtil.greenf(color)
        val b = ColorUtil.bluef(color)
        val a = ColorUtil.alphaf(color)

        putVertex(minX, minY, minU, minV, r, g, b, a)
        putVertex(minX, maxY, minU, maxV, r, g, b, a)
        putVertex(maxX, maxY, maxU, maxV, r, g, b, a)

        putVertex(minX, minY, minU, minV, r, g, b, a)
        putVertex(maxX, maxY, maxU, maxV, r, g, b, a)
        putVertex(maxX, minY, maxU, minV, r, g, b, a)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun putVertex(
        x: Float, y: Float,
        u: Float, v: Float,
        red: Float, green: Float, blue: Float, alpha: Float
    ) {
        shader.vertices
            .vec2(MatrixControl.transformX(x, y), MatrixControl.transformY(x, y))
            .vec2(u, v)
            .vec4(red, green, blue, alpha)
            .vec4(scissorMinX, scissorMinY, scissorMaxX, scissorMaxY)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun cacheScissor() {
        val version = ScissorControl.version
        if (version == scissorVersion) return

        if (hasPending()) {
            flush()
        }

        scissorVersion = version
        scissorMinX = ScissorControl.minX
        scissorMinY = ScissorControl.minY
        scissorMaxX = ScissorControl.maxX
        scissorMaxY = ScissorControl.maxY
    }
}
