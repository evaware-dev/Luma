package sweetie.evaware.renderutil.renderers

import kotlin.math.min
import sweetie.evaware.luma.matrix.MatrixControl
import sweetie.evaware.luma.shader.BaseShader
import sweetie.evaware.luma.texture.TextureAtlas
import sweetie.evaware.luma.uniform.Int1Uniform
import sweetie.evaware.luma.uniform.Mat4Uniform
import sweetie.evaware.renderutil.api.BatchRenderer
import sweetie.evaware.renderutil.helper.ColorUtil
import sweetie.evaware.renderutil.helper.ScissorCache

class RoundedRectShader : BaseShader("rounded_rect.frag", "rounded_rect.vert") {
    lateinit var uMatrix: Mat4Uniform
    lateinit var uTexture: Int1Uniform

    override fun setupLayout() {
        instanced()
        vertices.float(2, 0)
        vertices.float(2, 1)
        vertices.float(4, 2)
        vertices.float(4, 3)
        vertices.float(4, 4)
        vertices.float(4, 5)
        uMatrix = uniforms.mat4("uMatrix")
        uTexture = uniforms.int1("uTexture")
    }
}

class RoundedRectBatch(
    private val atlas: TextureAtlas
) : BatchRenderer, AutoCloseable {
    private val shader = RoundedRectShader()
    private val scissor = ScissorCache()
    private var instanceData = FloatArray(INITIAL_INSTANCE_CAPACITY * FLOATS_PER_INSTANCE)
    private var instanceCount = 0

    override fun load() {
        shader.load()
    }

    override fun hasPending() = instanceCount > 0

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
        append(
            x, y, width, height, color,
            topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius,
            0f, 0f, 0f, -1f
        )
    }

    fun texture(
        id: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Int,
        radius: Float
    ) {
        val region = atlas.region(id)
        append(
            x, y, width, height, color,
            radius, radius, radius, radius,
            region.uOffset, region.vOffset, region.uScale, region.vScale
        )
    }

    private fun append(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Int,
        topLeftRadius: Float,
        topRightRadius: Float,
        bottomRightRadius: Float,
        bottomLeftRadius: Float,
        uOffset: Float,
        vOffset: Float,
        uScale: Float,
        vScale: Float
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

        val base = instanceCount * FLOATS_PER_INSTANCE
        if (base + FLOATS_PER_INSTANCE > instanceData.size) {
            instanceData = instanceData.copyOf(instanceData.size shl 1)
        }
        val arr = instanceData
        arr[base] = x
        arr[base + 1] = y
        arr[base + 2] = width
        arr[base + 3] = height
        arr[base + 4] = topLeft
        arr[base + 5] = topRight
        arr[base + 6] = bottomRight
        arr[base + 7] = bottomLeft
        arr[base + 8] = red
        arr[base + 9] = green
        arr[base + 10] = blue
        arr[base + 11] = alpha
        arr[base + 12] = uOffset
        arr[base + 13] = vOffset
        arr[base + 14] = uScale
        arr[base + 15] = vScale
        arr[base + 16] = scissor.minX
        arr[base + 17] = scissor.minY
        arr[base + 18] = scissor.maxX
        arr[base + 19] = scissor.maxY
        instanceCount++
    }

    override fun flush() {
        val count = instanceCount
        if (count == 0) return
        instanceCount = 0
        shader.attach()
        shader.uniforms.int1(shader.uTexture, 0)
        shader.uniforms.mat4(shader.uMatrix, MatrixControl.current())
        atlas.texture().bind(0)
        val arr = instanceData
        shader.draw(count) {
            var i = 0
            repeat(count) {
                vec2(arr[i++], arr[i++])
                vec2(arr[i++], arr[i++])
                vec4(arr[i++], arr[i++], arr[i++], arr[i++])
                vec4(arr[i++], arr[i++], arr[i++], arr[i++])
                vec4(arr[i++], arr[i++], arr[i++], arr[i++])
                vec4(arr[i++], arr[i++], arr[i++], arr[i++])
            }
        }
    }

    override fun close() {
        instanceCount = 0
        shader.close()
    }

    private companion object {
        const val FLOATS_PER_INSTANCE = 20
        const val INITIAL_INSTANCE_CAPACITY = 64
    }
}
