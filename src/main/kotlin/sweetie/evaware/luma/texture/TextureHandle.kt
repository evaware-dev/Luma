package sweetie.evaware.luma.texture

import org.lwjgl.opengl.GL11
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.CloseableResourceBase
import sweetie.evaware.luma.resource.LumaResources

class TextureHandle(
    internal val id: Int,
    val width: Int,
    val height: Int
) : CloseableResourceBase() {

    fun bind() {
        requireOpen()
        Luma.bindTexture(id)
    }

    override fun close() {
        if (!markClosed()) return
        Luma.onTextureDeleted(id)
        if (Luma.hasContext()) {
            GL11.glDeleteTextures(id)
        }
        LumaResources.untrack(this)
    }
}
