package sweetie.evaware.luma.backend.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import sweetie.evaware.luma.api.TextureFormat
import sweetie.evaware.luma.backend.gl.GlTexture.Companion.glInternalFormat
import sweetie.evaware.luma.backend.gl.GlTexture.Companion.glPixelFormat
import sweetie.evaware.luma.backend.gl.GlTexture.Companion.glPixelType

class GlTextureFormatTest {
    @Test
    fun mapsTextureFormatsToExpectedGlEnumsAndByteSizes() {
        assertEquals(GL11.GL_RGBA8, TextureFormat.RGBA8.glInternalFormat)
        assertEquals(GL11.GL_RGBA, TextureFormat.RGBA8.glPixelFormat)
        assertEquals(GL11.GL_UNSIGNED_BYTE, TextureFormat.RGBA8.glPixelType)
        assertEquals(64, TextureFormat.RGBA8.byteSize(4, 4))

        assertEquals(GL30.GL_RG8, TextureFormat.RG8.glInternalFormat)
        assertEquals(GL30.GL_RG, TextureFormat.RG8.glPixelFormat)
        assertEquals(GL11.GL_UNSIGNED_BYTE, TextureFormat.RG8.glPixelType)
        assertEquals(18, TextureFormat.RG8.byteSize(3, 3))

        assertEquals(GL30.GL_R8, TextureFormat.R8.glInternalFormat)
        assertEquals(GL11.GL_RED, TextureFormat.R8.glPixelFormat)
        assertEquals(GL11.GL_UNSIGNED_BYTE, TextureFormat.R8.glPixelType)
        assertEquals(9, TextureFormat.R8.byteSize(3, 3))

        assertEquals(GL30.GL_RGBA16F, TextureFormat.RGBA16F.glInternalFormat)
        assertEquals(GL11.GL_RGBA, TextureFormat.RGBA16F.glPixelFormat)
        assertEquals(GL30.GL_HALF_FLOAT, TextureFormat.RGBA16F.glPixelType)
        assertEquals(128, TextureFormat.RGBA16F.byteSize(4, 4))

        assertFailsWith<IllegalArgumentException> { TextureFormat.R8.byteSize(0, 4) }
    }

    @Test
    fun debugGroupNoOpsSafelyWithoutGlContext() {
        val backend = Backend(GlBackendConfig(statePolicy = GlStatePolicy.PRESERVE))
        try {
            backend.pushDebugGroup("NoContext")
            backend.popDebugGroup()
            backend.popDebugGroup()
        } finally {
            backend.close()
        }
    }
}
