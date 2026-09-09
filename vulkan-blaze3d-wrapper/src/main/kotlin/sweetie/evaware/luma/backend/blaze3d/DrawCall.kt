package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.systems.RenderPass
import sweetie.evaware.luma.api.BlendFunction
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.TextureHandle

internal class DrawCall {
    var program: Program? = null
    var vertexOffset: Long = 0
    var vertexBytes: Long = 0
    var vertexCount: Int = 0
    var uboOffset: Long = 0
    var uboBytes: Long = 0
    var textures: Array<TextureHandle?> = NO_TEXTURES
    var primitiveType: PrimitiveType = PrimitiveType.TRIANGLES
    var blendEnabled: Boolean = true
    var blendFunction: BlendFunction = BlendFunction.TRANSLUCENT
    var depthEnabled: Boolean = false
    var depthWrite: Boolean = false
    var depthFunc: CompareOp = CompareOp.ALWAYS_PASS
    var cullEnabled: Boolean = false
    var scissorEnabled = false
    var scissorX = 0
    var scissorY = 0
    var scissorWidth = 0
    var scissorHeight = 0
    var target: VulkanRenderTarget? = null
    var targetPassId: Int = 0
    var clearColor: FloatArray? = null
    var vertexBindings: VertexBindingSnapshot? = null
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
        textures = NO_TEXTURES
        target = null
        clearColor = null
        vertexBindings = null
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

internal class VertexBindingSnapshot {
    val buffers = arrayOfNulls<VulkanVertexBuffer>(RenderPass.MAX_VERTEX_BUFFERS)
    val offsets = LongArray(RenderPass.MAX_VERTEX_BUFFERS)

    fun clear() {
        buffers.fill(null)
        offsets.fill(0L)
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
