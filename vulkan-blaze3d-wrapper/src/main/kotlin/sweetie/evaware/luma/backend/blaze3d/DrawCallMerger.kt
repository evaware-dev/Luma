package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.platform.CompareOp
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.TextureHandle

internal class DrawCallMerger {

    private val mainTargetMarker = Any()

    private fun topologyOf(type: PrimitiveType): PrimitiveTopology = when (type) {
        PrimitiveType.LINES -> PrimitiveTopology.LINES
        PrimitiveType.QUADS -> PrimitiveTopology.QUADS
        PrimitiveType.TRIANGLES -> PrimitiveTopology.TRIANGLES
    }

    private fun sameTextures(a: Array<TextureHandle?>, b: Array<TextureHandle?>): Boolean {
        if (a === b) return true
        for (i in a.indices) {
            if (a[i] !== b[i]) return false
        }
        return true
    }

    fun run(draws: DrawCallRecorder, consumer: GroupConsumer) {
        var currentTarget: Any? = Any()
        var currentClearColor: FloatArray? = null
        var currentProgram: Program? = null
        var currentTopology: PrimitiveTopology? = null
        var currentDepthEnabled = false
        var currentDepthWrite = false
        var currentDepthFunc = CompareOp.ALWAYS_PASS
        var currentCullEnabled = false
        var currentTextures: Array<TextureHandle?> = DrawCall.NO_TEXTURES
        var currentUboOffset = -1L
        var currentUboBytes = -1L
        var currentVertexOffset = -1L
        var currentVertexBytes = 0L
        var currentVertexCount = 0

        for (i in 0 until draws.size) {
            val draw = draws[i]
            val program = draw.program ?: continue
            val topology = topologyOf(draw.primitiveType)

            val targetKey: Any = draw.target ?: mainTargetMarker

            if (targetKey != currentTarget || !draw.clearColor.contentEquals(currentClearColor)) {
                if (currentVertexCount > 0 && currentTopology != null) {
                    consumer.onDraw(currentTopology, currentVertexOffset, currentVertexBytes, currentVertexCount)
                }
                currentVertexCount = 0

                consumer.onTargetChanged(draw.target, draw.clearColor)
                currentTarget = targetKey
                currentClearColor = draw.clearColor
                currentProgram = null
                currentTopology = null
                currentTextures = DrawCall.NO_TEXTURES
                currentUboOffset = -1L
                currentUboBytes = -1L
            }

            val canMerge = currentProgram == program &&
                currentTopology == topology &&
                currentDepthEnabled == draw.depthEnabled &&
                currentDepthWrite == draw.depthWrite &&
                currentDepthFunc == draw.depthFunc &&
                currentCullEnabled == draw.cullEnabled &&
                sameTextures(draw.textures, currentTextures) &&
                currentUboOffset == draw.uboOffset &&
                currentUboBytes == draw.uboBytes &&
                draw.vertexOffset == currentVertexOffset + currentVertexBytes

            if (canMerge) {
                currentVertexBytes += draw.vertexBytes
                currentVertexCount += draw.vertexCount
            } else {
                if (currentVertexCount > 0 && currentTopology != null) {
                    consumer.onDraw(currentTopology, currentVertexOffset, currentVertexBytes, currentVertexCount)
                }

                val pipelineChanged = currentProgram != program ||
                    currentTopology != topology ||
                    currentDepthEnabled != draw.depthEnabled ||
                    currentDepthWrite != draw.depthWrite ||
                    currentDepthFunc != draw.depthFunc ||
                    currentCullEnabled != draw.cullEnabled

                if (pipelineChanged) {
                    consumer.onPipelineChanged(
                        program,
                        topology,
                        draw.depthEnabled,
                        draw.depthWrite,
                        draw.depthFunc,
                        draw.cullEnabled
                    )
                }

                if (program.uniformInfos.isNotEmpty()) {
                    if (draw.uboOffset != currentUboOffset || draw.uboBytes != currentUboBytes) {
                        consumer.onUboChanged(draw.uboOffset, draw.uboBytes)
                    }
                }

                if (!sameTextures(draw.textures, currentTextures)) {
                    consumer.onTextureChanged(program, draw.textures)
                }

                currentProgram = program
                currentTopology = topology
                currentDepthEnabled = draw.depthEnabled
                currentDepthWrite = draw.depthWrite
                currentDepthFunc = draw.depthFunc
                currentCullEnabled = draw.cullEnabled
                currentTextures = draw.textures
                currentUboOffset = draw.uboOffset
                currentUboBytes = draw.uboBytes
                currentVertexOffset = draw.vertexOffset
                currentVertexBytes = draw.vertexBytes
                currentVertexCount = draw.vertexCount
            }
        }

        if (currentVertexCount > 0 && currentTopology != null) {
            consumer.onDraw(currentTopology, currentVertexOffset, currentVertexBytes, currentVertexCount)
        }
    }
}
