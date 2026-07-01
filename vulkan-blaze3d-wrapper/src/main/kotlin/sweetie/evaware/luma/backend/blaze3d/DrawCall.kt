package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.platform.CompareOp
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
    var depthEnabled: Boolean = false
    var depthWrite: Boolean = false
    var depthFunc: CompareOp = CompareOp.ALWAYS_PASS
    var cullEnabled: Boolean = false
    var target: VulkanRenderTarget? = null
    var clearColor: FloatArray? = null

    fun releaseReferences() {
        program = null
        textures = NO_TEXTURES
        target = null
        clearColor = null
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
        for (i in pool.indices) pool[i].releaseReferences()
        size = 0
    }

    fun obtain(): DrawCall {
        if (size >= pool.size) pool.add(DrawCall())
        return pool[size++]
    }

    operator fun get(index: Int): DrawCall = pool[index]
}
