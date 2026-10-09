package sweetie.evaware.luma.backend.blaze3d

import sweetie.evaware.luma.api.BlendFunction
import sweetie.evaware.luma.api.DepthCompare
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.TextureHandle

internal class DrawCall {
    var program: Program? = null
    var vertexOffset: Long = 0
    var vertexBytes: Long = 0
    var vertexCount: Int = 0
    var uboOffset: Long = 0
    var uboBytes: Long = 0
    val textures: Array<TextureHandle?> = arrayOfNulls(TEXTURE_UNITS)
    var textureMask: Int = 0
    var primitiveType: PrimitiveType = PrimitiveType.TRIANGLES
    var blendEnabled: Boolean = true
    var blendFunction: BlendFunction = BlendFunction.TRANSLUCENT
    var depthEnabled: Boolean = false
    var depthWrite: Boolean = false
    var depthFunc: DepthCompare = DepthCompare.ALWAYS
    var cullEnabled: Boolean = false
    var scissorEnabled = false
    var scissorX = 0
    var scissorY = 0
    var scissorWidth = 0
    var scissorHeight = 0
    var target: VulkanRenderTarget? = null
    var targetPassId: Int = 0
    val vertexBuffers: Array<VulkanVertexBuffer?> = arrayOfNulls(16)
    val vertexOffsets: LongArray = LongArray(16)
    var vertexBindingsMask: Int = 0
    var indexBuffer: VulkanIndexBuffer? = null
    var firstVertex = 0
    var firstIndex = 0
    var baseVertex = 0
    var instanceCount = 1
    var firstInstance = 0
    var indexed = false
    var direct = false
    var clearColorEnabled = false
    var clearDepthEnabled = false
    var clearRed = 0f
    var clearGreen = 0f
    var clearBlue = 0f
    var clearAlpha = 0f
    var clearDepth = 1.0

    fun releaseReferences() {
        program = null
        if (textureMask != 0) {
            textures.fill(null)
            textureMask = 0
        }
        if (vertexBindingsMask != 0) {
            vertexBuffers.fill(null)
            vertexOffsets.fill(0L)
            vertexBindingsMask = 0
        }
        target = null
        indexBuffer = null
        direct = false
        indexed = false
        clearColorEnabled = false
        clearDepthEnabled = false
    }

    companion object {
        const val TEXTURE_UNITS = 16
        val NO_TEXTURES: Array<TextureHandle?> = arrayOfNulls(TEXTURE_UNITS)
    }
}

internal class DrawCallRecorder {
    private val pool = ArrayList<DrawCall>()

    var size = 0
        private set

    fun reset() {
        for (index in 0 until size) pool[index].releaseReferences()
        size = 0
    }

    fun obtain(): DrawCall {
        if (size >= pool.size) pool.add(DrawCall())
        return pool[size++]
    }

    operator fun get(index: Int): DrawCall = pool[index]
}
