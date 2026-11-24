package sweetie.evaware.luma.backend.blaze3d

import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.TextureHandle

internal interface GroupConsumer {
    fun onTargetChanged(target: VulkanRenderTarget?)
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
    fun onPassBoundary() {}
    fun onDebugGroupPush(label: String) {}
    fun onDebugGroupPop() {}
    fun onPipelineChanged(program: Program, pipelineKey: Int)
    fun onUboChanged(offset: Long, size: Long)
    fun onTextureChanged(
        program: Program,
        textureMask: Int,
        texture0: TextureHandle?,
        overflowTextures: Array<TextureHandle?>?
    )
    fun onScissorChanged(enabled: Boolean, x: Int, y: Int, width: Int, height: Int)
    fun onDraw(topology: PrimitiveType, vertexStart: Long, vertexBytes: Long, vertexCount: Int)
    fun onDirectDraw(recorder: DrawCallRecorder, index: Int, topology: PrimitiveType)
}
