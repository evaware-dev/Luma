package sweetie.evaware.renderutil.renderers

import org.lwjgl.opengl.GL11
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.matrix.MatrixControl
import sweetie.evaware.luma.scissor.ScissorControl
import sweetie.evaware.luma.shader.Shader
import sweetie.evaware.luma.texture.TextureAtlas
import sweetie.evaware.luma.uniform.Int1Uniform
import sweetie.evaware.luma.uniform.Mat4Uniform
import sweetie.evaware.msdf.MsdfFont
import sweetie.evaware.renderutil.RenderStats
import sweetie.evaware.renderutil.api.BatchRenderer
import sweetie.evaware.renderutil.helper.ColorUtil

internal class UberBatch : BatchRenderer, AutoCloseable {
    companion object {
        private const val plainMode = 0f
        private const val textMode = 1f
    }

    private val shader = Shader(
        "assets/luma-renderer/shaders/core/uber.frag",
        "assets/luma-renderer/shaders/core/uber.vert"
    ).drawMode(GL11.GL_TRIANGLES)

    private val uMatrix: Mat4Uniform
    private val uTexture: Int1Uniform
    private val scissor = FloatArray(4)

    init {
        with(shader) {
            vertices.float(2, 0)
            vertices.float(2, 1)
            vertices.float(4, 2)
            vertices.float(2, 3)
            vertices.float(4, 4)
            uMatrix = uniforms.mat4("uMatrix")
            uTexture = uniforms.int1("uTexture")
        }
    }

    override fun load() {
        shader.load()
    }

    override fun hasPending() = shader.vertices.hasVertices()

    fun rect(x: Float, y: Float, width: Float, height: Float, color: Int) {
        ScissorControl.copyCurrent(scissor)
        quad(TextureAtlas.whiteRegion(), x, y, width, height, color, plainMode, 0f)
    }

    fun texture(id: String, x: Float, y: Float, width: Float, height: Float, color: Int) {
        ScissorControl.copyCurrent(scissor)
        quad(TextureAtlas.region(id), x, y, width, height, color, plainMode, 0f)
    }

    fun text(font: MsdfFont, text: String, x: Float, y: Float, size: Float, color: Int) {
        if (text.isEmpty()) return

        val red = ColorUtil.redf(color)
        val green = ColorUtil.greenf(color)
        val blue = ColorUtil.bluef(color)
        val alpha = ColorUtil.alphaf(color)
        val lineHeight = font.lineHeight * size
        val range = font.range
        var cursorX = x
        var baselineY = y + (font.lineHeight + font.descender) * size
        var index = 0

        ScissorControl.copyCurrent(scissor)

        while (index < text.length) {
            val code = text.codePointAt(index)
            index += Character.charCount(code)

            if (code == '\n'.code) {
                cursorX = x
                baselineY += lineHeight
                continue
            }

            val glyph = font.glyph(code) ?: continue
            val minX = cursorX + glyph.planeLeft * size
            val maxX = cursorX + glyph.planeRight * size
            val minY = baselineY - glyph.planeTop * size
            val maxY = baselineY - glyph.planeBottom * size

            if (minX != maxX && minY != maxY) {
                putQuad(
                    minX,
                    minY,
                    maxX,
                    maxY,
                    glyph.minU,
                    glyph.minV,
                    glyph.maxU,
                    glyph.maxV,
                    red,
                    green,
                    blue,
                    alpha,
                    textMode,
                    range
                )
            }

            cursorX += glyph.advance * size
        }
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
        color: Int,
        mode: Float,
        range: Float
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
            alpha,
            mode,
            range
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
        alpha: Float,
        mode: Float,
        range: Float
    ) {
        putVertex(minX, minY, minU, minV, red, green, blue, alpha, mode, range)
        putVertex(minX, maxY, minU, maxV, red, green, blue, alpha, mode, range)
        putVertex(maxX, maxY, maxU, maxV, red, green, blue, alpha, mode, range)
        putVertex(minX, minY, minU, minV, red, green, blue, alpha, mode, range)
        putVertex(maxX, maxY, maxU, maxV, red, green, blue, alpha, mode, range)
        putVertex(maxX, minY, maxU, minV, red, green, blue, alpha, mode, range)
    }

    private fun putVertex(
        x: Float,
        y: Float,
        u: Float,
        v: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        mode: Float,
        range: Float
    ) {
        shader.vertices.attribute2(0, MatrixControl.transformX(x, y), MatrixControl.transformY(x, y))
        shader.vertices.attribute2(1, u, v)
        shader.vertices.attribute4(2, red, green, blue, alpha)
        shader.vertices.attribute2(3, mode, range)
        shader.vertices.attribute4(4, scissor[0], scissor[1], scissor[2], scissor[3])
    }
}
