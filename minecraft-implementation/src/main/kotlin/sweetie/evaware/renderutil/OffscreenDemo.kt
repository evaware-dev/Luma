package sweetie.evaware.renderutil

import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.matrix.MatrixControl
import sweetie.evaware.luma.resource.GlResources
import sweetie.evaware.luma.shader.BaseShader
import sweetie.evaware.luma.uniform.Int1Uniform
import sweetie.evaware.luma.uniform.Mat4Uniform

class BlitShader : BaseShader("blit.frag", "blit.vert") {
    lateinit var uMatrix: Mat4Uniform
    lateinit var uTexture: Int1Uniform

    override fun setupLayout() {
        drawMode(PrimitiveType.QUADS)
        vertices.float(2, 0)
        vertices.float(2, 1)
        uMatrix = uniforms.mat4("uMatrix")
        uTexture = uniforms.int1("uTexture")
    }
}

object OffscreenDemo {
    private val transparent = floatArrayOf(0f, 0f, 0f, 0f)
    private val blit = BlitShader()

    private var target: RenderTargetHandle? = null
    private var width = 0
    private var height = 0

    fun render() {
        val w = Luma.platform.getGuiScaledWidth().toInt().coerceAtLeast(1)
        val h = Luma.platform.getGuiScaledHeight().toInt().coerceAtLeast(1)
        val renderTarget = ensureTarget(w, h)

        RenderUtil.renderToTarget(renderTarget, transparent) {
            RenderTest.renderGui()
        }

        present(renderTarget, w.toFloat(), h.toFloat())
    }

    private fun present(renderTarget: RenderTargetHandle, w: Float, h: Float) {
        blit.attach()
        blit.uniforms.mat4(blit.uMatrix, MatrixControl.current())
        blit.uniforms.int1(blit.uTexture, 0)
        Luma.bindTexture(renderTarget.colorTexture, 0)
        repeat(4) {
            blit.vertices.vec2(0f, 0f).vec2(w, h)
        }
        blit.draw()
    }

    private fun ensureTarget(w: Int, h: Int): RenderTargetHandle {
        val existing = target
        if (existing != null && width == w && height == h) return existing

        if (existing != null) {
            GlResources.untrack(existing)
            existing.close()
        }

        val created = GlResources.track(RenderUtil.createRenderTarget(w, h, useDepth = true))
        target = created
        width = w
        height = h
        return created
    }
}
