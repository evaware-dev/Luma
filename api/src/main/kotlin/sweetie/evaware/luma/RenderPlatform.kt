package sweetie.evaware.luma

interface RenderPlatform {
    val activeBackend: GraphicsBackend get() = GraphicsBackend.OPENGL

    fun getGuiScaledWidth(): Float
    fun getGuiScaledHeight(): Float
    fun getGuiScale(): Float
    fun getWindowHeight(): Float
    fun getViewport(viewport: IntArray): Boolean
    fun swapToMainFramebuffer(luma: Luma)

    fun enableBlend() {}
    fun disableBlend() {}
    fun enableDepthTest() {}
    fun disableDepthTest() {}
    fun enableCull() {}
    fun disableCull() {}
}

object DefaultRenderPlatform : RenderPlatform {
    override fun getGuiScaledWidth(): Float = 960f
    override fun getGuiScaledHeight(): Float = 540f
    override fun getGuiScale(): Float = 1f
    override fun getWindowHeight(): Float = 540f

    override fun getViewport(viewport: IntArray): Boolean {
        viewport[0] = 0
        viewport[1] = 0
        viewport[2] = 960
        viewport[3] = 540
        return true
    }

    override fun swapToMainFramebuffer(luma: Luma) {}
}