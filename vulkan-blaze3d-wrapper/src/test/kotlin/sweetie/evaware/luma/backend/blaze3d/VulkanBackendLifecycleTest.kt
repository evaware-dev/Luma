package sweetie.evaware.luma.backend.blaze3d

import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sweetie.evaware.luma.api.BufferUsage
import sweetie.evaware.luma.api.IndexType
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.api.TextureFormat
import sweetie.evaware.luma.backend.blaze3d.preset.*
import sweetie.evaware.luma.shader.translator.ShaderAttributeInfo
import sweetie.evaware.luma.shader.translator.ShaderReflection
import sweetie.evaware.luma.shader.translator.ShaderSamplerInfo
import sweetie.evaware.luma.shader.translator.ShaderUniformInfo
import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.ShaderVertType
import sweetie.evaware.luma.vertex.VertexLayout

class VulkanBackendLifecycleTest {
    @Test
    fun multipleBackendsAreIsolatedWithoutInitializingGlobalPreset() {
        assertFalse(VulkanPresets.isInitialized)

        val presetA = FakeVulkanPreset("preset-A")
        val presetB = FakeVulkanPreset("preset-B")
        val backendA = Backend(presetA, VulkanBackendConfig(precompileDefaultPipeline = false))
        val backendB = Backend(presetB, VulkanBackendConfig(precompileDefaultPipeline = false))

        try {
            val bufA = backendA.createVertexBuffer(64, BufferUsage.STATIC)
            val bufB = backendB.createVertexBuffer(64, BufferUsage.STATIC)

            bufA.close()
            assertEquals(0, presetA.closedBuffers)
            assertEquals(0, presetB.closedBuffers)

            // Submitting frame on backendB must NOT retire backendA's pending resources
            backendB.beginFrame()
            backendB.endFrame()
            assertEquals(0, presetA.closedBuffers)
            assertEquals(0, presetA.submittedEncoders)

            // Submitting and completing fence on backendA retires only backendA's buffer
            backendA.beginFrame()
            backendA.endFrame()
            assertEquals(1, presetA.submittedEncoders)
            assertEquals(0, presetA.closedBuffers)

            presetA.completeAllFences()
            backendA.beginFrame()
            assertEquals(1, presetA.closedBuffers)
            assertEquals(0, presetB.closedBuffers)

            bufB.close()
        } finally {
            presetA.autoCompleteOnWait = true
            presetB.autoCompleteOnWait = true
            backendA.close()
            backendB.close()
        }

        assertFalse(VulkanPresets.isInitialized)
        assertTrue(presetB.closedBuffers >= 1)
    }

    @Test
    fun programCloseRetiresCachedPipelines() {
        val preset = FakeVulkanPreset("pipeline-retire", autoCompleteOnWait = true)
        val backend = Backend(preset, VulkanBackendConfig(precompileDefaultPipeline = true))
        try {
            val layout = VertexLayout().apply { add(ShaderVertType.FLOAT, 2, 0) }
            val program = backend.createProgram("", "", layout)
            assertEquals(2, preset.createdPipelines)
            assertEquals(0, preset.closedPipelines)

            program.close()
            backend.beginFrame()
            backend.endFrame()
            assertEquals(0, preset.closedPipelines)

            // Post-record action deferred pipelines; next frame attaches fence and closeAll drains them
            backend.beginFrame()
            backend.endFrame()
            preset.completeAllFences()
            backend.beginFrame()
            assertEquals(2, preset.closedPipelines)
        } finally {
            backend.close()
        }
    }

    @Test
    fun retirementQueueCollectsAllCompletedEvenWhenOneResourceThrows() {
        val queue = VulkanRetirementQueue()
        val preset = FakeVulkanPreset("throw-test")
        var closedSecond = false

        queue.defer(AutoCloseable { throw IllegalStateException("first failure") })
        queue.defer(AutoCloseable { closedSecond = true })

        val encoder = preset.createCommandEncoder()
        val submission = queue.attachTo(encoder)
        encoder.submit()
        queue.submitted(submission)

        // Not complete yet -> nothing closed
        queue.collectCompleted()
        assertFalse(closedSecond)

        preset.completeAllFences()
        val thrown = runCatching { queue.collectCompleted() }.exceptionOrNull()
        assertTrue(thrown is IllegalStateException)
        assertTrue(closedSecond)
    }

    @Test
    fun uniformsSkipCleanDrawsUpdateOnMutationAndHandleAlternatingSets() {
        val preset = FakeVulkanPreset("uniform-test", autoCompleteOnWait = true)
        val backend = Backend(preset, VulkanBackendConfig(precompileDefaultPipeline = false))
        try {
            val layout = VertexLayout().apply { add(ShaderVertType.FLOAT, 2, 0) }
            val shaderSrc = "layout(std140) uniform LumaUniforms {\n vec4 uColor;\n };"
            val program = backend.createProgram(shaderSrc, shaderSrc, layout)
            val target = backend.createRenderTarget(16, 16, false, RenderTargetFormat.RGBA8)

            val first = ShaderUniforms()
            val firstColor = first.float4("uColor")
            val unused = first.float1("uUnused")
            first.vec4(firstColor, 1f, 0f, 0f, 1f)

            val second = ShaderUniforms()
            val secondColor = second.float4("uColor")
            second.vec4(secondColor, 0f, 1f, 0f, 1f)

            val verts = ByteBuffer.allocateDirect(6 * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer()
            verts.put(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f))

            backend.beginFrame()
            backend.beginRenderTarget(target, null)
            // Draw 1: initial upload for 'first'
            backend.draw(program, verts, 3, first, PrimitiveType.TRIANGLES)
            val offset1 = (program as Program).uboCacheOffset
            // Draw 2: clean draw with 'first' -> reuses exact same UBO offset
            backend.draw(program, verts, 3, first, PrimitiveType.TRIANGLES)
            assertEquals(offset1, program.uboCacheOffset)
            // Draw 3: unrelated uniform mutated -> still reuses UBO offset
            first.float1(unused, 99f)
            backend.draw(program, verts, 3, first, PrimitiveType.TRIANGLES)
            assertEquals(offset1, program.uboCacheOffset)
            // Draw 4: active uniform mutated -> writes new UBO offset
            first.vec4(firstColor, 0.5f, 0f, 0f, 1f)
            backend.draw(program, verts, 3, first, PrimitiveType.TRIANGLES)
            val offset2 = program.uboCacheOffset
            assertNotEquals(offset1, offset2)
            // Draw 5: switch to 'second' -> new UBO offset
            backend.draw(program, verts, 3, second, PrimitiveType.TRIANGLES)
            val offset3 = program.uboCacheOffset
            assertNotEquals(offset2, offset3)
            // Draw 6: switch back to 'first' (clean) -> must still write fresh UBO offset
            backend.draw(program, verts, 3, first, PrimitiveType.TRIANGLES)
            val offset4 = program.uboCacheOffset
            assertNotEquals(offset3, offset4)
            // Draw 7: repeated 'first' -> clean skip, reuses offset4
            backend.draw(program, verts, 3, first, PrimitiveType.TRIANGLES)
            assertEquals(offset4, program.uboCacheOffset)

            backend.endRenderTarget()
            backend.endFrame()

            target.close()
            program.close()
        } finally {
            backend.close()
        }
    }

    @Test
    fun beginFrameResetsBoundTexturesAndSkipsRedundantSequentialIndexBind() {
        val preset = FakeVulkanPreset("state-test", autoCompleteOnWait = true)
        val backend = Backend(preset, VulkanBackendConfig(precompileDefaultPipeline = false))
        try {
            val layout = VertexLayout().apply { add(ShaderVertType.FLOAT, 2, 0) }
            val program = backend.createProgram("", "uniform sampler2D Sampler0;", layout)
            val target = backend.createRenderTarget(16, 16, false, RenderTargetFormat.RGBA8)
            val texture = backend.createTexture(BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), false)
            val quadVerts = ByteBuffer.allocateDirect(8 * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer()
            quadVerts.put(floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f))

            // Frame 1: bind texture and draw two quads separated by blend change (forces 2 pass draws)
            backend.beginFrame()
            backend.beginRenderTarget(target, null)
            backend.bindTexture(texture, 0)
            backend.blend(true)
            backend.draw(program, quadVerts, 4, ShaderUniforms(), PrimitiveType.QUADS)
            backend.blend(false)
            backend.draw(program, quadVerts, 4, ShaderUniforms(), PrimitiveType.QUADS)
            backend.endRenderTarget()
            backend.endFrame()

            assertEquals(1, preset.textureBindCalls)
            // Both quad draws share the same sequential buffer so setSequentialIndexBuffer runs only once
            assertEquals(1, preset.sequentialIndexBindCalls)

            // Frame 2: do NOT bind texture -> boundTextureMask was reset in beginFrame()
            preset.textureBindCalls = 0
            backend.beginFrame()
            backend.beginRenderTarget(target, null)
            backend.draw(program, quadVerts, 4, ShaderUniforms(), PrimitiveType.QUADS)
            backend.endRenderTarget()
            backend.endFrame()
            assertEquals(0, preset.textureBindCalls)

            texture.close()
            target.close()
            program.close()
        } finally {
            backend.close()
        }
    }

    @Test
    fun drawCallRecorderAndVulkanBufferTrimAfterLowWaterFrames() {
        val recorder = DrawCallRecorder(minRetainedCapacity = 8, lowWaterFramesThreshold = 3)
        repeat(100) { recorder.obtain() }
        assertTrue(recorder.capacity >= 100)
        recorder.reset()
        // 3 low-water frames with 2 draws each -> trims down to max(8, 2 * 2) = 8
        repeat(3) {
            repeat(2) { recorder.obtain() }
            recorder.reset()
        }
        assertEquals(8, recorder.capacity)

        val preset = FakeVulkanPreset("slice-trim")
        val queue = VulkanRetirementQueue()
        val buffer = VulkanBuffer(
            label = { "test" },
            usage = 32,
            initialCapacity = 256,
            preset = preset,
            retirement = queue,
            initialSliceCapacity = 4,
            maxSliceCapacity = 16,
            lowWaterFramesThreshold = 3
        )
        buffer.ensureCapacity(1024)
        // Spike above maxSliceCapacity: cache stays bounded at 16
        repeat(24) { i -> buffer.slice(i.toLong() * 4, 4) }
        assertEquals(16, buffer.sliceCacheCapacity)
        buffer.beginFrame()
        // 3 low-water frames with 1 slice each -> trims back to initial capacity 4
        repeat(3) {
            buffer.slice(0, 4)
            buffer.beginFrame()
        }
        assertEquals(4, buffer.sliceCacheCapacity)
        buffer.close()
    }

    @Test
    fun vulkanBufferSliceCacheResistsDynamicOffsetDrift() {
        val preset = FakeVulkanPreset("slice-drift")
        val queue = VulkanRetirementQueue()
        val buffer = VulkanBuffer(
            label = { "drift" },
            usage = 32,
            initialCapacity = 4096,
            preset = preset,
            retirement = queue,
            initialSliceCapacity = 32,
            maxSliceCapacity = 64,
            lowWaterFramesThreshold = 60
        )
        buffer.ensureCapacity(4096)

        // Frame 1: slices at 0, 64, 128, ..., 576
        val firstFrameSlices = (0 until 10).map { i -> buffer.slice(i.toLong() * 64L, 64L) }
        buffer.beginFrame()

        // Frame 2: draw 0 grows to 128 bytes, shifting draws 1..9 to offsets 128..640
        // Draws 1..8 (offsets 128..576) MUST hit the exact same cached slices from frame 1!
        val shifted0 = buffer.slice(0L, 128L)
        assertEquals(128L, shifted0.length)
        for (i in 1 until 9) {
            val hit = buffer.slice((i + 1).toLong() * 64L, 64L)
            assertTrue("Expected cache hit for shifted offset ${(i + 1) * 64}", hit === firstFrameSlices[i + 1])
        }
        buffer.close()
    }

    @Test
    fun pipelineStateKeyRoundTripsAllFieldsWithoutBitCollision() {
        val blends = listOf(
            sweetie.evaware.luma.api.BlendFunction.TRANSLUCENT,
            sweetie.evaware.luma.api.BlendFunction.PREMULTIPLIED_ALPHA,
            sweetie.evaware.luma.api.BlendFunction.OVERLAY,
            sweetie.evaware.luma.api.BlendFunction(
                sweetie.evaware.luma.api.BlendFactor.SRC_ALPHA_SATURATE,
                sweetie.evaware.luma.api.BlendFactor.ONE_MINUS_CONSTANT_ALPHA,
                sweetie.evaware.luma.api.BlendOp.MAX,
                sweetie.evaware.luma.api.BlendFactor.CONSTANT_COLOR,
                sweetie.evaware.luma.api.BlendFactor.DST_ALPHA,
                sweetie.evaware.luma.api.BlendOp.REVERSE_SUBTRACT
            )
        )
        for (topology in PrimitiveType.entries) {
            for (blendEnabled in booleanArrayOf(false, true)) {
                for (blend in blends) {
                    for (depthEnabled in booleanArrayOf(false, true)) {
                        for (depthWrite in booleanArrayOf(false, true)) {
                            for (depthFunc in sweetie.evaware.luma.api.DepthCompare.entries) {
                                for (cull in booleanArrayOf(false, true)) {
                                    val key = PipelineStateKey.pack(
                                        topology, blendEnabled, blend, depthEnabled, depthWrite, depthFunc, cull
                                    )
                                    assertTrue("Bit 31 must remain free for hasDepthAttachment", key >= 0)
                                    assertEquals(topology, PipelineStateKey.unpackTopology(key))
                                    assertEquals(blendEnabled, PipelineStateKey.unpackBlendEnabled(key))
                                    assertEquals(blend, PipelineStateKey.unpackBlendFunction(key))
                                    assertEquals(depthEnabled, PipelineStateKey.unpackDepthEnabled(key))
                                    assertEquals(depthEnabled && depthWrite, PipelineStateKey.unpackDepthWrite(key))
                                    assertEquals(
                                        if (depthEnabled) depthFunc else sweetie.evaware.luma.api.DepthCompare.ALWAYS,
                                        PipelineStateKey.unpackDepthFunc(key)
                                    )
                                    assertEquals(cull, PipelineStateKey.unpackCullEnabled(key))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun textureUploadQueueHasPendingVolatileFlagTracksState() {
        val preset = FakeVulkanPreset("upload-flag", autoCompleteOnWait = true)
        val queue = VulkanRetirementQueue()
        val uploads = TextureUploadQueue(1024L * 1024L, queue)
        try {
            assertFalse(uploads.hasPending())
            val texture = preset.createTexture({ "t" }, 5, PresetFormat.RGBA8_UNORM, 2, 2)
            uploads.enqueue(texture, BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), 0, 0)
            assertTrue(uploads.hasPending())

            val encoder = preset.createCommandEncoder()
            uploads.record(encoder)
            assertFalse(uploads.hasPending())
            encoder.submit()
            queue.closeAll(preset::createCommandEncoder)
            texture.close()
        } finally {
            uploads.close()
            assertFalse(uploads.hasPending())
        }
    }

    @Test
    fun programUsesStructuredReflectionDirectlyAndFallsBackWhenNull() {
        val preset = FakeVulkanPreset("reflection-test", autoCompleteOnWait = true)
        val backend = Backend(preset, VulkanBackendConfig(precompileDefaultPipeline = false))
        try {
            val layout = VertexLayout().apply { add(ShaderVertType.FLOAT, 2, 0) }
            val reflection = ShaderReflection(
                attributes = listOf(ShaderAttributeInfo(0, "vec2", "aPos", "Position")),
                uniforms = listOf(ShaderUniformInfo("uTint", "vec4"), ShaderUniformInfo("uScale", "float")),
                samplers = listOf(ShaderSamplerInfo("uTex", "Sampler2", "sampler2D", unit = 2, binding = 3))
            )
            // Intentionally pass blank sources so reflection is the ONLY possible source of metadata
            val reflected = backend.createProgram("", "", layout, reflection) as Program
            assertEquals(mapOf(0 to "Position"), reflected.attributeNames)
            assertEquals(listOf(UniformInfo("uTint", "vec4"), UniformInfo("uScale", "float")), reflected.uniformInfos)
            assertEquals(listOf("Sampler2"), reflected.samplers)
            assertEquals(listOf(SamplerBinding("Sampler2", 2)), reflected.samplerBindings)

            // Fallback when reflection is null still parses raw GLSL sources
            val raw = backend.createProgram(
                "// @in 0 vec2 aPos Position\nlayout(std140) uniform LumaUniforms { vec4 uColor; };",
                "uniform sampler2D Sampler0;",
                layout,
                null
            ) as Program
            assertEquals(mapOf(0 to "Position"), raw.attributeNames)
            assertEquals(listOf(UniformInfo("uColor", "vec4")), raw.uniformInfos)
            assertEquals(listOf(SamplerBinding("Sampler0", 0)), raw.samplerBindings)

            reflected.close()
            raw.close()
        } finally {
            backend.close()
        }
    }

    @Test
    fun textureFormatsSelectExpectedPresetFormatsAndValidateUploadSizes() {
        val preset = FakeVulkanPreset("format-test", autoCompleteOnWait = true)
        val backend = Backend(preset, VulkanBackendConfig(precompileDefaultPipeline = false))
        try {
            val r8 = backend.createTexture(BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB), false, TextureFormat.R8)
            val rg8 = backend.createTexture(BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB), false, TextureFormat.RG8)
            val rawHalf = ByteBuffer.allocateDirect(2 * 2 * TextureFormat.RGBA16F.bytesPerPixel)
            val rgba16f = backend.createTexture(2, 2, rawHalf, TextureFormat.RGBA16F, false)

            assertEquals(TextureFormat.R8, r8.format)
            assertEquals(TextureFormat.RG8, rg8.format)
            assertEquals(TextureFormat.RGBA16F, rgba16f.format)
            assertEquals(
                listOf(PresetFormat.R8_UNORM, PresetFormat.RG8_UNORM, PresetFormat.RGBA16_FLOAT),
                preset.createdTextureFormats
            )

            // BufferedImage upload to RGBA16F must be rejected
            val badImg = runCatching {
                backend.createTexture(BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), false, TextureFormat.RGBA16F)
            }.exceptionOrNull()
            assertTrue(badImg is IllegalArgumentException)

            // Too-small raw buffer upload must be rejected
            val tooSmall = runCatching {
                backend.updateTexture(rg8, 0, 0, 3, 2, ByteBuffer.allocate(5))
            }.exceptionOrNull()
            assertTrue(tooSmall is IllegalArgumentException)

            backend.beginFrame()
            backend.endFrame()
            assertEquals(listOf(6, 12, 32), preset.textureUploadByteSizes)

            r8.close()
            rg8.close()
            rgba16f.close()
        } finally {
            backend.close()
        }
    }

    @Test
    fun debugGroupsReplayAcrossRenderPassesAndBalanceOnClose() {
        val preset = FakeVulkanPreset("debug-groups", autoCompleteOnWait = true)
        val backend = Backend(preset, VulkanBackendConfig(precompileDefaultPipeline = false))
        try {
            val layout = VertexLayout().apply { add(ShaderVertType.FLOAT, 2, 0) }
            val program = backend.createProgram("", "", layout)
            val target1 = backend.createRenderTarget(16, 16, false, RenderTargetFormat.RGBA8)
            val target2 = backend.createRenderTarget(16, 16, false, RenderTargetFormat.RGBA8)
            val verts = ByteBuffer.allocateDirect(6 * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer()
            verts.put(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f))

            backend.beginFrame()
            backend.pushDebugGroup("Frame")
            backend.beginRenderTarget(target1, null)
            backend.draw(program, verts, 3, ShaderUniforms(), PrimitiveType.TRIANGLES)
            backend.endRenderTarget()
            backend.beginRenderTarget(target2, null)
            backend.pushDebugGroup("Pass2")
            backend.draw(program, verts, 3, ShaderUniforms(), PrimitiveType.TRIANGLES)
            backend.popDebugGroup()
            backend.endRenderTarget()
            backend.popDebugGroup()
            backend.endFrame()

            assertEquals(
                listOf("push:Frame", "pop", "push:Frame", "push:Pass2", "pop", "pop"),
                preset.debugGroupEvents
            )

            target2.close()
            target1.close()
            program.close()
        } finally {
            backend.close()
        }
    }

    private class FakeVulkanPreset(
        override val name: String,
        var autoCompleteOnWait: Boolean = false
    ) : VulkanPreset {
        override val minUniformOffsetAlignment: Int = 64

        var closedBuffers = 0
        var createdPipelines = 0
        var closedPipelines = 0
        var submittedEncoders = 0
        var textureBindCalls = 0
        var sequentialIndexBindCalls = 0
        val createdTextureFormats = ArrayList<PresetFormat>()
        val textureUploadByteSizes = ArrayList<Int>()
        val debugGroupEvents = ArrayList<String>()

        private val fences = ArrayList<FakeFence>()
        private val sharedSequentialBuffer = FakeBuffer(65536)

        fun completeAllFences() {
            for (fence in fences) fence.completed = true
        }

        override fun createBuffer(label: () -> String, usage: Int, sizeBytes: Long): PresetGpuBuffer =
            FakeBuffer(sizeBytes)

        override fun createTexture(
            label: () -> String,
            usage: Int,
            format: PresetFormat,
            width: Int,
            height: Int
        ): PresetGpuTexture {
            createdTextureFormats.add(format)
            return FakeTexture(width, height)
        }

        override fun createTextureView(texture: PresetGpuTexture): PresetGpuTextureView = FakeTextureView(texture)

        override fun createTextureView(texture: PresetGpuTexture, baseMip: Int, mipCount: Int): PresetGpuTextureView =
            FakeTextureView(texture)

        override fun createSampler(
            addressMode: PresetAddressMode,
            filterMode: PresetFilterMode,
            maxAnisotropy: Int
        ): PresetGpuSampler = object : PresetGpuSampler {
            override fun close() {}
        }

        override fun createPipeline(descriptor: PresetPipelineDescriptor): PresetPipeline {
            createdPipelines++
            return object : PresetPipeline {
                override fun close() {
                    closedPipelines++
                }
            }
        }

        override fun precompilePipeline(
            pipeline: PresetPipeline,
            vertexSource: String,
            fragmentSource: String
        ): PresetCompilationResult = PresetCompilationResult(true)

        override fun createCommandEncoder(): PresetCommandEncoder = FakeCommandEncoder()

        override fun bindDefaultUniforms(pass: PresetRenderPass) {}

        override fun getSequentialQuadBuffer(indexCount: Int): PresetSequentialBuffer =
            object : PresetSequentialBuffer {
                override val buffer: PresetGpuBuffer = sharedSequentialBuffer
                override val indexCount: Int = indexCount
            }

        override fun getLastDebugMessages(): List<String> = emptyList()

        override fun wrapTextureView(rawView: Any): PresetGpuTextureView = rawView as PresetGpuTextureView

        private inner class FakeBuffer(override val sizeBytes: Long) : PresetGpuBuffer {
            override fun slice(offset: Long, length: Long): PresetGpuBufferSlice = FakeSlice(offset, length)
            override fun slice(): PresetGpuBufferSlice = FakeSlice(0, sizeBytes)
            override fun close() {
                closedBuffers++
            }
        }

        private class FakeSlice(override val offset: Long, override val length: Long) : PresetGpuBufferSlice

        private class FakeTexture(override val width: Int, override val height: Int) : PresetGpuTexture {
            override fun close() {}
        }

        private class FakeTextureView(override val texture: PresetGpuTexture) : PresetGpuTextureView {
            override fun getWidth(mip: Int): Int = texture.width
            override fun getHeight(mip: Int): Int = texture.height
            override fun close() {}
        }

        private inner class FakeFence : PresetFence {
            var completed = false
            override fun awaitCompletion(timeoutNs: Long): Boolean {
                if (timeoutNs > 0L && autoCompleteOnWait) completed = true
                return completed
            }
            override fun close() {}
        }

        private inner class FakeCommandEncoder : PresetCommandEncoder {
            override fun writeToBuffer(slice: PresetGpuBufferSlice, data: ByteBuffer) {}
            override fun writeToTexture(
                texture: PresetGpuTexture,
                data: ByteBuffer,
                mip: Int,
                depth: Int,
                x: Int,
                y: Int,
                width: Int,
                height: Int
            ) {
                textureUploadByteSizes.add(data.remaining())
            }
            override fun clearColorTexture(texture: PresetGpuTexture, red: Float, green: Float, blue: Float, alpha: Float) {}
            override fun clearDepthTexture(texture: PresetGpuTexture, depth: Double) {}
            override fun clearColorAndDepthTextures(
                color: PresetGpuTexture,
                red: Float,
                green: Float,
                blue: Float,
                alpha: Float,
                depth: PresetGpuTexture,
                depthValue: Double
            ) {}
            override fun createRenderPass(
                label: () -> String,
                colorView: PresetGpuTextureView,
                depthView: PresetGpuTextureView?
            ): PresetRenderPass = FakeRenderPass()

            override fun createFence(): PresetFence = FakeFence().also { fences.add(it) }

            override fun submit() {
                submittedEncoders++
            }
        }

        private inner class FakeRenderPass : PresetRenderPass {
            override fun pushDebugGroup(label: String) {
                debugGroupEvents.add("push:$label")
            }
            override fun popDebugGroup() {
                debugGroupEvents.add("pop")
            }
            override fun setPipeline(pipeline: PresetPipeline) {}
            override fun setUniform(name: String, slice: PresetGpuBufferSlice) {}
            override fun bindTexture(name: String, view: PresetGpuTextureView, sampler: PresetGpuSampler) {
                textureBindCalls++
            }
            override fun enableScissor(x: Int, y: Int, width: Int, height: Int) {}
            override fun disableScissor() {}
            override fun setVertexBuffer(slot: Int, slice: PresetGpuBufferSlice) {}
            override fun setIndexBuffer(buffer: PresetGpuBuffer, indexType: IndexType) {}
            override fun setSequentialIndexBuffer(buffer: PresetSequentialBuffer) {
                sequentialIndexBindCalls++
            }
            override fun draw(vertexCount: Int, instanceCount: Int, firstVertex: Int, firstInstance: Int) {}
            override fun drawIndexed(
                indexCount: Int,
                instanceCount: Int,
                firstIndex: Int,
                baseVertex: Int,
                firstInstance: Int
            ) {}
            override fun close() {}
        }
    }
}
