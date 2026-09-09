package sweetie.evaware.renderutil

import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.matrix.MatrixControl
import sweetie.evaware.luma.resource.GlResources
import sweetie.evaware.luma.shader.BaseShader
import sweetie.evaware.luma.uniform.Int1Uniform
import sweetie.evaware.luma.uniform.Mat4Uniform
import sweetie.evaware.luma.vertex.PreparedVertices

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

    private var blit: BlitShader? = null
    private var target: RenderTargetHandle? = null
    private var width = 0
    private var height = 0
    private var preparedVertices: PreparedVertices? = null

    fun render() {
        val w = Luma.platform.getGuiScaledWidth().toInt().coerceAtLeast(1)
        val h = Luma.platform.getGuiScaledHeight().toInt().coerceAtLeast(1)
        updateSize(w, h)
        val renderTarget = target ?: return
        val shader = ensureBlit()

        RenderUtil.renderFrame {
            RenderUtil.renderToTarget(renderTarget, transparent) {
                RenderTest.renderGui()
            }
        }

        RenderUtil.renderFrame {
            present(shader, renderTarget)
        }
    }

    private fun present(shader: BlitShader, renderTarget: RenderTargetHandle) {
        val prepared = preparedVertices ?: return
        shader.attach()
        shader.uniforms.mat4(shader.uMatrix, MatrixControl.current())
        shader.uniforms.int1(shader.uTexture, 0)
        Luma.bindTexture(renderTarget.colorTexture, 0)
        shader.draw(prepared)
    }

    fun reset() {
        target = null
        blit = null
        width = 0
        height = 0
        preparedVertices?.close()
        preparedVertices = null
    }

    private fun ensureBlit(): BlitShader {
        var shader = blit
        if (shader == null) {
            shader = BlitShader()
            blit = shader
            shader.load()
        }
        return shader
    }

    private fun updateSize(w: Int, h: Int) {
        if (width == w && height == h && target != null && preparedVertices != null) return
        width = w
        height = h

        target?.let {
            GlResources.untrack(it)
            it.close()
        }
        target = GlResources.track(RenderUtil.createRenderTarget(w, h))

        preparedVertices?.close()
        val shader = ensureBlit()
        val wf = w.toFloat()
        val hf = h.toFloat()
        preparedVertices = shader.prepareVertices(4)
            .vec2(0f, 0f).vec2(wf, hf)
            .vec2(0f, 0f).vec2(wf, hf)
            .vec2(0f, 0f).vec2(wf, hf)
            .vec2(0f, 0f).vec2(wf, hf)
            .seal()
    }
}
