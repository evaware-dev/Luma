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
    var arrayBuffer = 0
    var scissorEnabled = false
    var colorMaskRed = true
    var colorMaskGreen = true
    var colorMaskBlue = true
    var colorMaskAlpha = true
    val clearColor = FloatArray(4)
    var clearColorCaptured = false

    private var textureUnits = IntArray(INITIAL_TEXTURE_UNITS)
    private var boundTextures = IntArray(INITIAL_TEXTURE_UNITS)
    private var samplerBindings = IntArray(INITIAL_TEXTURE_UNITS)
    var textureUnitCount = 0
        private set

    fun clearTextureUnits() {
        textureUnitCount = 0
    }

    fun hasTextureUnit(unit: Int): Boolean {
        for (index in 0 until textureUnitCount) {
            if (textureUnits[index] == unit) return true
        }
        return false
    }

    fun addTextureUnit(unit: Int, texture: Int, sampler: Int) {
        ensureTextureCapacity(textureUnitCount + 1)
        textureUnits[textureUnitCount] = unit
        boundTextures[textureUnitCount] = texture
        samplerBindings[textureUnitCount] = sampler
        textureUnitCount++
    }

    fun textureUnit(index: Int): Int = textureUnits[index]

    fun boundTexture(index: Int): Int = boundTextures[index]

    fun samplerBinding(index: Int): Int = samplerBindings[index]

    private fun ensureTextureCapacity(required: Int) {
        if (required <= textureUnits.size) return
        val capacity = maxOf(required, textureUnits.size shl 1)
        textureUnits = textureUnits.copyOf(capacity)
        boundTextures = boundTextures.copyOf(capacity)
        samplerBindings = samplerBindings.copyOf(capacity)
    }

    private companion object {
        const val INITIAL_TEXTURE_UNITS = 4
    }
}
