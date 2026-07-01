package sweetie.evaware.renderutil.renderers

import sweetie.evaware.luma.matrix.MatrixControl
import sweetie.evaware.luma.shader.BaseShader
import sweetie.evaware.luma.texture.TextureAtlas
import sweetie.evaware.luma.uniform.Mat4Uniform
import sweetie.evaware.luma.uniform.Int1Uniform
import sweetie.evaware.renderutil.api.BatchRenderer
import sweetie.evaware.renderutil.helper.ColorUtil
import sweetie.evaware.renderutil.helper.ScissorCache

class TextureRectShader : BaseShader("texture_rect.frag", "texture_rect.vert") {
    lateinit var uMatrix: Mat4Uniform
    lateinit var uTexture: Int1Uniform

    override fun setupLayout() {
        instanced()
        vertices.float(2, 0)
        vertices.float(2, 1)
        vertices.float(2, 2)
        vertices.float(2, 3)
        vertices.float(4, 4)
        vertices.float(4, 5)
        uMatrix = uniforms.mat4("uMatrix")
        uTexture = uniforms.int1("uTexture")
    }
}

class TextureRectBatch : BatchRenderer, AutoCloseable {
    private val shader = TextureRectShader()
    private val scissor = ScissorCache()

    override fun load() {
        shader.load()
    }

    override fun hasPending() = shader.vertices.hasVertices()

    fun texture(id: String, x: Float, y: Float, width: Float, height: Float, color: Int) {
        scissor.update(this)

        val region = TextureAtlas.region(id)
        val r = ColorUtil.redf(color)
        val g = ColorUtil.greenf(color)
        val b = ColorUtil.bluef(color)
        val a = ColorUtil.alphaf(color)

        shader.vertices
            .vec2(x, y)
            .vec2(width, height)
            .vec2(region.uOffset, region.vOffset)
            .vec2(region.uScale, region.vScale)
            .vec4(r, g, b, a)
            .vec4(scissor.minX, scissor.minY, scissor.maxX, scissor.maxY)
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
}
