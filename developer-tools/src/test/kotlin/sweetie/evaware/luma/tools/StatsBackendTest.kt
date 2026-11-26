package sweetie.evaware.luma.tools

import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import sweetie.evaware.luma.api.GpuTimestampPool
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.api.RenderBackend
import sweetie.evaware.luma.api.RenderTargetFormat
import sweetie.evaware.luma.api.RenderTargetHandle
import sweetie.evaware.luma.api.SamplerState
import sweetie.evaware.luma.api.TextureFilter
import sweetie.evaware.luma.api.TextureFormat
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.shader.translator.ShaderReflection
import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.ShaderVertType
import sweetie.evaware.luma.vertex.VertexCursor
import sweetie.evaware.luma.vertex.VertexInputLayout
import sweetie.evaware.luma.vertex.VertexLayout

class StatsBackendTest {
    @Test
    fun countsFrameOperationsWithoutReplacingSnapshots() {
        val delegate = FakeBackend()
        val backend = StatsBackend(delegate)
        val layout = VertexLayout().apply { add(ShaderVertType.FLOAT, 2, 0) }
        val program = backend.createProgram("", "", layout)
        val texture = backend.createTexture(BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB), false)
        val target = backend.createRenderTarget(
            32,
            32,
            false,
            RenderTargetFormat.RGBA8,
            SamplerState.LINEAR_CLAMP
        )
        val lastFrame = backend.lastFrame

        backend.beginFrame()
        backend.bindTexture(texture, 0)
        backend.bindTexture(texture, 0)
        backend.updateTexture(texture, 0, 0, BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB))
        backend.beginRenderTarget(target, null)
        val cursor = VertexCursor()
        backend.beginVertices(program, 6, cursor)
        backend.endVertices(program, cursor, 6, ShaderUniforms(), PrimitiveType.TRIANGLES)
        backend.beginVertices(program, 6, cursor)
        backend.endVertices(program, cursor, 6, ShaderUniforms(), PrimitiveType.TRIANGLES)
        backend.endRenderTarget()
        backend.endFrame()

        assertSame(lastFrame, backend.lastFrame)
        assertEquals(1L, lastFrame.frameIndex)
        assertEquals(2L, lastFrame.drawCalls)
        assertEquals(12L, lastFrame.vertices)
        assertEquals(4L, lastFrame.primitives)
        assertEquals(1L, lastFrame.programChanges)
        assertEquals(2L, lastFrame.textureBindCalls)
        assertEquals(1L, lastFrame.textureBindingChanges)
        assertEquals(1L, lastFrame.renderTargetPasses)
        assertEquals(1L, lastFrame.textureUploads)
        assertEquals(32L, lastFrame.uploadedBytes)
        assertEquals(SamplerState.LINEAR_CLAMP, delegate.lastSampler)

        backend.beginFrame()
        backend.endFrame()

        assertSame(lastFrame, backend.lastFrame)
        assertEquals(2L, lastFrame.frameIndex)
        assertEquals(0L, lastFrame.drawCalls)
        assertEquals(2L, backend.lifetime.drawCalls)
        assertEquals(1L, backend.lifetime.programsCreated)
        assertEquals(1L, backend.lifetime.texturesCreated)
        assertEquals(1L, backend.lifetime.renderTargetsCreated)
        assertEquals(2L, backend.lifetime.textureUploads)
        assertEquals(64L, backend.lifetime.uploadedBytes)
    }

    @Test
    fun countsLargeInstancedDraws() {
        val backend = StatsBackend(FakeBackend())
        val layout = VertexLayout().apply {
            add(ShaderVertType.FLOAT, 4, 0)
            markInstanced(6)
        }
        val program = backend.createProgram("", "", layout)

        backend.beginFrame()
        val cursor = VertexCursor()
        backend.beginVertices(program, 40_000, cursor)
        backend.endVertices(program, cursor, 40_000, ShaderUniforms(), PrimitiveType.TRIANGLES)
        backend.endFrame()

        assertEquals(1L, backend.lastFrame.drawCalls)
        assertEquals(40_000L, backend.lastFrame.instances)
        assertEquals(240_000L, backend.lastFrame.vertices)
        assertEquals(80_000L, backend.lastFrame.primitives)
    }

    @Test
    fun rejectsProgramsFromAnotherStatsBackend() {
        val first = StatsBackend(FakeBackend())
        val second = StatsBackend(FakeBackend())
        val layout = VertexLayout().apply { add(ShaderVertType.FLOAT, 2, 0) }
        val program = first.createProgram("", "", layout)

        assertFailsWith<IllegalArgumentException> {
            second.beginVertices(program, 3, VertexCursor())
        }
        assertFailsWith<IllegalArgumentException> {
            second.endVertices(program, VertexCursor(), 3, ShaderUniforms(), PrimitiveType.TRIANGLES)
        }
        assertFailsWith<IllegalArgumentException> {
            second.bindProgram(program)
        }
    }

    @Test
    fun tracksBindingsPerFrameAndGrowsTextureUnits() {
        val backend = StatsBackend(FakeBackend())
        val texture = backend.createTexture(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), false)

        backend.beginFrame()
        backend.bindTexture(texture, 24)
        backend.bindTexture(texture, 24)
        backend.endFrame()

        assertEquals(2L, backend.lastFrame.textureBindCalls)
        assertEquals(1L, backend.lastFrame.textureBindingChanges)

        backend.beginFrame()
        backend.bindTexture(texture, 24)
        backend.endFrame()

        assertEquals(1L, backend.lastFrame.textureBindCalls)
        assertEquals(1L, backend.lastFrame.textureBindingChanges)
    }

    @Test
    fun doesNotPublishFailedFrames() {
        val delegate = FakeBackend()
        val backend = StatsBackend(delegate)

        backend.beginFrame()
        delegate.failEndFrame = true
        assertFailsWith<IllegalStateException> { backend.endFrame() }

        assertEquals(0L, backend.lastFrame.frameIndex)
        assertEquals(0L, backend.lifetime.frames)

        delegate.failEndFrame = false
        backend.beginFrame()
        backend.endFrame()
        assertEquals(1L, backend.lastFrame.frameIndex)
    }

    @Test
    fun countsOnlySuccessfullyCreatedResources() {
        val delegate = FakeBackend().apply { failCreation = true }
        val backend = StatsBackend(delegate)
        val layout = VertexLayout().apply { add(ShaderVertType.FLOAT, 2, 0) }

        assertFailsWith<IllegalStateException> { backend.createProgram("", "", layout) }
        assertFailsWith<IllegalStateException> {
            backend.createTexture(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), false)
        }
        assertFailsWith<IllegalStateException> {
            backend.createRenderTarget(1, 1, false, RenderTargetFormat.RGBA8)
        }

        assertEquals(0L, backend.lifetime.programsCreated)
        assertEquals(0L, backend.lifetime.texturesCreated)
        assertEquals(0L, backend.lifetime.renderTargetsCreated)
    }

    @Test
    fun accountsFormatByteSizeAndForwardsDebugGroups() {
        val delegate = FakeBackend()
        val backend = StatsBackend(delegate)

        backend.beginFrame()
        backend.pushDebugGroup("Fonts")
        val r8Tex = backend.createTexture(BufferedImage(8, 4, BufferedImage.TYPE_INT_ARGB), false, TextureFormat.R8)
        backend.updateTexture(r8Tex, 0, 0, 4, 2, ByteBuffer.allocate(8))
        backend.popDebugGroup()
        backend.endFrame()

        assertEquals(32L + 8L, backend.lastFrame.uploadedBytes)
        assertEquals(2L, backend.lastFrame.textureUploads)
        assertEquals(listOf("push:Fonts", "pop"), delegate.debugEvents)
    }

    @Test
    fun forwardsSamplerMipmapColorMaskStencilAndBlitCalls() {
        val delegate = FakeBackend()
        val backend = StatsBackend(delegate)
        val tex = backend.createTexture(BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB))
        val src = backend.createRenderTarget(8, 8, useDepth = true, useStencil = true)
        val dst = backend.createRenderTarget(8, 8)

        backend.setTextureSampler(tex, SamplerState.NEAREST_REPEAT)
        backend.generateMipmaps(tex)
        backend.colorMask(true, false, true, false)
        backend.blitRenderTarget(src, dst, filter = TextureFilter.NEAREST)

        assertEquals(SamplerState.NEAREST_REPEAT, delegate.lastUpdatedSampler)
        assertEquals(1, delegate.mipmapCalls)
        assertEquals(listOf(true, false, true, false), delegate.lastColorMask)
        assertEquals(1, delegate.blitCalls)
        assertEquals(true, delegate.lastUseStencil)
    }

    @Test
    fun gpuTimersDisabledCreatesNoPoolsAndWritesNoTimestamps() {
        val delegate = FakeBackend().apply { timestampsSupported = true }
        val backend = StatsBackend(delegate, gpuTimers = false)

        backend.beginFrame()
        backend.pushDebugGroup("Pass")
        backend.popDebugGroup()
        backend.endFrame()
        backend.close()

        assertEquals(0, delegate.createdPools.size)
        assertEquals(0, delegate.timestampWrites)
        assertEquals(0L, backend.lastGpuFrameNanos)
        assertEquals(0, backend.gpuZoneCount)
        assertFalse(backend.pollGpuTimers())
    }

    @Test
    fun gpuTimersRingBuffersNonBlockingHarvestNestedZonesAndTeardown() {
        val delegate = FakeBackend().apply { timestampsSupported = true }
        val backend = StatsBackend(delegate, gpuTimers = true)

        // Frame 1: outer zone + nested inner zone (pool 0)
        backend.beginFrame()
        delegate.gpuClockNanos += 100L
        backend.pushDebugGroup("Scene")
        delegate.gpuClockNanos += 200L
        backend.pushDebugGroup("Shadow")
        delegate.gpuClockNanos += 500L
        backend.popDebugGroup()
        delegate.gpuClockNanos += 300L
        backend.popDebugGroup()
        delegate.gpuClockNanos += 50L
        backend.endFrame()

        assertEquals(GpuTimerTracker.RING_SIZE, delegate.createdPools.size)
        // Not ready yet: non-blocking poll must NOT stall and must report 0
        assertFalse(backend.pollGpuTimers())
        assertEquals(0L, backend.lastGpuFrameNanos)
        assertEquals(0, backend.gpuZoneCount)

        // Frame 2 while Frame 1 is still in flight -> uses pool 1 without overwriting pool 0
        backend.beginFrame()
        delegate.gpuClockNanos += 1_000L
        backend.pushDebugGroup("UI")
        delegate.gpuClockNanos += 400L
        backend.popDebugGroup()
        delegate.gpuClockNanos += 100L
        backend.endFrame()

        // Mark only pool 0 ready; pool 1 still in flight
        delegate.createdPools[0].ready = true
        assertTrue(backend.pollGpuTimers())
        assertEquals(1L, backend.lastGpuFrameIndex)
        assertEquals(1_150L, backend.lastGpuFrameNanos)
        assertEquals(1_150L, backend.lastFrame.gpuFrameNanos)
        assertEquals(2, backend.gpuZoneCount)
        assertEquals("Scene", backend.gpuZones[0].name)
        assertEquals(1_000L, backend.gpuZones[0].nanos)
        assertEquals(0, backend.gpuZones[0].depth)
        assertEquals("Shadow", backend.gpuZones[1].name)
        assertEquals(500L, backend.gpuZones[1].nanos)
        assertEquals(1, backend.gpuZones[1].depth)

        // Frame 3 harvests nothing more while pool 1 is still pending, then completes pool 1
        delegate.createdPools[1].ready = true
        backend.beginFrame()
        // Harvesting happened automatically during beginFrame()!
        assertEquals(2L, backend.lastGpuFrameIndex)
        assertEquals(1_500L, backend.lastGpuFrameNanos)
        assertEquals(1, backend.gpuZoneCount)
        assertEquals("UI", backend.gpuZones[0].name)
        assertEquals(400L, backend.gpuZones[0].nanos)
        assertEquals(0, backend.gpuZones[0].depth)
        backend.endFrame()

        // Verify ring-full graceful skip: fill all 4 slots without marking ready
        for (p in delegate.createdPools) p.ready = false
        repeat(GpuTimerTracker.RING_SIZE + 2) {
            backend.beginFrame()
            backend.pushDebugGroup("Overflow")
            backend.popDebugGroup()
            backend.endFrame()
        }
        // Pool count never grew past RING_SIZE and no exception/stall occurred
        assertEquals(GpuTimerTracker.RING_SIZE, delegate.createdPools.size)

        // Teardown closes all pools cleanly before delegate.close()
        backend.close()
        assertTrue(delegate.createdPools.all { it.closed })
        assertTrue(delegate.closed)
    }

    private class FakeTimestampPool(
        override val capacity: Int
    ) : GpuTimestampPool {
        override val timestampPeriodNanos: Double = 1.0
        val written = LongArray(capacity)
        var ready = false
        var closed = false

        override fun tryReadTimestampsNanos(count: Int, outNanos: LongArray): Boolean {
            if (!ready) return false
            for (i in 0 until count) outNanos[i] = written[i]
            return true
        }

        override fun close() {
            closed = true
        }
    }

    private class FakeBackend : RenderBackend {
        var lastSampler: SamplerState? = null
        var lastUpdatedSampler: SamplerState? = null
        var lastUseStencil: Boolean = false
        var lastColorMask: List<Boolean> = emptyList()
        var mipmapCalls = 0
        var blitCalls = 0
        var failCreation = false
        var failEndFrame = false
        var timestampsSupported = false
        var gpuClockNanos = 10_000L
        var timestampWrites = 0
        var closed = false
        val createdPools = ArrayList<FakeTimestampPool>()
        val debugEvents = ArrayList<String>()

        override val supportsGpuTimestamps: Boolean get() = timestampsSupported

        override fun createTimestampPool(capacity: Int): GpuTimestampPool {
            check(timestampsSupported)
            return FakeTimestampPool(capacity).also { createdPools.add(it) }
        }

        override fun writeTimestamp(pool: GpuTimestampPool, queryIndex: Int) {
            timestampWrites++
            (pool as FakeTimestampPool).written[queryIndex] = gpuClockNanos
        }

        override fun beginFrame() {}
        override fun endFrame() {
            check(!failEndFrame)
        }

        override fun createProgram(
            vertexSource: String,
            fragmentSource: String,
            layouts: VertexInputLayout,
            reflection: ShaderReflection?
        ): ProgramHandle {
            check(!failCreation)
            return FakeProgram()
        }

        override fun bindProgram(program: ProgramHandle) {}

        override fun createTexture(
            image: BufferedImage,
            mipmap: Boolean,
            format: TextureFormat,
            sampler: SamplerState
        ): TextureHandle {
            check(!failCreation)
            return FakeTexture(image.width, image.height, format)
        }

        override fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage) {}
        override fun updateTexture(
            texture: TextureHandle,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            data: ByteBuffer
        ) {}
        override fun setTextureSampler(texture: TextureHandle, sampler: SamplerState) {
            lastUpdatedSampler = sampler
        }
        override fun generateMipmaps(texture: TextureHandle) {
            mipmapCalls++
        }
        override fun colorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) {
            lastColorMask = listOf(red, green, blue, alpha)
        }
        override fun pushDebugGroup(label: String) { debugEvents.add("push:$label") }
        override fun popDebugGroup() { debugEvents.add("pop") }
        override fun bindTexture(texture: TextureHandle, unit: Int) {}
        override fun beginVertices(program: ProgramHandle, vertexCount: Int, cursor: VertexCursor) {}
        override fun endVertices(
            program: ProgramHandle,
            cursor: VertexCursor,
            vertexCount: Int,
            uniforms: ShaderUniforms,
            primitiveType: PrimitiveType
        ) {}

        override fun createRenderTarget(
            width: Int,
            height: Int,
            useDepth: Boolean,
            format: RenderTargetFormat,
            sampler: SamplerState,
            useStencil: Boolean
        ): RenderTargetHandle {
            check(!failCreation)
            lastSampler = sampler
            if (useStencil) lastUseStencil = true
            return FakeTarget(width, height)
        }

        override fun blitRenderTarget(
            source: RenderTargetHandle,
            destination: RenderTargetHandle?,
            srcX: Int,
            srcY: Int,
            srcWidth: Int,
            srcHeight: Int,
            dstX: Int,
            dstY: Int,
            dstWidth: Int,
            dstHeight: Int,
            filter: TextureFilter,
            copyColor: Boolean,
            copyDepth: Boolean
        ) {
            blitCalls++
        }

        override fun beginRenderTarget(target: RenderTargetHandle, clearColor: FloatArray?) {}
        override fun endRenderTarget() {}
        override fun close() { closed = true }
        override fun hasContext() = true
    }

    private class FakeProgram : ProgramHandle {
        override fun close() {}
    }

    private class FakeTexture(
        override val width: Int,
        override val height: Int,
        override val format: TextureFormat = TextureFormat.RGBA8
    ) : TextureHandle {
        override fun close() {}
    }

    private class FakeTarget(
        override val width: Int,
        override val height: Int
    ) : RenderTargetHandle {
        override val colorTexture = FakeTexture(width, height)
        override fun close() {}
    }
}
