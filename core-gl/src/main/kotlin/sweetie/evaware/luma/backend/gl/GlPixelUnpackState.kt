package sweetie.evaware.luma.backend.gl

import org.lwjgl.opengl.GL11

internal class GlPixelUnpackState(
    private val preserveExternalState: Boolean
) {
    private val saved = IntArray(4)
    private var prepared = false
    private var ownedStateKnown = false

    fun prepare() {
        if (prepared) return
        if (!preserveExternalState) {
            if (!ownedStateKnown) {
                setRequiredUnconditionally()
                ownedStateKnown = true
            }
            prepared = true
            return
        }
        saved[0] = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT)
        saved[1] = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH)
        saved[2] = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS)
        saved[3] = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS)
        setRequired()
        prepared = true
    }

    fun restore() {
        if (!prepared) return
        if (preserveExternalState) {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, saved[0])
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, saved[1])
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, saved[2])
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, saved[3])
        }
        prepared = false
    }

    fun invalidate() {
        restore()
        ownedStateKnown = false
        prepared = false
    }

    private fun setRequired() {
        if (saved[0] != 4) GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4)
        if (saved[1] != 0) GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0)
        if (saved[2] != 0) GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0)
        if (saved[3] != 0) GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0)
    }

    private fun setRequiredUnconditionally() {
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4)
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0)
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0)
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0)
    }
}
