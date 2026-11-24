package sweetie.evaware.luma.backend.blaze3d

import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.backend.blaze3d.preset.PresetGpuSampler
import sweetie.evaware.luma.backend.blaze3d.preset.PresetTimestampQueryPool

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
    fun onBlit(
        source: VulkanRenderTarget,
        destination: VulkanRenderTarget,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int,
        dstX: Int,
        dstY: Int,
        dstWidth: Int,
        dstHeight: Int,
        linearFilter: Boolean,
        copyColor: Boolean,
        copyDepth: Boolean,
        nativeCopy: Boolean,
        vertexOffset: Long
    ) {}
    fun onPassBoundary() {}
    fun onDebugGroupPush(label: String) {}
    fun onDebugGroupPop() {}
    fun onWriteTimestamp(pool: PresetTimestampQueryPool, queryIndex: Int) {}
    fun onPipelineChanged(program: Program, pipelineKey: Long)
    fun onUboChanged(offset: Long, size: Long)
    fun onTextureChanged(
        program: Program,
        textureMask: Int,
        texture0: TextureHandle?,
        sampler0: PresetGpuSampler?,
        overflowTextures: Array<TextureHandle?>?,
        overflowSamplers: Array<PresetGpuSampler?>?
    )
    fun onScissorChanged(enabled: Boolean, x: Int, y: Int, width: Int, height: Int)
    fun onDraw(topology: PrimitiveType, vertexStart: Long, vertexBytes: Long, vertexCount: Int)
    fun onDirectDraw(recorder: DrawCallRecorder, index: Int, topology: PrimitiveType)
}
