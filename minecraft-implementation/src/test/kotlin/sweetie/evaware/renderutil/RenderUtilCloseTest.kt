package sweetie.evaware.renderutil

import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.api.RenderBackend
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.VertexLayout
import sweetie.evaware.luma.scissor.ScissorControl
import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RenderUtilCloseTest {
    private class MockBackend(var context: Boolean) : RenderBackend {
        override fun beginFrame() {}
        override fun endFrame() {}
        override fun createProgram(vertexSource: String, fragmentSource: String, layout: VertexLayout): ProgramHandle = error("mock")
        override fun bindProgram(program: ProgramHandle) {}
        override fun createTexture(image: BufferedImage, mipmap: Boolean): TextureHandle = error("mock")
        override fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage) {}
        override fun bindTexture(texture: TextureHandle, unit: Int) {}
        override fun draw(
            program: ProgramHandle,
            vertices: FloatBuffer,
            vertexCount: Int,
            uniforms: ShaderUniforms,
            texture: TextureHandle?,
            primitiveType: Int
        ) {}
        override fun createRenderTarget(
            width: Int,
            height: Int,
            useDepth: Boolean,
            format: RenderTargetFormat
        ): RenderTargetHandle = error("mock")
        override fun beginRenderTarget(target: RenderTargetHandle, clearColor: FloatArray?) {}
        override fun endRenderTarget() {}
        override fun close() {}
        override fun hasContext(): Boolean = context
    }

    private var originalBackend: RenderBackend? = null

    @AfterTest
    fun resetContextProvider() {
        originalBackend?.let { Luma.backend = it }
        ScissorControl.clear()
    }

    @Test
    fun `close is idempotent without gl context`() {
        try {
            originalBackend = Luma.backend
        } catch (_: Throwable) {}

        Luma.backend = MockBackend(false)

        RenderUtil.close()
        RenderUtil.close()
    }

    @Test
    fun `scissor block pops after exception`() {
        ScissorControl.clear()

        assertFailsWith<IllegalStateException> {
            RenderUtil.scissor(0f, -10f, 10f, 10f) {
                error("boom")
            }
        }

        assertEquals(0f, ScissorControl.minX)
        assertEquals(0f, ScissorControl.minY)
        assertEquals(0f, ScissorControl.maxX)
        assertEquals(0f, ScissorControl.maxY)
    }
}
