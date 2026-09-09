package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.platform.CompareOp
import sweetie.evaware.luma.api.BlendFunction
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
        if (a.size != b.size) return false
        for (i in a.indices) {
            if (a[i] !== b[i]) return false
        }
        return true
    }

    fun run(draws: DrawCallRecorder, consumer: GroupConsumer) {
        var currentTarget: Any? = null
        var currentTargetPassId = -1
        var currentProgram: Program? = null
        var currentTopology: PrimitiveTopology? = null
        var currentBlendEnabled = true
        var currentBlendFunction = BlendFunction.TRANSLUCENT
        var currentDepthEnabled = false
        var currentDepthWrite = false
        var currentDepthFunc = CompareOp.ALWAYS_PASS
        var currentCullEnabled = false
        var currentScissorKnown = false
        var currentScissorEnabled = false
        var currentScissorX = 0
        var currentScissorY = 0
        var currentScissorWidth = 0
        var currentScissorHeight = 0
        var currentTextures: Array<TextureHandle?> = DrawCall.NO_TEXTURES
        var currentUboOffset = -1L
        var currentUboBytes = -1L
        var currentVertexOffset = -1L
        var currentVertexBytes = 0L
        var currentVertexCount = 0

        for (i in 0 until draws.size) {
            val draw = draws[i]
            if (draw.clearColorEnabled || draw.clearDepthEnabled) {
                if (currentVertexCount > 0 && currentTopology != null) {
                    consumer.onDraw(currentTopology, currentVertexOffset, currentVertexBytes, currentVertexCount)
                }
                currentVertexCount = 0
                consumer.onClear(
                    draw.target,
                    draw.clearColorEnabled,
                    draw.clearRed,
                    draw.clearGreen,
                    draw.clearBlue,
                    draw.clearAlpha,
                    draw.clearDepthEnabled,
                    draw.clearDepth
                )
                currentTarget = null
                currentTargetPassId = -1
                currentProgram = null
                currentTopology = null
                currentTextures = DrawCall.NO_TEXTURES
                currentUboOffset = -1L
                currentUboBytes = -1L
                currentScissorKnown = false
                continue
            }
            val program = draw.program ?: continue
            val topology = topologyOf(draw.primitiveType)

            val targetKey: Any = draw.target ?: mainTargetMarker

            if (targetKey !== currentTarget || draw.targetPassId != currentTargetPassId) {
                if (currentVertexCount > 0 && currentTopology != null) {
                    consumer.onDraw(currentTopology, currentVertexOffset, currentVertexBytes, currentVertexCount)
                }
                currentVertexCount = 0

                consumer.onTargetChanged(draw.target)
                currentTarget = targetKey
                currentTargetPassId = draw.targetPassId
                currentProgram = null
                currentTopology = null
                currentTextures = DrawCall.NO_TEXTURES
                currentUboOffset = -1L
                currentUboBytes = -1L
                currentScissorKnown = false
            }

            val texturesSame = currentProgram === program && sameTextures(draw.textures, currentTextures)
            val canMerge = !draw.direct && currentProgram === program &&
                currentTopology == topology &&
                currentBlendEnabled == draw.blendEnabled &&
                currentBlendFunction == draw.blendFunction &&
                currentDepthEnabled == draw.depthEnabled &&
                currentDepthWrite == draw.depthWrite &&
                currentDepthFunc == draw.depthFunc &&
                currentCullEnabled == draw.cullEnabled &&
                currentScissorKnown &&
                currentScissorEnabled == draw.scissorEnabled &&
                (!draw.scissorEnabled || (
                    currentScissorX == draw.scissorX &&
                    currentScissorY == draw.scissorY &&
                    currentScissorWidth == draw.scissorWidth &&
                    currentScissorHeight == draw.scissorHeight
                )) &&
                texturesSame &&
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

                val pipelineChanged = currentProgram !== program ||
                    currentTopology != topology ||
                    currentBlendEnabled != draw.blendEnabled ||
                    currentBlendFunction != draw.blendFunction ||
                    currentDepthEnabled != draw.depthEnabled ||
                    currentDepthWrite != draw.depthWrite ||
                    currentDepthFunc != draw.depthFunc ||
                    currentCullEnabled != draw.cullEnabled

                if (pipelineChanged) {
                    consumer.onPipelineChanged(
                        program,
                        topology,
                        draw.blendEnabled,
                        draw.blendFunction,
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

                if (!texturesSame) {
                    consumer.onTextureChanged(program, draw.textures)
                }

                val scissorChanged = !currentScissorKnown ||
                    currentScissorEnabled != draw.scissorEnabled ||
                    (draw.scissorEnabled && (
                        currentScissorX != draw.scissorX ||
                        currentScissorY != draw.scissorY ||
                        currentScissorWidth != draw.scissorWidth ||
                        currentScissorHeight != draw.scissorHeight
                    ))
                if (scissorChanged) {
                    consumer.onScissorChanged(
                        draw.scissorEnabled,
                        draw.scissorX,
                        draw.scissorY,
                        draw.scissorWidth,
                        draw.scissorHeight
                    )
                }

                currentProgram = program
                currentTopology = topology
                currentBlendEnabled = draw.blendEnabled
                currentBlendFunction = draw.blendFunction
                currentDepthEnabled = draw.depthEnabled
                currentDepthWrite = draw.depthWrite
                currentDepthFunc = draw.depthFunc
                currentCullEnabled = draw.cullEnabled
                currentScissorKnown = true
                currentScissorEnabled = draw.scissorEnabled
                currentScissorX = draw.scissorX
                currentScissorY = draw.scissorY
                currentScissorWidth = draw.scissorWidth
                currentScissorHeight = draw.scissorHeight
                currentTextures = draw.textures
                currentUboOffset = draw.uboOffset
                currentUboBytes = draw.uboBytes
                if (draw.direct) {
                    consumer.onDirectDraw(draw, topology)
                    currentVertexOffset = -1L
                    currentVertexBytes = 0L
                    currentVertexCount = 0
                } else {
                    currentVertexOffset = draw.vertexOffset
                    currentVertexBytes = draw.vertexBytes
                    currentVertexCount = draw.vertexCount
                }
            }
        }

        if (currentVertexCount > 0 && currentTopology != null) {
            consumer.onDraw(currentTopology, currentVertexOffset, currentVertexBytes, currentVertexCount)
        }
    }
}
