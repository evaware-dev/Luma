package sweetie.evaware.luma.backend.blaze3d

import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.backend.blaze3d.preset.PresetGpuSampler

internal class DrawCallMerger {
    private val mainTargetMarker = Any()

    private fun sameOverflowTextures(
        a: Array<TextureHandle?>?,
        b: Array<TextureHandle?>?,
        sa: Array<PresetGpuSampler?>?,
        sb: Array<PresetGpuSampler?>?,
        extraMask: Int
    ): Boolean {
        if (a === b && sa === sb) return true
        if (a == null || b == null) return false
        var mask = extraMask
        while (mask != 0) {
            val bit = Integer.numberOfTrailingZeros(mask)
            if (a[bit] !== b[bit]) return false
            if (sa?.get(bit) !== sb?.get(bit)) return false
            mask = mask and (mask - 1)
        }
        return true
    }

    fun run(draws: DrawCallRecorder, consumer: GroupConsumer) {
        val count = draws.size
        if (count == 0) return

        val flagsArr = draws.flags
        val programsArr = draws.programs
        val targetsArr = draws.targets
        val targetPassIdsArr = draws.targetPassIds
        val pipelineKeysArr = draws.pipelineKeys
        val vertexOffsetsArr = draws.vertexOffsets
        val vertexBytesArr = draws.vertexBytes
        val vertexCountsArr = draws.vertexCounts
        val uboOffsetsArr = draws.uboOffsets
        val uboBytesArr = draws.uboBytes
        val scissorXYArr = draws.scissorXY
        val scissorWHArr = draws.scissorWH
        val textureMasksArr = draws.textureMasks
        val texture0Arr = draws.texture0
        val sampler0Arr = draws.sampler0
        val overflowTexturesArr = draws.overflowTextures
        val overflowSamplersArr = draws.overflowSamplers

        var currentTarget: Any? = null
        var currentTargetPassId = -1
        var currentProgram: Program? = null
        var currentPipelineKey = -1L
        var currentTopology: PrimitiveType? = null
        var currentScissorKnown = false
        var currentScissorXY = 0L
        var currentScissorWH = 0L
        var currentTextureMask = 0
        var currentTexture0: TextureHandle? = null
        var currentSampler0: PresetGpuSampler? = null
        var currentOverflowTextures: Array<TextureHandle?>? = null
        var currentOverflowSamplers: Array<PresetGpuSampler?>? = null
        var currentUboOffset = -1L
        var currentUboBytes = -1L
        var currentVertexOffset = -1L
        var currentVertexBytes = 0L
        var currentVertexCount = 0

        for (i in 0 until count) {
            val f = flagsArr[i].toInt() and 0xFF
            if ((f and DrawCallRecorder.FLAG_SPECIAL_MASK) != 0) {
                if (currentVertexCount > 0 && currentTopology != null) {
                    consumer.onDraw(currentTopology, currentVertexOffset, currentVertexBytes, currentVertexCount)
                }
                currentVertexCount = 0
                if ((f and DrawCallRecorder.FLAG_CLEAR_MASK) != 0) {
                    consumer.onClear(
                        targetsArr[i],
                        (f and DrawCallRecorder.FLAG_CLEAR_COLOR) != 0,
                        draws.clearRed!![i],
                        draws.clearGreen!![i],
                        draws.clearBlue!![i],
                        draws.clearAlpha!![i],
                        (f and DrawCallRecorder.FLAG_CLEAR_DEPTH) != 0,
                        draws.clearDepth!![i]
                    )
                    currentTarget = null
                    currentTargetPassId = -1
                    currentProgram = null
                    currentPipelineKey = -1L
                    currentTopology = null
                    currentTextureMask = 0
                    currentTexture0 = null
                    currentSampler0 = null
                    currentOverflowTextures = null
                    currentOverflowSamplers = null
                    currentUboOffset = -1L
                    currentUboBytes = -1L
                    currentScissorKnown = false
                } else if ((f and DrawCallRecorder.FLAG_BLIT) != 0) {
                    val bf = draws.blitFlags!![i].toInt()
                    consumer.onBlit(
                        source = draws.blitSources!![i]!!,
                        destination = targetsArr[i]!!,
                        srcX = draws.blitSrcX!![i],
                        srcY = draws.blitSrcY!![i],
                        srcWidth = draws.blitSrcW!![i],
                        srcHeight = draws.blitSrcH!![i],
                        dstX = draws.blitDstX!![i],
                        dstY = draws.blitDstY!![i],
                        dstWidth = draws.blitDstW!![i],
                        dstHeight = draws.blitDstH!![i],
                        linearFilter = (bf and DrawCallRecorder.BLIT_FLAG_LINEAR) != 0,
                        copyColor = (bf and DrawCallRecorder.BLIT_FLAG_COLOR) != 0,
                        copyDepth = (bf and DrawCallRecorder.BLIT_FLAG_DEPTH) != 0,
                        nativeCopy = (bf and DrawCallRecorder.BLIT_FLAG_NATIVE) != 0,
                        vertexOffset = vertexOffsetsArr[i]
                    )
                    currentTarget = null
                    currentTargetPassId = -1
                    currentProgram = null
                    currentPipelineKey = -1L
                    currentTopology = null
                    currentTextureMask = 0
                    currentTexture0 = null
                    currentSampler0 = null
                    currentOverflowTextures = null
                    currentOverflowSamplers = null
                    currentUboOffset = -1L
                    currentUboBytes = -1L
                    currentScissorKnown = false
                } else if ((f and DrawCallRecorder.FLAG_DEBUG_PUSH) != 0) {
                    if (currentTarget != null && targetPassIdsArr[i] != currentTargetPassId) {
                        consumer.onPassBoundary()
                        currentTarget = null
                        currentTargetPassId = -1
                        currentProgram = null
                        currentPipelineKey = -1L
                        currentTopology = null
                        currentTextureMask = 0
                        currentTexture0 = null
                        currentSampler0 = null
                        currentOverflowTextures = null
                        currentOverflowSamplers = null
                        currentUboOffset = -1L
                        currentUboBytes = -1L
                        currentScissorKnown = false
                    }
                    draws.debugGroupLabels?.get(i)?.let(consumer::onDebugGroupPush)
                    currentVertexOffset = -1L
                    currentVertexBytes = 0L
                } else if ((f and DrawCallRecorder.FLAG_DEBUG_POP) != 0) {
                    if (currentTarget != null && targetPassIdsArr[i] != currentTargetPassId) {
                        consumer.onPassBoundary()
                        currentTarget = null
                        currentTargetPassId = -1
                        currentProgram = null
                        currentPipelineKey = -1L
                        currentTopology = null
                        currentTextureMask = 0
                        currentTexture0 = null
                        currentSampler0 = null
                        currentOverflowTextures = null
                        currentOverflowSamplers = null
                        currentUboOffset = -1L
                        currentUboBytes = -1L
                        currentScissorKnown = false
                    }
                    consumer.onDebugGroupPop()
                    currentVertexOffset = -1L
                    currentVertexBytes = 0L
                } else if ((f and DrawCallRecorder.FLAG_TIMESTAMP) != 0) {
                    if (currentTarget != null && targetPassIdsArr[i] != currentTargetPassId) {
                        consumer.onPassBoundary()
                        currentTarget = null
                        currentTargetPassId = -1
                        currentProgram = null
                        currentPipelineKey = -1L
                        currentTopology = null
                        currentTextureMask = 0
                        currentTexture0 = null
                        currentSampler0 = null
                        currentOverflowTextures = null
                        currentOverflowSamplers = null
                        currentUboOffset = -1L
                        currentUboBytes = -1L
                        currentScissorKnown = false
                    }
                    draws.timestampPools?.get(i)?.let { pool ->
                        consumer.onWriteTimestamp(pool, draws.timestampIndices!![i])
                    }
                    currentVertexOffset = -1L
                    currentVertexBytes = 0L
                }
                continue
            }

            val program = programsArr[i] ?: continue
            val target = targetsArr[i]
            val targetPassId = targetPassIdsArr[i]
            val targetKey: Any = target ?: mainTargetMarker

            if (targetKey !== currentTarget || targetPassId != currentTargetPassId) {
                if (currentVertexCount > 0 && currentTopology != null) {
                    consumer.onDraw(currentTopology, currentVertexOffset, currentVertexBytes, currentVertexCount)
                }
                currentVertexCount = 0

                consumer.onTargetChanged(target)
                currentTarget = targetKey
                currentTargetPassId = targetPassId
                currentProgram = null
                currentPipelineKey = -1L
                currentTopology = null
                currentTextureMask = 0
                currentTexture0 = null
                currentSampler0 = null
                currentOverflowTextures = null
                currentOverflowSamplers = null
                currentUboOffset = -1L
                currentUboBytes = -1L
                currentScissorKnown = false
            }

            val pipelineKey = pipelineKeysArr[i]
            val pipelineSame = currentProgram === program && currentPipelineKey == pipelineKey
            val texMask = textureMasksArr[i]
            val drawTex0 = if (texMask != 0) texture0Arr[i] else null
            val drawSamp0 = if (texMask != 0) sampler0Arr[i] else null
            val drawOverflow = if ((texMask and 1.inv()) != 0) overflowTexturesArr?.get(i) else null
            val drawOverflowSamp = if ((texMask and 1.inv()) != 0) overflowSamplersArr?.get(i) else null
            val texturesSame = if (currentProgram !== program || currentTextureMask != texMask) {
                false
            } else if (texMask == 0) {
                true
            } else if (texMask == 1) {
                currentTexture0 === drawTex0 && currentSampler0 === drawSamp0
            } else {
                currentTexture0 === drawTex0 &&
                    currentSampler0 === drawSamp0 &&
                    sameOverflowTextures(
                        drawOverflow,
                        currentOverflowTextures,
                        drawOverflowSamp,
                        currentOverflowSamplers,
                        texMask and 1.inv()
                    )
            }

            val drawScissorWH = scissorWHArr[i]
            val drawScissorXY = scissorXYArr[i]
            val scissorSame = currentScissorKnown &&
                currentScissorWH == drawScissorWH &&
                (drawScissorWH == 0L || currentScissorXY == drawScissorXY)

            val drawUboOffset = uboOffsetsArr[i]
            val drawUboBytes = uboBytesArr[i]
            val isDirect = (f and DrawCallRecorder.FLAG_DIRECT) != 0

            if (
                isDirect &&
                pipelineSame &&
                scissorSame &&
                texturesSame &&
                drawUboOffset == currentUboOffset &&
                drawUboBytes == currentUboBytes &&
                currentVertexCount == 0 &&
                currentTopology != null
            ) {
                consumer.onDirectDraw(draws, i, currentTopology)
                continue
            }

            val drawVertexOffset = vertexOffsetsArr[i]

            val canMerge = !isDirect &&
                pipelineSame &&
                scissorSame &&
                texturesSame &&
                currentUboOffset == drawUboOffset &&
                currentUboBytes == drawUboBytes &&
                drawVertexOffset == currentVertexOffset + currentVertexBytes

            if (canMerge) {
                currentVertexBytes += vertexBytesArr[i]
                currentVertexCount += vertexCountsArr[i]
            } else {
                if (currentVertexCount > 0 && currentTopology != null) {
                    consumer.onDraw(currentTopology, currentVertexOffset, currentVertexBytes, currentVertexCount)
                }

                val topology = PipelineStateKey.unpackTopology(pipelineKey)

                if (!pipelineSame) {
                    consumer.onPipelineChanged(program, pipelineKey)
                }

                if (program.hasUniforms) {
                    if (drawUboOffset != currentUboOffset || drawUboBytes != currentUboBytes) {
                        consumer.onUboChanged(drawUboOffset, drawUboBytes)
                    }
                }

                if (!texturesSame) {
                    consumer.onTextureChanged(program, texMask, drawTex0, drawSamp0, drawOverflow, drawOverflowSamp)
                }

                if (!scissorSame) {
                    if (drawScissorWH != 0L) {
                        consumer.onScissorChanged(
                            true,
                            PipelineStateKey.unpackHigh(drawScissorXY),
                            PipelineStateKey.unpackLow(drawScissorXY),
                            PipelineStateKey.unpackHigh(drawScissorWH),
                            PipelineStateKey.unpackLow(drawScissorWH)
                        )
                    } else {
                        consumer.onScissorChanged(false, 0, 0, 0, 0)
                    }
                }

                currentProgram = program
                currentPipelineKey = pipelineKey
                currentTopology = topology
                currentScissorKnown = true
                currentScissorXY = drawScissorXY
                currentScissorWH = drawScissorWH
                currentTextureMask = texMask
                currentTexture0 = drawTex0
                currentSampler0 = drawSamp0
                currentOverflowTextures = drawOverflow
                currentOverflowSamplers = drawOverflowSamp
                currentUboOffset = drawUboOffset
                currentUboBytes = drawUboBytes
                if (isDirect) {
                    consumer.onDirectDraw(draws, i, topology)
                    currentVertexOffset = -1L
                    currentVertexBytes = 0L
                    currentVertexCount = 0
                } else {
                    currentVertexOffset = drawVertexOffset
                    currentVertexBytes = vertexBytesArr[i]
                    currentVertexCount = vertexCountsArr[i]
                }
            }
        }

        if (currentVertexCount > 0 && currentTopology != null) {
            consumer.onDraw(currentTopology, currentVertexOffset, currentVertexBytes, currentVertexCount)
        }
    }
}
