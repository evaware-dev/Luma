package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.platform.CompareOp
import sweetie.evaware.luma.api.TextureHandle

interface GroupConsumer {
    fun onTargetChanged(target: VulkanRenderTarget?, clearColor: FloatArray?)
    fun onPipelineChanged(
        program: Program,
        topology: PrimitiveTopology,
        depthEnabled: Boolean,
        depthWrite: Boolean,
        depthFunc: CompareOp,
        cullEnabled: Boolean
    )
    fun onUboChanged(offset: Long, size: Long)
    fun onTextureChanged(program: Program, textures: Array<TextureHandle?>)
    fun onDraw(topology: PrimitiveTopology, vertexStart: Long, vertexBytes: Long, vertexCount: Int)
}
