package sweetie.evaware.luma.backend.gl

class GlStateSnapshot {
    var drawFramebuffer = 0
    var readFramebuffer = 0
    var viewportX = 0
    var viewportY = 0
    var viewportWidth = 0
    var viewportHeight = 0
    var blendEnabled = false
    var depthEnabled = false
    var cullEnabled = false
    var blendSrcRgb = 0
    var blendDstRgb = 0
    var blendSrcAlpha = 0
    var blendDstAlpha = 0
    var blendEquationRgb = 0
    var blendEquationAlpha = 0
    var program = 0
    var vertexArray = 0
    var activeTexture = 0
    val boundTextures = IntArray(2)
    var arrayBuffer = 0
    val samplerBindings = IntArray(2)
    var scissorEnabled = false
}
