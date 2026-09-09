package sweetie.evaware.benchmark.workload

import java.awt.image.BufferedImage
import java.nio.ByteOrder
import org.lwjgl.system.MemoryUtil
import sweetie.evaware.benchmark.core.BenchmarkRunner
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.BlendFunction
import sweetie.evaware.luma.api.BufferUsage
import sweetie.evaware.luma.api.DepthCompare
import sweetie.evaware.luma.api.IndexBufferHandle
import sweetie.evaware.luma.api.IndexType
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.api.RenderBackend
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.api.VertexBufferHandle
import sweetie.evaware.luma.shader.Shader
import sweetie.evaware.luma.uniform.Float4Uniform
import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.ShaderVertType
import sweetie.evaware.luma.vertex.VertexLayout

internal class RenderBenchmarkSuite(
    private val backendName: String,
    private val beforeSubmit: () -> Unit = {},
    private val awaitGpu: () -> Unit
) {
    private val runner = BenchmarkRunner(backendName, beforeSubmit, awaitGpu)

    private lateinit var firstTarget: RenderTargetHandle
    private lateinit var secondTarget: RenderTargetHandle
    private lateinit var depthTarget: RenderTargetHandle
    private lateinit var shader: Shader
    private lateinit var secondShader: Shader
    private lateinit var lineShader: Shader
    private lateinit var quadShader: Shader
    private lateinit var instancedShader: Shader
    private lateinit var uniformShader: Shader
    private lateinit var uniformColor: Float4Uniform
    private lateinit var texturedShader: Shader
    private lateinit var firstTexture: TextureHandle
    private lateinit var secondTexture: TextureHandle
    private lateinit var directProgram: ProgramHandle
    private lateinit var directUniformProgram: ProgramHandle
    private lateinit var directVertexBuffer: VertexBufferHandle
    private lateinit var directIndexBuffer: IndexBufferHandle
    private val directUniforms = ShaderUniforms()
    private val firstDirectUniforms = ShaderUniforms()
    private val secondDirectUniforms = ShaderUniforms()

    fun run() {
        prepareResources()
        var failure: Throwable? = null
        try {
            println("[Info] Running full $backendName benchmark suite...")
            benchmarkLifecycle()
            benchmarkState()
            benchmarkBindings()
            benchmarkDraws()
            benchmarkPasses()
            benchmarkUploads()
            benchmarkResources()
            benchmarkPostPeakLifecycle()
        } catch (throwable: Throwable) {
            failure = throwable
            throw throwable
        } finally {
            try {
                closeResources()
            } catch (closeFailure: Throwable) {
                if (failure == null) throw closeFailure
                failure.addSuppressed(closeFailure)
            }
        }
    }

    private fun prepareResources() {
        shader = createPositionShader("shaders/solid.frag")
        secondShader = createPositionShader("shaders/solid.frag")
        lineShader = createPositionShader("shaders/solid.frag").drawMode(PrimitiveType.LINES)
        quadShader = createPositionShader("shaders/solid.frag").drawMode(PrimitiveType.QUADS)

        instancedShader = Shader("shaders/solid.frag", "shaders/solid-instanced.vert").instanced(3)
        instancedShader.vertices.float2(0)
        instancedShader.load()

        uniformShader = createPositionShader("shaders/uniform.frag", load = false)
        uniformColor = uniformShader.uniforms.float4("uColor")
        uniformShader.load()

        texturedShader = createPositionShader("shaders/textured.frag")

        val backend = Luma.backend
        firstTarget = backend.createRenderTarget(64, 64, false, RenderTargetFormat.RGBA8)
        secondTarget = backend.createRenderTarget(64, 64, false, RenderTargetFormat.RGBA8)
        depthTarget = backend.createRenderTarget(64, 64, true, RenderTargetFormat.RGBA8)
        firstTexture = backend.createTexture(solidImage(0xFFFFFFFF.toInt()), false)
        secondTexture = backend.createTexture(solidImage(0xFF000000.toInt()), false)
        prepareDirectResources(backend)
        synchronize()
    }

    private fun prepareDirectResources(backend: RenderBackend) {
        val layout = VertexLayout().apply { add(ShaderVertType.FLOAT, 2, 0) }
        val translated = Luma.shaderTranslator.translate(
            resource("shaders/solid.vert"),
            resource("shaders/solid.frag"),
            layout
        )
        val uniformTranslated = Luma.shaderTranslator.translate(
            resource("shaders/solid.vert"),
            resource("shaders/uniform.frag"),
            layout
        )
        directProgram = backend.createProgram(translated.vertexSource, translated.fragmentSource, layout)
        directUniformProgram = backend.createProgram(
            uniformTranslated.vertexSource,
            uniformTranslated.fragmentSource,
            layout
        )
        val firstColor = firstDirectUniforms.float4("uColor")
        val secondColor = secondDirectUniforms.float4("uColor")
        firstDirectUniforms.vec4(firstColor, 1f, 0f, 0f, 1f)
        secondDirectUniforms.vec4(secondColor, 0f, 1f, 0f, 1f)
        directVertexBuffer = backend.createVertexBuffer(6L * Float.SIZE_BYTES, BufferUsage.STATIC)
        directIndexBuffer = backend.createIndexBuffer(3L * Short.SIZE_BYTES, IndexType.UINT16, BufferUsage.STATIC)

        val vertices = MemoryUtil.memAlloc(6 * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        val indices = MemoryUtil.memAlloc(3 * Short.SIZE_BYTES).order(ByteOrder.nativeOrder())
        try {
            vertices.asFloatBuffer()
                .put(2f).put(2f)
                .put(2.001f).put(2f)
                .put(2f).put(2.001f)
            vertices.limit(6 * Float.SIZE_BYTES)
            indices.asShortBuffer().put(0).put(1).put(2)
            indices.limit(3 * Short.SIZE_BYTES)
            backend.updateVertexBuffer(directVertexBuffer, 0L, vertices)
            backend.updateIndexBuffer(directIndexBuffer, 0L, indices)
        } finally {
            MemoryUtil.memFree(indices)
            MemoryUtil.memFree(vertices)
        }
    }

    private fun createPositionShader(fragmentPath: String, load: Boolean = true): Shader {
        val result = Shader(fragmentPath, "shaders/solid.vert")
        result.vertices.float2(0)
        if (load) result.load()
        return result
    }

    private fun benchmarkLifecycle() {
        runner.measure("lifecycle", "empty frame", 10_000) {
            repeat(10_000) { Luma.render {} }
        }

        runner.measure("lifecycle", "target stack transition", 20_000) {
            Luma.render {
                repeat(10_000) {
                    Luma.backend.beginRenderTarget(firstTarget, null)
                    Luma.backend.endRenderTarget()
                }
            }
        }

        runner.measure("lifecycle", "nested target stack transition", 20_000) {
            Luma.render {
                repeat(5_000) {
                    Luma.backend.beginRenderTarget(firstTarget, null)
                    Luma.backend.beginRenderTarget(secondTarget, null)
                    Luma.backend.endRenderTarget()
                    Luma.backend.endRenderTarget()
                }
            }
        }
    }

    private fun benchmarkState() {
        runner.measure("state", "idempotent state command", 120_000) {
            Luma.render {
                repeat(20_000) {
                    Luma.backend.blend(true)
                    Luma.backend.blendFunction(BlendFunction.TRANSLUCENT)
                    Luma.backend.depthTest(false)
                    Luma.backend.depthWrite(false)
                    Luma.backend.depthCompare(DepthCompare.ALWAYS)
                    Luma.backend.cull(false)
                }
            }
        }

        runner.measure("state", "alternating state command", 120_000) {
            Luma.render {
                repeat(20_000) { index ->
                    val enabled = index and 1 == 0
                    Luma.backend.blend(enabled)
                    Luma.backend.blendFunction(
                        if (enabled) BlendFunction.TRANSLUCENT else BlendFunction.PREMULTIPLIED_ALPHA
                    )
                    Luma.backend.depthTest(enabled)
                    Luma.backend.depthWrite(enabled)
                    Luma.backend.depthCompare(if (enabled) DepthCompare.LESS else DepthCompare.ALWAYS)
                    Luma.backend.cull(enabled)
                }
            }
        }
    }

    private fun benchmarkBindings() {
        runner.measure("binding", "redundant texture bind", 20_000) {
            Luma.render {
                repeat(20_000) { Luma.backend.bindTexture(firstTexture, 0) }
            }
        }

        runner.measure("binding", "alternating texture bind", 20_000) {
            Luma.render {
                repeat(20_000) { index ->
                    Luma.backend.bindTexture(if (index and 1 == 0) firstTexture else secondTexture, 0)
                }
            }
        }
    }

    private fun benchmarkDraws() {
        runner.measure("draw", "API draw submission", 2_000) {
            renderToTarget(firstTarget) {
                shader.attach()
                repeat(2_000) {
                    putOffscreenTriangle(shader)
                    shader.draw()
                }
            }
        }

        runner.measure("draw", "clean uniform submission", 2_000) {
            renderToTarget(firstTarget) {
                uniformShader.attach()
                uniformShader.uniforms.vec4(uniformColor, 1f, 1f, 1f, 1f)
                repeat(2_000) {
                    putOffscreenTriangle(uniformShader)
                    uniformShader.draw()
                }
            }
        }

        runner.measure("draw", "dirty uniform submission", 2_000) {
            renderToTarget(firstTarget) {
                uniformShader.attach()
                repeat(2_000) { index ->
                    uniformShader.uniforms.vec4(uniformColor, index.toFloat(), 1f, 1f, 1f)
                    putOffscreenTriangle(uniformShader)
                    uniformShader.draw()
                }
            }
        }

        runner.measure("draw", "program switch", 2_000) {
            renderToTarget(firstTarget) {
                repeat(2_000) { index ->
                    val active = if (index and 1 == 0) shader else secondShader
                    active.attach()
                    putOffscreenTriangle(active)
                    active.draw()
                }
            }
        }

        runner.measure("draw", "texture switch", 2_000) {
            renderToTarget(firstTarget) {
                texturedShader.attach()
                repeat(2_000) { index ->
                    Luma.backend.bindTexture(if (index and 1 == 0) firstTexture else secondTexture, 0)
                    putOffscreenTriangle(texturedShader)
                    texturedShader.draw()
                }
            }
        }

        runner.measure("draw", "batched triangles", 40_000) {
            renderToTarget(firstTarget) {
                shader.attach()
                repeat(40_000) { putOffscreenTriangle(shader) }
                shader.draw()
            }
        }

        runner.measure("draw", "instanced triangles", 40_000) {
            renderToTarget(firstTarget) {
                instancedShader.attach()
                repeat(40_000) { instancedShader.vertices.vec2(2f, 2f) }
                instancedShader.draw()
            }
        }

        runner.measure("draw", "direct buffer submission", 2_000) {
            renderToTarget(firstTarget) {
                Luma.backend.bindVertexBuffer(0, directVertexBuffer)
                repeat(2_000) {
                    Luma.backend.draw(directProgram, directUniforms, PrimitiveType.TRIANGLES, 0, 3)
                }
            }
        }

        runner.measure("draw", "direct indexed submission", 2_000) {
            renderToTarget(firstTarget) {
                Luma.backend.bindVertexBuffer(0, directVertexBuffer)
                Luma.backend.bindIndexBuffer(directIndexBuffer)
                repeat(2_000) {
                    Luma.backend.drawIndexed(directProgram, directUniforms, PrimitiveType.TRIANGLES, 0, 3)
                }
            }
        }

        runner.measure("draw", "alternating uniform sets", 2_000) {
            renderToTarget(firstTarget) {
                Luma.backend.bindVertexBuffer(0, directVertexBuffer)
                repeat(2_000) { index ->
                    Luma.backend.draw(
                        directUniformProgram,
                        if (index and 1 == 0) firstDirectUniforms else secondDirectUniforms,
                        PrimitiveType.TRIANGLES,
                        0,
                        3
                    )
                }
            }
        }

        runner.measure("draw", "line submission", 2_000) {
            renderToTarget(firstTarget) {
                lineShader.attach()
                repeat(2_000) {
                    lineShader.vertices.vec2(2f, 2f).vec2(2.001f, 2.001f)
                    lineShader.draw()
                }
            }
        }

        runner.measure("draw", "quad submission", 2_000) {
            renderToTarget(firstTarget) {
                quadShader.attach()
                repeat(2_000) {
                    quadShader.vertices
                        .vec2(2f, 2f)
                        .vec2(2.001f, 2f)
                        .vec2(2.001f, 2.001f)
                        .vec2(2f, 2.001f)
                    quadShader.draw()
                }
            }
        }
    }

    private fun benchmarkPasses() {
        runner.measure("pass", "target switch with draw", 2_000) {
            Luma.render {
                repeat(1_000) {
                    drawSingleTriangleTo(firstTarget)
                    drawSingleTriangleTo(secondTarget)
                }
            }
        }

        runner.measure("pass", "blend pipeline switch", 2_000) {
            renderToTarget(firstTarget) {
                shader.attach()
                repeat(2_000) { index ->
                    Luma.backend.blend(index and 1 == 0)
                    putOffscreenTriangle(shader)
                    shader.draw()
                }
            }
        }


        runner.measure("pass", "depth pipeline switch", 2_000) {
            renderToTarget(depthTarget) {
                shader.attach()
                repeat(2_000) { index ->
                    Luma.backend.depthTest(index and 1 == 0)
                    Luma.backend.depthWrite(index and 1 == 0)
                    Luma.backend.depthCompare(if (index and 1 == 0) DepthCompare.LESS else DepthCompare.ALWAYS)
                    putOffscreenTriangle(shader)
                    shader.draw()
                }
            }
        }
    }

    private fun benchmarkUploads() {
        val backend: RenderBackend = Luma.backend
        val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
        val subImage = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)

        runner.measure("upload", "texture create", 10, warmupSamples = 10, measurementSamples = 30) {
            Luma.render { repeat(10) { backend.createTexture(image, false).close() } }
        }

        val texture = backend.createTexture(image, false)
        synchronize()
        try {
            runner.measure("upload", "texture subimage", 100, warmupSamples = 10, measurementSamples = 30) {
                repeat(100) { backend.updateTexture(texture, 0, 0, subImage) }
                Luma.render {}
            }
        } finally {
            texture.close()
        }
    }

    private fun benchmarkPostPeakLifecycle() {
        runner.measure("lifecycle", "empty frame after peak", 10_000) {
            repeat(10_000) { Luma.render {} }
        }
    }

    private fun benchmarkResources() {
        val backend = Luma.backend
        runner.measure("resource", "render target create", 4, warmupSamples = 5, measurementSamples = 20) {
            Luma.render {
                repeat(4) {
                    backend.createRenderTarget(64, 64, true, RenderTargetFormat.RGBA8).close()
                }
            }
        }

        runner.measure("resource", "shader create", 1, warmupSamples = 3, measurementSamples = 10) {
            Luma.render {
                createPositionShader("shaders/solid.frag").close()
            }
        }
    }

    private inline fun renderToTarget(target: RenderTargetHandle, action: () -> Unit) {
        Luma.render {
            Luma.backend.beginRenderTarget(target, null)
            try {
                action()
            } finally {
                Luma.backend.endRenderTarget()
            }
        }
    }

    private fun drawSingleTriangleTo(target: RenderTargetHandle) {
        Luma.backend.beginRenderTarget(target, null)
        try {
            shader.attach()
            putOffscreenTriangle(shader)
            shader.draw()
        } finally {
            Luma.backend.endRenderTarget()
        }
    }

    private fun putOffscreenTriangle(targetShader: Shader) {
        targetShader.vertices
            .vec2(2f, 2f)
            .vec2(2.001f, 2f)
            .vec2(2f, 2.001f)
    }

    private fun solidImage(argb: Int): BufferedImage {
        return BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB).also { image ->
            for (y in 0 until image.height) {
                for (x in 0 until image.width) image.setRGB(x, y, argb)
            }
        }
    }

    private fun resource(path: String): String =
        javaClass.classLoader.getResourceAsStream(path)!!.bufferedReader().use { it.readText() }

    private fun synchronize() {
        beforeSubmit()
        Luma.render {}
        awaitGpu()
    }

    private fun closeResources() {
        directIndexBuffer.close()
        directVertexBuffer.close()
        directUniformProgram.close()
        directProgram.close()
        secondTexture.close()
        firstTexture.close()
        depthTarget.close()
        secondTarget.close()
        firstTarget.close()
        texturedShader.close()
        uniformShader.close()
        instancedShader.close()
        quadShader.close()
        lineShader.close()
        secondShader.close()
        shader.close()
        synchronize()
    }
}
