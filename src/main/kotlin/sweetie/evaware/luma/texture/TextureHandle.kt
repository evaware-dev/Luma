package sweetie.evaware.luma.texture

import org.lwjgl.opengl.GL11
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.Unloadable
import sweetie.evaware.luma.resource.LumaResources

class TextureHandle(
    internal val id: Int,
    val width: Int,
    val height: Int
) : Unloadable, AutoCloseable {
    private var closed = false

    fun bind() {
        Luma.bindTexture(id)
    }

    override fun unload() {
        close()
    }

    override fun close() {
        if (closed) return
        Luma.onTextureDeleted(id)
        GL11.glDeleteTextures(id)
        closed = true
        LumaResources.untrack(this)
    }
}
