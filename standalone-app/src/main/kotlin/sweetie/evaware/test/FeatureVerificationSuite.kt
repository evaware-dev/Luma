package sweetie.evaware.test

import java.nio.ByteBuffer
import org.lwjgl.BufferUtils
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.*
import sweetie.evaware.luma.list.DrawList
import sweetie.evaware.luma.shader.Shader
import sweetie.evaware.luma.tools.StatsBackend

internal class FeatureVerificationSuite(
    private val backendName: String,
    private val backend: RenderBackend,
    private val awaitGpu: () -> Unit,
    private val readTarget: (RenderTargetHandle) -> ByteBuffer
) : AutoCloseable {
    private val solidShader = Shader("shaders/solid-color.frag", "shaders/triangle.vert")
    private val solidColorUniform = solidShader.uniforms.float4("uColor").also {
        solidShader.vertices.float2(0)
        solidShader.load()
    }

    private val circleMaskShader = Shader("shaders/circle-mask.frag", "shaders/circle-mask.vert").apply {
        vertices.float2(0)
        load()
    }

    private val texturedShader = Shader("shaders/textured-quad.frag", "shaders/textured-quad.vert").apply {
        vertices.float2(0)
        vertices.float2(1)
        load()
    }

    fun runAll() {
        verifyClipMaskGeometryAndSdf()
        verifyBlitRenderTarget()
        verifySamplerStateAndDynamicUpdate()
        if (backend.supportsStencil) {
            verifyNativeStencil()
        }
        verifyDrawListMatchesImmediatePixels()
        verifyGpuTimestamps()
    }

    private fun verifyClipMaskGeometryAndSdf() {
        val size = 64
        val target = backend.createRenderTarget(size, size, useDepth = true, format = RenderTargetFormat.RGBA8)
        try {
            // 1. Float diamond mask -> inside RED, outside GREEN
            Luma.render {
                backend.beginRenderTarget(target, floatArrayOf(0f, 0f, 1f, 1f))
                ClipMask.write(clear = true, backend = backend) {
                    drawDiamond(solidShader)
                }
                ClipMask.inside(backend = backend) {
                    drawFullscreenColor(1f, 0f, 0f, 1f)
                }
                ClipMask.outside(backend = backend) {
                    drawFullscreenColor(0f, 1f, 0f, 1f)
                }
                backend.endRenderTarget()
            }
            val diamondPixels = readTarget(target)
            val center = colorAt(diamondPixels, size, size / 2, size / 2)
            val corner = colorAt(diamondPixels, size, 4, 4)
            check(isRed(center) && isGreen(corner)) {
                "$backendName ClipMask diamond mismatch: center=${center.contentToString()} corner=${corner.contentToString()}"
            }

            // 2. Arbitrary SDF discard circle mask (radius 0.5 in NDC) -> inside RED, outside GREEN
            Luma.render {
                backend.beginRenderTarget(target, floatArrayOf(0f, 0f, 1f, 1f))
                ClipMask.write(clear = true, backend = backend) {
                    circleMaskShader.attach()
                    putFullscreenQuad(circleMaskShader)
                    circleMaskShader.draw()
                }
                ClipMask.inside(backend = backend) {
                    drawFullscreenColor(1f, 0f, 0f, 1f)
                }
                ClipMask.outside(backend = backend) {
                    drawFullscreenColor(0f, 1f, 0f, 1f)
                }
                backend.endRenderTarget()
            }
            val sdfPixels = readTarget(target)
            val sdfCenter = colorAt(sdfPixels, size, size / 2, size / 2)
            val sdfEdgeInside = colorAt(sdfPixels, size, size / 2 + 10, size / 2)
            val sdfOutside = colorAt(sdfPixels, size, 4, 4)
            check(isRed(sdfCenter) && isRed(sdfEdgeInside) && isGreen(sdfOutside)) {
                "$backendName ClipMask SDF mismatch: center=${sdfCenter.contentToString()} edge=${sdfEdgeInside.contentToString()} outside=${sdfOutside.contentToString()}"
            }
            println("[Info] $backendName ClipMask (diamond + SDF discard, inside + outside): center=${sdfCenter.contentToString()} outside=${sdfOutside.contentToString()} => PASS")
        } finally {
            target.close()
        }
    }

    private fun verifyBlitRenderTarget() {
        val srcSize = 32
        val scaledSize = 64
        val src = backend.createRenderTarget(srcSize, srcSize, useDepth = false, format = RenderTargetFormat.RGBA8)
        val dstSame = backend.createRenderTarget(srcSize, srcSize, useDepth = false, format = RenderTargetFormat.RGBA8)
        val dstScaled = backend.createRenderTarget(scaledSize, scaledSize, useDepth = false, format = RenderTargetFormat.RGBA8)
        try {
            Luma.render {
                backend.beginRenderTarget(src, floatArrayOf(0f, 0f, 0f, 1f))
                // Left half RED, right half GREEN
                drawRectColor(-1f, -1f, 0f, 1f, 1f, 0f, 0f, 1f)
                drawRectColor(0f, -1f, 1f, 1f, 0f, 1f, 0f, 1f)
                backend.endRenderTarget()

                backend.blitRenderTarget(src, dstSame, filter = TextureFilter.NEAREST)
                backend.blitRenderTarget(src, dstScaled, filter = TextureFilter.LINEAR)
            }

            val samePixels = readTarget(dstSame)
            val sameLeft = colorAt(samePixels, srcSize, 8, 16)
            val sameRight = colorAt(samePixels, srcSize, 24, 16)
            check(isRed(sameLeft) && isGreen(sameRight)) {
                "$backendName blitRenderTarget 1:1 mismatch: left=${sameLeft.contentToString()} right=${sameRight.contentToString()}"
            }

            val scaledPixels = readTarget(dstScaled)
            val scaledLeft = colorAt(scaledPixels, scaledSize, 12, 32)
            val scaledRight = colorAt(scaledPixels, scaledSize, 52, 32)
            val seamLeft = colorAt(scaledPixels, scaledSize, 31, 32)
            val seamRight = colorAt(scaledPixels, scaledSize, 32, 32)
            val hasLinearBlend = (seamLeft[1] > 20 && seamLeft[0] > 20) || (seamRight[0] > 20 && seamRight[1] > 20)
            check(isRed(scaledLeft) && isGreen(scaledRight) && hasLinearBlend) {
                "$backendName blitRenderTarget scaled mismatch: left=${scaledLeft.contentToString()} right=${scaledRight.contentToString()} seamL=${seamLeft.contentToString()} seamR=${seamRight.contentToString()}"
            }
            println("[Info] $backendName blitRenderTarget (1:1 + 2x LINEAR): 1:1=${sameLeft.contentToString()}/${sameRight.contentToString()} seam=${seamLeft.contentToString()} => PASS")
        } finally {
            dstScaled.close()
            dstSame.close()
            src.close()
        }
    }

    private fun verifySamplerStateAndDynamicUpdate() {
        val width = 64
        val height = 16
        val target = backend.createRenderTarget(width, height, useDepth = false, format = RenderTargetFormat.RGBA8)
        val raw = BufferUtils.createByteBuffer(2 * 1 * 4).apply {
            // Texel 0: RED, Texel 1: BLUE
            put(0xFF.toByte()).put(0).put(0).put(0xFF.toByte())
            put(0).put(0).put(0xFF.toByte()).put(0xFF.toByte())
            flip()
        }
        val tex = backend.createTexture(
            width = 2,
            height = 1,
            data = raw,
            format = TextureFormat.RGBA8,
            mipmap = false,
            sampler = SamplerState.NEAREST_REPEAT
        )
        try {
            // 1. NEAREST_REPEAT across UV u in [0, 2]
            Luma.render {
                backend.beginRenderTarget(target, floatArrayOf(0f, 0f, 0f, 1f))
                backend.bindTexture(tex, 0)
                drawTexturedQuad(uMax = 2f)
                backend.endRenderTarget()
            }
            val repeatPixels = readTarget(target)
            val p0 = colorAt(repeatPixels, width, 8, 8)   // u = 0.25 -> RED
            val p1 = colorAt(repeatPixels, width, 24, 8)  // u = 0.75 -> BLUE
            val p2 = colorAt(repeatPixels, width, 40, 8)  // u = 1.25 (wrapped) -> RED
            val p3 = colorAt(repeatPixels, width, 56, 8)  // u = 1.75 (wrapped) -> BLUE
            check(isRed(p0) && isBlue(p1) && isRed(p2) && isBlue(p3)) {
                "$backendName SamplerState.NEAREST_REPEAT mismatch: p0=${p0.contentToString()} p1=${p1.contentToString()} p2=${p2.contentToString()} p3=${p3.contentToString()}"
            }

            // 2. Dynamically switch to LINEAR_CLAMP on the same texture
            Luma.render {
                backend.beginRenderTarget(target, floatArrayOf(0f, 0f, 0f, 1f))
                backend.setTextureSampler(tex, SamplerState.LINEAR_CLAMP)
                backend.bindTexture(tex, 0)
                drawTexturedQuad(uMax = 2f)
                backend.endRenderTarget()
            }
            val linearPixels = readTarget(target)
            val mid = colorAt(linearPixels, width, 16, 8)      // u = 0.5 -> blended R+B
            val clamped = colorAt(linearPixels, width, 56, 8)  // u = 1.75 clamped to 1.0 -> BLUE
            val isBlended = mid[0] in 70..190 && mid[2] in 70..190 && mid[1] < 40
            check(isBlended && isBlue(clamped)) {
                "$backendName SamplerState.LINEAR_CLAMP mismatch: mid=${mid.contentToString()} clamped=${clamped.contentToString()}"
            }

            // 3. On OpenGL, also verify generateMipmaps executes cleanly
            if (backend.supportsStencil) {
                Luma.render {
                    backend.generateMipmaps(tex)
                    backend.beginRenderTarget(target, floatArrayOf(0f, 0f, 0f, 1f))
                    backend.bindTexture(tex, 0)
                    drawTexturedQuad(uMax = 1f)
                    backend.endRenderTarget()
                }
            }
            println("[Info] $backendName SamplerState (NEAREST_REPEAT + LINEAR_CLAMP): wrapped=${p2.contentToString()} linearMid=${mid.contentToString()} clamped=${clamped.contentToString()} => PASS")
        } finally {
            tex.close()
            target.close()
        }
    }

    private fun verifyNativeStencil() {
        val size = 64
        val target = backend.createRenderTarget(
            size,
            size,
            useDepth = true,
            format = RenderTargetFormat.RGBA8,
            useStencil = true
        )
        check(target.hasStencil) { "$backendName render target must report hasStencil = true" }
        try {
            Luma.render {
                backend.beginRenderTarget(target, floatArrayOf(0f, 0f, 1f, 1f))
                backend.clearStencil(0)

                // Write stencil = 1 inside centered diamond without touching color
                backend.stencilTest(true)
                backend.colorMask(false, false, false, false)
                backend.stencilMask(0xFF)
                backend.stencilFunc(StencilCompare.ALWAYS, 1, 0xFF)
                backend.stencilOp(StencilOp.KEEP, StencilOp.KEEP, StencilOp.REPLACE)
                drawDiamond(solidShader)

                // Draw RED where stencil == 1 and GREEN where stencil != 1
                backend.colorMask(true, true, true, true)
                backend.stencilMask(0x00)
                backend.stencilOp(StencilOp.KEEP, StencilOp.KEEP, StencilOp.KEEP)
                backend.stencilFunc(StencilCompare.EQUAL, 1, 0xFF)
                drawFullscreenColor(1f, 0f, 0f, 1f)
                backend.stencilFunc(StencilCompare.NOT_EQUAL, 1, 0xFF)
                drawFullscreenColor(0f, 1f, 0f, 1f)

                backend.stencilMask(0xFF)
                backend.stencilTest(false)
                backend.endRenderTarget()
            }
            val pixels = readTarget(target)
            val center = colorAt(pixels, size, size / 2, size / 2)
            val corner = colorAt(pixels, size, 4, 4)
            check(isRed(center) && isGreen(corner)) {
                "$backendName native Stencil mismatch: center=${center.contentToString()} corner=${corner.contentToString()}"
            }
            println("[Info] $backendName native GL Stencil: center=${center.contentToString()} corner=${corner.contentToString()} => PASS")
        } finally {
            target.close()
        }
    }

    private fun verifyDrawListMatchesImmediatePixels() {
        val size = 64
        val immediateTarget = backend.createRenderTarget(size, size, useDepth = true, format = RenderTargetFormat.RGBA8)
        val drawListTarget = backend.createRenderTarget(size, size, useDepth = true, format = RenderTargetFormat.RGBA8)
        val list = DrawList()
        try {
            fun emitScene(target: RenderTargetHandle) {
                Luma.backend.beginRenderTarget(target, floatArrayOf(0f, 0f, 1f, 1f))
                Luma.debugGroup("DrawListScene") {
                    // Left half RED, right half GREEN (mutating same ShaderUniforms between draws!)
                    drawRectColor(-1f, -1f, 0f, 1f, 1f, 0f, 0f, 1f)
                    drawRectColor(0f, -1f, 1f, 1f, 0f, 1f, 0f, 1f)
                    // Clipped white diamond in center via Early-Z ClipMask
                    ClipMask.clip(
                        mode = ClipMode.INSIDE,
                        clear = true,
                        mask = { drawDiamond(solidShader) },
                        content = { drawFullscreenColor(1f, 1f, 1f, 1f) }
                    )
                }
                Luma.backend.endRenderTarget()
            }

            // 1. Render directly (immediate reference)
            Luma.render { emitScene(immediateTarget) }

            // 2. Record into DrawList (off-frame), mutate caller uniform afterwards, then replay twice
            list.record { emitScene(drawListTarget) }
            solidShader.uniforms.vec4(solidColorUniform, 0f, 0f, 0f, 1f)

            Luma.render { list.replay() }
            Luma.render { list.replay() }

            val expected = readTarget(immediateTarget)
            val actual = readTarget(drawListTarget)
            check(expected.remaining() == actual.remaining()) {
                "$backendName DrawList buffer size mismatch: ${expected.remaining()} vs ${actual.remaining()}"
            }
            for (i in 0 until expected.remaining()) {
                val e = expected.get(i).toInt() and 0xFF
                val a = actual.get(i).toInt() and 0xFF
                check(e == a) {
                    val px = i / 4
                    "$backendName DrawList pixel mismatch at (${px % size},${px / size}) channel ${i % 4}: expected=$e actual=$a"
                }
            }
            val left = colorAt(actual, size, 8, size / 2)
            val right = colorAt(actual, size, size - 8, size / 2)
            val center = colorAt(actual, size, size / 2, size / 2)
            check(isRed(left) && isGreen(right) && center[0] > 240 && center[1] > 240 && center[2] > 240) {
                "$backendName DrawList sanity check failed: left=${left.contentToString()} right=${right.contentToString()} center=${center.contentToString()}"
            }
            println("[Info] $backendName DrawList pixel-exact replay (${list.commandCount} ops, ${list.vertexFloatCount} floats): left=${left.contentToString()} center=${center.contentToString()} right=${right.contentToString()} => PASS")
        } finally {
            list.close()
            drawListTarget.close()
            immediateTarget.close()
        }
    }

    private fun verifyGpuTimestamps() {
        check(backend.supportsGpuTimestamps) {
            "$backendName expected supportsGpuTimestamps == true on this GPU"
        }
        val size = 128
        val target = backend.createRenderTarget(size, size, useDepth = false, format = RenderTargetFormat.RGBA8)
        val previousBackend = Luma.backend
        val stats = StatsBackend(backend, gpuTimers = true)
        Luma.backend = stats
        // Create a temporary shader program through StatsBackend so StatsBackend tracks its draws cleanly
        val timedShader = Shader("shaders/solid-color.frag", "shaders/triangle.vert")
        val timedColor = timedShader.uniforms.float4("uColor")
        timedShader.vertices.float2(0)
        timedShader.load()
        try {
            // Submit a few frames with nested debug groups so the ring populates and harvests cleanly
            repeat(4) {
                Luma.render {
                    Luma.backend.beginRenderTarget(target, floatArrayOf(0f, 0f, 0f, 1f))
                    Luma.debugGroup("OuterPass") {
                        Luma.debugGroup("FillQuads") {
                            timedShader.attach()
                            timedShader.uniforms.vec4(timedColor, 1f, 0.25f, 0.5f, 1f)
                            repeat(64) {
                                timedShader.vertices
                                    .vec2(-1f, -1f).vec2(1f, -1f).vec2(1f, 1f)
                                    .vec2(-1f, -1f).vec2(1f, 1f).vec2(-1f, 1f)
                            }
                            timedShader.draw()
                        }
                    }
                    Luma.backend.endRenderTarget()
                }
            }
            awaitGpu()
            stats.pollGpuTimers()

            check(stats.lastGpuFrameNanos > 0L) {
                "$backendName expected lastGpuFrameNanos > 0, got ${stats.lastGpuFrameNanos}"
            }
            check(stats.gpuZoneCount == 2) {
                "$backendName expected 2 GPU zones, got ${stats.gpuZoneCount}"
            }
            val outer = stats.gpuZones[0]
            val inner = stats.gpuZones[1]
            check(outer.name == "OuterPass" && outer.depth == 0 && outer.nanos > 0L) {
                "$backendName invalid outer GPU zone: name=${outer.name}, depth=${outer.depth}, nanos=${outer.nanos}"
            }
            check(inner.name == "FillQuads" && inner.depth == 1 && inner.nanos > 0L) {
                "$backendName invalid inner GPU zone: name=${inner.name}, depth=${inner.depth}, nanos=${inner.nanos}"
            }
            println("[Info] $backendName GPU Timestamps: frame=${stats.lastGpuFrameNanos} ns, OuterPass=${outer.nanos} ns, FillQuads=${inner.nanos} ns => PASS")
        } finally {
            timedShader.close()
            Luma.backend = previousBackend
            awaitGpu()
            stats.closeGpuTimers()
            target.close()
        }
    }

    private fun drawDiamond(shader: Shader) {
        shader.attach()
        shader.uniforms.vec4(solidColorUniform, 1f, 1f, 1f, 1f)
        shader.vertices
            .vec2(0f, 0.5f).vec2(0.5f, 0f).vec2(0f, -0.5f)
            .vec2(0f, 0.5f).vec2(0f, -0.5f).vec2(-0.5f, 0f)
        shader.draw()
    }

    private fun drawFullscreenColor(r: Float, g: Float, b: Float, a: Float) {
        drawRectColor(-1f, -1f, 1f, 1f, r, g, b, a)
    }

    private fun drawRectColor(x0: Float, y0: Float, x1: Float, y1: Float, r: Float, g: Float, b: Float, a: Float) {
        solidShader.attach()
        solidShader.uniforms.vec4(solidColorUniform, r, g, b, a)
        solidShader.vertices
            .vec2(x0, y0).vec2(x1, y0).vec2(x1, y1)
            .vec2(x0, y0).vec2(x1, y1).vec2(x0, y1)
        solidShader.draw()
    }

    private fun putFullscreenQuad(shader: Shader) {
        shader.vertices
            .vec2(-1f, -1f).vec2(1f, -1f).vec2(1f, 1f)
            .vec2(-1f, -1f).vec2(1f, 1f).vec2(-1f, 1f)
    }

    private fun drawTexturedQuad(uMax: Float) {
        texturedShader.attach()
        texturedShader.vertices
            .vec2(-1f, -1f).vec2(0f, 0f)
            .vec2(1f, -1f).vec2(uMax, 0f)
            .vec2(1f, 1f).vec2(uMax, 1f)
            .vec2(-1f, -1f).vec2(0f, 0f)
            .vec2(1f, 1f).vec2(uMax, 1f)
            .vec2(-1f, 1f).vec2(0f, 1f)
        texturedShader.draw()
    }

    override fun close() {
        texturedShader.close()
        circleMaskShader.close()
        solidShader.close()
    }

    private companion object {
        fun isGreen(color: IntArray): Boolean = color[0] < 60 && color[1] > 200 && color[2] < 60
    }
}
