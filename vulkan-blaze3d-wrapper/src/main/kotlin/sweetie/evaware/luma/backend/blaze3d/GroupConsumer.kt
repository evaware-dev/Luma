package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.platform.CompareOp
import sweetie.evaware.luma.api.BlendFunction
import sweetie.evaware.luma.api.TextureHandle

internal interface GroupConsumer {
    fun onTargetChanged(target: VulkanRenderTarget?, clearColor: FloatArray?)
    fun onClear(
        target: VulkanRenderTarget?,
        color: Boolean,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        depth: Boolean,
        depthValue: Double
    )
    fun onPipelineChanged(
        program: Program,
        topology: PrimitiveTopology,
        blendEnabled: Boolean,
        blendFunction: BlendFunction,
        depthEnabled: Boolean,
        depthWrite: Boolean,
        depthFunc: CompareOp,
        cullEnabled: Boolean
    )
    fun onUboChanged(offset: Long, size: Long)
    fun onTextureChanged(program: Program, textures: Array<TextureHandle?>)
    fun onScissorChanged(enabled: Boolean, x: Int, y: Int, width: Int, height: Int)
    fun onDraw(topology: PrimitiveTopology, vertexStart: Long, vertexBytes: Long, vertexCount: Int)
    fun onDirectDraw(draw: DrawCall, topology: PrimitiveTopology)
}
