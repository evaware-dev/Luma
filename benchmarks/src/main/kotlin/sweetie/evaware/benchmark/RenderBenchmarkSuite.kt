package sweetie.evaware.benchmark

import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.RenderBackend
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.shader.Shader
import java.awt.image.BufferedImage

internal class RenderBenchmarkSuite(
    private val backendName: String,
    private val beforeSubmit: () -> Unit = {},
    private val awaitGpu: () -> Unit
) {
    private lateinit var target: RenderTargetHandle
    private lateinit var shader: Shader
    private lateinit var instancedShader: Shader

    fun run() {
        shader = Shader("shaders/solid.frag", "shaders/solid.vert")
        shader.vertices.float2(0)
        shader.load()
        instancedShader = Shader("shaders/solid.frag", "shaders/solid-instanced.vert").instanced(3)
        instancedShader.vertices.float2(0)
        instancedShader.load()
        target = Luma.backend.createRenderTarget(64, 64, false, RenderTargetFormat.RGBA8)

        try {
            println("[Info] Running comparable $backendName benchmarks...")
            benchmark("API draw submissions", 2_000, 15, 50) { submitManyDraws(2_000) }
            benchmark("Batched triangles", 20_000, 15, 50) { submitBatch(20_000) }
            benchmark("Instanced triangles", 20_000, 15, 50) { submitInstanced(20_000) }
            benchmarkTextureUploads()
        } finally {
            target.close()
            shader.close()
            instancedShader.close()
            beforeSubmit()
            Luma.render {}
            awaitGpu()
        }
    }

    private fun benchmark(
        name: String,
        unitsPerSample: Int,
        warmupSamples: Int,
        measurementSamples: Int,
        submit: () -> Unit
    ) {
        repeat(warmupSamples) {
            beforeSubmit()
            submit()
            awaitGpu()
        }

        val submitTimings = LongArray(measurementSamples)
        val completeTimings = LongArray(measurementSamples)
        repeat(measurementSamples) { sample ->
            beforeSubmit()
            val started = System.nanoTime()
            submit()
            val submitted = System.nanoTime()
            awaitGpu()
            val completed = System.nanoTime()
            submitTimings[sample] = Math.round((submitted - started).toDouble() / unitsPerSample)
            completeTimings[sample] = Math.round((completed - started).toDouble() / unitsPerSample)
        }

        println("[Benchmark][$backendName] $name submit ${statistics(submitTimings)}")
        println("[Benchmark][$backendName] $name complete ${statistics(completeTimings)}")
    }

    private fun submitManyDraws(count: Int) {
        renderToTarget {
            shader.attach()
            repeat(count) {
                putOffscreenTriangle(shader)
                shader.draw()
            }
        }
    }

    private fun submitBatch(count: Int) {
        renderToTarget {
            shader.attach()
            repeat(count) { putOffscreenTriangle(shader) }
            shader.draw()
        }
    }

    private fun submitInstanced(count: Int) {
        renderToTarget {
            instancedShader.attach()
            repeat(count) { instancedShader.vertices.vec2(2f, 2f) }
            instancedShader.draw()
        }
    }

    private inline fun renderToTarget(action: () -> Unit) {
        Luma.render {
            Luma.backend.beginRenderTarget(target, null)
            action()
            Luma.backend.endRenderTarget()
        }
    }

    private fun putOffscreenTriangle(targetShader: Shader) {
        targetShader.vertices
            .vec2(2f, 2f)
            .vec2(2.001f, 2f)
            .vec2(2f, 2.001f)
    }

    private fun benchmarkTextureUploads() {
        val backend: RenderBackend = Luma.backend
        val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
        val subImage = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        benchmark("Texture create/upload", 10, 10, 30) {
            Luma.render { repeat(10) { backend.createTexture(image, false).close() } }
        }

        val texture = backend.createTexture(image, false)
        beforeSubmit()
        Luma.render {}
        awaitGpu()
        benchmark("Subimage upload", 100, 10, 30) {
            repeat(100) { backend.updateTexture(texture, 0, 0, subImage) }
            Luma.render {}
        }
        texture.close()
    }

    private fun statistics(values: LongArray): String {
        values.sort()
        val min = values.first()
        val max = values.last()
        val average = values.average()
        val median = percentile(values, 0.5)
        val p05 = percentile(values, 0.05)
        val p95 = percentile(values, 0.95)
        return "avg=${"%.2f".format(average)} ns/op median=$median ns min=$min ns max=$max ns " +
            "p05=$p05 ns p95=$p95 ns spread=${p95 - p05} ns"
    }

    private fun percentile(values: LongArray, percentile: Double): Long {
        val index = Math.round((values.size - 1) * percentile).toInt().coerceIn(values.indices)
        return values[index]
    }
}
