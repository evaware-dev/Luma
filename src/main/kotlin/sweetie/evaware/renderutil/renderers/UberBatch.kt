package sweetie.evaware.renderutil.renderers

import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.wrapper.LumaVertexBuffer
import sweetie.evaware.luma.wrapper.matrix.MatrixControl
import sweetie.evaware.luma.wrapper.scissor.ScissorControl
import sweetie.evaware.luma.wrapper.texture.TextureAtlas
import sweetie.evaware.msdf.MsdfFont
import sweetie.evaware.renderutil.RenderStats
import sweetie.evaware.renderutil.api.BatchRenderer
import sweetie.evaware.renderutil.helper.ColorUtil

internal class UberBatch : BatchRenderer, AutoCloseable {
    companion object {
        private const val plainMode = 0f
        private const val textMode = 1f
        private const val floatsPerVertex = 14
    }

    private val vertices = LumaVertexBuffer(floatsPerVertex)

    override fun load() {
        TextureAtlas.prepare()
    }

    override fun hasPending() = vertices.hasVertices()

    fun rect(x: Float, y: Float, width: Float, height: Float, color: Int) {
        quad(TextureAtlas.whiteRegion(), x, y, width, height, color, plainMode, 0f)
    }

    fun texture(id: String, x: Float, y: Float, width: Float, height: Float, color: Int) {
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
        val scissorMinX = ScissorControl.minX
        val scissorMinY = ScissorControl.minY
        val scissorMaxX = ScissorControl.maxX
        val scissorMaxY = ScissorControl.maxY
        var cursorX = x
        var baselineY = y + (font.lineHeight + font.descender) * size
        var index = 0

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
                    range,
                    scissorMinX,
                    scissorMinY,
                    scissorMaxX,
                    scissorMaxY
                )
            }

            cursorX += glyph.advance * size
        }
    }

    override fun flush() {
        if (!hasPending()) return
        RenderStats.markBatch()
        Luma.draw(RenderUtilPipelines.uber, vertices, TextureAtlas.texture())
        RenderStats.markDrawCall()
    }

    override fun close() {
        vertices.close()
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
        val scissorMinX = ScissorControl.minX
        val scissorMinY = ScissorControl.minY
        val scissorMaxX = ScissorControl.maxX
        val scissorMaxY = ScissorControl.maxY
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
            range,
            scissorMinX,
            scissorMinY,
            scissorMaxX,
            scissorMaxY
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
        range: Float,
        scissorMinX: Float,
        scissorMinY: Float,
        scissorMaxX: Float,
        scissorMaxY: Float
    ) {
        putVertex(minX, minY, minU, minV, red, green, blue, alpha, mode, range, scissorMinX, scissorMinY, scissorMaxX, scissorMaxY)
        putVertex(minX, maxY, minU, maxV, red, green, blue, alpha, mode, range, scissorMinX, scissorMinY, scissorMaxX, scissorMaxY)
        putVertex(maxX, maxY, maxU, maxV, red, green, blue, alpha, mode, range, scissorMinX, scissorMinY, scissorMaxX, scissorMaxY)
        putVertex(minX, minY, minU, minV, red, green, blue, alpha, mode, range, scissorMinX, scissorMinY, scissorMaxX, scissorMaxY)
        putVertex(maxX, maxY, maxU, maxV, red, green, blue, alpha, mode, range, scissorMinX, scissorMinY, scissorMaxX, scissorMaxY)
        putVertex(maxX, minY, maxU, minV, red, green, blue, alpha, mode, range, scissorMinX, scissorMinY, scissorMaxX, scissorMaxY)
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
        range: Float,
        scissorMinX: Float,
        scissorMinY: Float,
        scissorMaxX: Float,
        scissorMaxY: Float
    ) {
        vertices.putVertex14(
            x,
            y,
            u,
            v,
            red,
            green,
            blue,
            alpha,
            mode,
            range,
            scissorMinX,
            scissorMinY,
            scissorMaxX,
            scissorMaxY
        )
    }
}
