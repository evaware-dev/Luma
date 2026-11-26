package sweetie.evaware.luma.list

import java.awt.image.BufferedImage
import java.lang.management.ManagementFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.joml.Matrix4f
import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.*
import sweetie.evaware.luma.shader.translator.ShaderReflection
import sweetie.evaware.luma.uniform.*
import sweetie.evaware.luma.vertex.VertexCursor
import sweetie.evaware.luma.vertex.VertexInputLayout

class DrawListTest {
    private var previousBackend: RenderBackend? = null

    @BeforeTest
    fun saveBackend() {
        previousBackend = if (Luma.isBackendInitialized) Luma.backend else null
    }

    @AfterTest
    fun restoreBackend() {
        previousBackend?.let { Luma.backend = it }
    }

    @Test
    fun restoresLumaBackendEvenOnExceptionAndClearsPartialRecording() {
        val backend = RecordingTestBackend()
        Luma.backend = backend

        DrawList().use { list ->
            assertFailsWith<IllegalStateException> {
                list.record {
                    assertFalse(Luma.backend === backend)
                    Luma.enableBlend()
                    throw IllegalStateException("boom")
                }
            }
            assertSame(backend, Luma.backend)
            assertTrue(list.isEmpty)
            assertEquals(0, list.commandCount)

            list.replay()
            assertTrue(backend.events.isEmpty())
        }
    }

    @Test
    fun snapshotsUniformMutationsBetweenDrawsAccurately() {
        val backend = RecordingTestBackend()
        Luma.backend = backend
        val program = FakeProgram("p1")
        val uniforms = ShaderUniforms()
        val uFloat = uniforms.float1("uFloat")
        val uVec2 = uniforms.float2("uVec2")
        val uVec3 = uniforms.float3("uVec3")
        val uColor = uniforms.float4("uColor")
        val uMode = uniforms.int1("uMode")
        val uMat = uniforms.mat4("uMat")

        val verts1 = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f)
        val verts2 = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f)

        DrawList().use { list ->
            list.record {
                uniforms.float1(uFloat, 0.25f)
                uniforms.vec2(uVec2, 1f, 2f)
                uniforms.vec3(uVec3, 3f, 4f, 5f)
                uniforms.vec4(uColor, 1f, 0f, 0f, 1f)
                uniforms.int1(uMode, 7)
                uniforms.mat4(uMat, Matrix4f().translation(10f, 20f, 30f))
                submitVertices(Luma.backend, program, verts1, 3, uniforms, PrimitiveType.TRIANGLES)

                // Mutate the exact same ShaderUniforms before the second draw
                uniforms.float1(uFloat, 0.75f)
                uniforms.vec2(uVec2, 6f, 7f)
                uniforms.vec3(uVec3, 8f, 9f, 10f)
                uniforms.vec4(uColor, 0f, 1f, 0f, 0.5f)
                uniforms.int1(uMode, 42)
                uniforms.mat4(uMat, Matrix4f().scaling(2f, 3f, 4f))
                submitVertices(Luma.backend, program, verts2, 3, uniforms, PrimitiveType.TRIANGLES)
            }

            // Mutate caller's uniforms again after recording — replay must NOT see this!
            uniforms.vec4(uColor, 0f, 0f, 0f, 0f)
            uniforms.int1(uMode, 999)

            list.replay()

            assertEquals(2, backend.immediateDraws.size)
            val first = backend.immediateDraws[0]
            assertContentEquals(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f), first.vertices)
            assertEquals(0.25f, first.uniformSnapshot["uFloat"])
            assertEquals(listOf(1f, 2f), first.uniformSnapshot["uVec2"])
            assertEquals(listOf(3f, 4f, 5f), first.uniformSnapshot["uVec3"])
            assertEquals(listOf(1f, 0f, 0f, 1f), first.uniformSnapshot["uColor"])
            assertEquals(7, first.uniformSnapshot["uMode"])
            assertEquals(Matrix4f().translation(10f, 20f, 30f), first.uniformSnapshot["uMat"])

            val second = backend.immediateDraws[1]
            assertContentEquals(floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f), second.vertices)
            assertEquals(0.75f, second.uniformSnapshot["uFloat"])
            assertEquals(listOf(6f, 7f), second.uniformSnapshot["uVec2"])
            assertEquals(listOf(8f, 9f, 10f), second.uniformSnapshot["uVec3"])
            assertEquals(listOf(0f, 1f, 0f, 0.5f), second.uniformSnapshot["uColor"])
            assertEquals(42, second.uniformSnapshot["uMode"])
            assertEquals(Matrix4f().scaling(2f, 3f, 4f), second.uniformSnapshot["uMat"])
        }
    }

    @Test
    fun recordsAndReplaysDirectIndexedStateTargetsClipMaskAndDebugGroups() {
        val backend = RecordingTestBackend()
        Luma.backend = backend
        val program = FakeProgram("pDirect")
        val vbo = FakeVertexBuffer(256)
        val ibo = FakeIndexBuffer(64)
        val tex = FakeTexture(16, 16)
        val rtOuter = FakeTarget("outer", 64, 64)
        val rtInner = FakeTarget("inner", 32, 32)
        val uniforms = ShaderUniforms()
        val uAlpha = uniforms.float1("uAlpha")
        uniforms.float1(uAlpha, 0.5f)
        val clear = floatArrayOf(0.1f, 0.2f, 0.3f, 1.0f)

        DrawList().use { list ->
            Luma.record(list) {
                assertTrue(Luma.hasContext())
                assertTrue(Luma.backend.supportsStencil)

                Luma.debugGroup("OuterPass") {
                    Luma.backend.beginRenderTarget(rtOuter, clear)
                    clear[0] = 99f // Mutating caller clear array must not corrupt recorded clear color

                    Luma.enableBlend()
                    Luma.blendFunction(BlendFunction.PREMULTIPLIED_ALPHA)
                    Luma.enableCull()
                    Luma.backend.scissor(1, 2, 30, 40)
                    Luma.bindTexture(tex, 1)
                    Luma.setTextureSampler(tex, SamplerState.NEAREST_REPEAT)
                    Luma.generateMipmaps(tex)

                    Luma.backend.beginRenderTarget(rtInner, null)
                    Luma.clearColor(0f, 0f, 1f, 1f)
                    Luma.clearDepth(0.5)
                    Luma.clearStencil(3)

                    Luma.enableStencilTest()
                    Luma.stencilFunc(StencilCompare.EQUAL, 1, 0xFF)
                    Luma.stencilOp(StencilOp.KEEP, StencilOp.ZERO, StencilOp.REPLACE)
                    Luma.stencilMask(0x0F)

                    ClipMask.clip(
                        mode = ClipMode.INSIDE,
                        clear = true,
                        mask = {
                            Luma.backend.bindProgram(program)
                            Luma.backend.bindVertexBuffer(0, vbo, 16L)
                            Luma.backend.draw(program, uniforms, PrimitiveType.TRIANGLES, 3, 6, 2, 1)
                        },
                        content = {
                            Luma.backend.bindIndexBuffer(ibo)
                            Luma.backend.drawIndexed(program, uniforms, PrimitiveType.TRIANGLES, 2, 12, 4, 3, 5)
                        }
                    )

                    Luma.disableStencilTest()
                    Luma.backend.endRenderTarget()

                    Luma.blitRenderTarget(
                        rtInner,
                        rtOuter,
                        srcX = 0, srcY = 0, srcWidth = 32, srcHeight = 32,
                        dstX = 4, dstY = 8, dstWidth = 60, dstHeight = 56,
                        filter = TextureFilter.NEAREST,
                        copyColor = true,
                        copyDepth = true
                    )
                    Luma.backend.disableScissor()
                    Luma.backend.endRenderTarget()
                }
            }

            // Nothing executed during record
            assertTrue(backend.events.isEmpty())

            Luma.replay(list)

            assertEquals(
                listOf(
                    "pushDebug:OuterPass",
                    "beginRT:outer:[0.1, 0.2, 0.3, 1.0]",
                    "blend:true",
                    "blendFunc:${BlendFunction.PREMULTIPLIED_ALPHA}",
                    "cull:true",
                    "scissor:1,2,30,40",
                    "bindTex:1",
                    "sampler:${SamplerState.NEAREST_REPEAT}",
                    "mipmaps",
                    "beginRT:inner:null",
                    "clearColor:0.0,0.0,1.0,1.0",
                    "clearDepth:0.5",
                    "clearStencil:3",
                    "stencilTest:true",
                    "stencilFunc:EQUAL,1,255",
                    "stencilOp:KEEP,ZERO,REPLACE",
                    "stencilMask:15",
                    // ClipMask.clip: write phase
                    "clearDepth:1.0",
                    "colorMask:false,false,false,false",
                    "depthTest:true",
                    "depthWrite:true",
                    "depthCompare:ALWAYS",
                    "bindProgram:pDirect",
                    "bindVB:0,16",
                    "drawDirect:pDirect,TRIANGLES,3,6,2,1,uAlpha=0.5",
                    // ClipMask.clip: inside test phase
                    "colorMask:true,true,true,true",
                    "depthTest:true",
                    "depthWrite:false",
                    "depthCompare:EQUAL",
                    "bindIB",
                    "drawIndexed:pDirect,TRIANGLES,2,12,4,3,5,uAlpha=0.5",
                    // ClipMask.clip: restore initial state
                    "colorMask:true,true,true,true",
                    "depthTest:false",
                    "depthWrite:false",
                    "depthCompare:ALWAYS",
                    "stencilTest:false",
                    "endRT",
                    "blit:inner->outer:0,0,32,32->4,8,60,56:NEAREST:true:true",
                    "disableScissor",
                    "endRT",
                    "popDebug"
                ),
                backend.events
            )
        }
    }

    @Test
    fun reRecordingReusesBuffersAndReplacesPreviousCommands() {
        val backend = RecordingTestBackend()
        Luma.backend = backend
        val program = FakeProgram("pReuse")
        val uniforms = ShaderUniforms()
        val uColor = uniforms.float4("uColor")

        DrawList(initialCommandCapacity = 4, initialVertexFloats = 16).use { list ->
            list.record {
                uniforms.vec4(uColor, 1f, 0f, 0f, 1f)
                submitVertices(Luma.backend, program, floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f), 3, uniforms, PrimitiveType.TRIANGLES)
            }
            list.replay()
            assertEquals(1, backend.immediateDraws.size)
            assertEquals(listOf(1f, 0f, 0f, 1f), backend.immediateDraws[0].uniformSnapshot["uColor"])

            backend.clear()

            // Re-record completely different state & uniform value
            list.record {
                uniforms.vec4(uColor, 0f, 0f, 1f, 1f)
                submitVertices(Luma.backend, program, floatArrayOf(9f, 8f, 7f, 6f, 5f, 4f), 3, uniforms, PrimitiveType.LINES)
            }
            list.replay()
            assertEquals(1, backend.immediateDraws.size)
            assertEquals(PrimitiveType.LINES, backend.immediateDraws[0].primitiveType)
            assertContentEquals(floatArrayOf(9f, 8f, 7f, 6f, 5f, 4f), backend.immediateDraws[0].vertices)
            assertEquals(listOf(0f, 0f, 1f, 1f), backend.immediateDraws[0].uniformSnapshot["uColor"])
        }
    }

    @Test
    fun closedListRejectsOperationsAndCloseIsIdempotent() {
        val backend = RecordingTestBackend()
        Luma.backend = backend
        val list = DrawList()
        list.close()
        list.close() // idempotent
        assertTrue(list.isClosed)
        assertFailsWith<IllegalStateException> { list.record {} }
        assertFailsWith<IllegalStateException> { list.replay() }
        assertFailsWith<IllegalStateException> { list.clear() }
    }

    @Test
    fun rejectsNestedRecordReplayAndLifecycleCallsDuringRecord() {
        val backend = RecordingTestBackend()
        Luma.backend = backend
        DrawList().use { list ->
            list.record {
                assertFailsWith<IllegalStateException> { list.record {} }
                assertFailsWith<IllegalStateException> { list.replay() }
                assertFailsWith<IllegalStateException> { list.clear() }
                assertFailsWith<IllegalStateException> { list.close() }
                assertFailsWith<IllegalStateException> { Luma.backend.beginFrame() }
                assertFailsWith<IllegalStateException> { Luma.backend.endFrame() }
                assertFailsWith<IllegalStateException> { Luma.backend.close() }
            }
            assertSame(backend, Luma.backend)
        }
    }

    @Test
    fun steadyStateReplayAllocatesZeroHeapBytes() {
        val noop = NoopZeroAllocBackend()
        Luma.backend = noop
        val program = FakeProgram("zero")
        val target = FakeTarget("rt", 64, 64)
        val tex = FakeTexture(8, 8)
        val uniforms = ShaderUniforms()
        val uColor = uniforms.float4("uColor")
        val verts = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f)
        val clear = floatArrayOf(0f, 0f, 0f, 1f)

        try {
            DrawList().use { list ->
                list.record {
                    Luma.backend.beginRenderTarget(target, clear)
                    Luma.enableBlend()
                    Luma.bindTexture(tex, 0)
                    for (i in 0 until 100) {
                        uniforms.vec4(uColor, i.toFloat(), 1f, 0f, 1f)
                        submitVertices(Luma.backend, program, verts, 3, uniforms, PrimitiveType.TRIANGLES)
                    }
                    Luma.backend.endRenderTarget()
                }

                // Warm up JIT & replay caches
                repeat(500) { list.replay(noop) }

                val threadBean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
                if (threadBean != null && threadBean.isThreadAllocatedMemorySupported) {
                    val tid = Thread.currentThread().threadId()
                    val before = threadBean.getThreadAllocatedBytes(tid)
                    repeat(1_000) { list.replay(noop) }
                    val after = threadBean.getThreadAllocatedBytes(tid)
                    assertEquals(0L, after - before, "Steady-state DrawList.replay() must allocate 0 bytes")
                }
                assertEquals(150_000, noop.draws)
            }
        } finally {
            noop.close()
        }
    }

    @Test
    fun tailMergesConsecutiveIdenticalImmediateDrawsAndSplitsOnStateChange() {
        val backend = RecordingTestBackend()
        Luma.backend = backend
        val program = FakeProgram("pMerge")
        val uniforms = ShaderUniforms()
        val uColor = uniforms.float4("uColor")
        uniforms.vec4(uColor, 1f, 0f, 0f, 1f)
        val tri1 = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f)
        val tri2 = floatArrayOf(2f, 0f, 3f, 0f, 2f, 1f)
        val tri3 = floatArrayOf(4f, 0f, 5f, 0f, 4f, 1f)

        try {
            DrawList().use { list ->
                list.record {
                    submitVertices(Luma.backend, program, tri1, 3, uniforms, PrimitiveType.TRIANGLES)
                    submitVertices(Luma.backend, program, tri2, 3, uniforms, PrimitiveType.TRIANGLES)
                    Luma.enableBlend()
                    submitVertices(Luma.backend, program, tri3, 3, uniforms, PrimitiveType.TRIANGLES)
                }

                assertEquals(3, list.commandCount) // 1 merged draw + 1 blend + 1 draw
                list.replay()

                assertEquals(2, backend.immediateDraws.size)
                assertEquals(6, backend.immediateDraws[0].vertexCount)
                assertContentEquals(
                    floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 2f, 0f, 3f, 0f, 2f, 1f),
                    backend.immediateDraws[0].vertices
                )
                assertEquals(3, backend.immediateDraws[1].vertexCount)
                assertContentEquals(floatArrayOf(4f, 0f, 5f, 0f, 4f, 1f), backend.immediateDraws[1].vertices)
            }
        } finally {
            backend.close()
        }
    }

    private fun submitVertices(
        backend: RenderBackend,
        program: ProgramHandle,
        floats: FloatArray,
        vertexCount: Int,
        uniforms: ShaderUniforms,
        primitiveType: PrimitiveType
    ) {
        val cursor = VertexCursor()
        backend.beginVertices(program, vertexCount, cursor)
        for (f in floats) cursor.vec1(f)
        val token = cursor.token
        cursor.reset()
        cursor.token = token
        backend.endVertices(program, cursor, vertexCount, uniforms, primitiveType)
    }

    private data class RecordedImmediateDraw(
        val program: ProgramHandle,
        val vertices: FloatArray,
        val vertexCount: Int,
        val primitiveType: PrimitiveType,
        val uniformSnapshot: Map<String, Any>
    )

    private class FakeProgram(
        val name: String,
        override val vertexStrideBytes: Int = 2 * Float.SIZE_BYTES
    ) : ProgramHandle {
        override fun close() {}
    }

    private class FakeTexture(override val width: Int, override val height: Int) : TextureHandle {
        override fun close() {}
    }

    private class FakeTarget(val name: String, override val width: Int, override val height: Int) : RenderTargetHandle {
        override val colorTexture: TextureHandle = FakeTexture(width, height)
        override fun close() {}
    }

    private class FakeVertexBuffer(override val sizeBytes: Long) : VertexBufferHandle {
        override fun close() {}
    }

    private class FakeIndexBuffer(override val sizeBytes: Long) : IndexBufferHandle {
        override val indexType: IndexType = IndexType.UINT16
        override fun close() {}
    }

    private class RecordingTestBackend : RenderBackend {
        private val scratch = MemoryUtil.memAllocFloat(256)
        private val scratchAddr = MemoryUtil.memAddress(scratch)
        val events = ArrayList<String>()
        val immediateDraws = ArrayList<RecordedImmediateDraw>()

        private var depthTestVal = false
        private var depthWriteVal = false
        private var depthCompareVal = DepthCompare.ALWAYS
        private var maskR = true
        private var maskG = true
        private var maskB = true
        private var maskA = true

        override val supportsStencil: Boolean = true
        override val isDepthTestEnabled: Boolean get() = depthTestVal
        override val isDepthWriteEnabled: Boolean get() = depthWriteVal
        override val currentDepthCompare: DepthCompare get() = depthCompareVal
        override val colorMaskRed: Boolean get() = maskR
        override val colorMaskGreen: Boolean get() = maskG
        override val colorMaskBlue: Boolean get() = maskB
        override val colorMaskAlpha: Boolean get() = maskA

        fun clear() {
            events.clear()
            immediateDraws.clear()
        }

        override fun beginFrame() {}
        override fun endFrame() {}
        override fun close() { MemoryUtil.memFree(scratch) }
        override fun hasContext(): Boolean = true

        override fun createProgram(vertexSource: String, fragmentSource: String, layouts: VertexInputLayout, reflection: ShaderReflection?): ProgramHandle =
            FakeProgram("created")
        override fun bindProgram(program: ProgramHandle) {
            events.add("bindProgram:${(program as FakeProgram).name}")
        }
        override fun createTexture(image: BufferedImage, mipmap: Boolean, format: TextureFormat, sampler: SamplerState): TextureHandle =
            FakeTexture(image.width, image.height)
        override fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage) {}
        override fun bindTexture(texture: TextureHandle, unit: Int) {
            events.add("bindTex:$unit")
        }
        override fun setTextureSampler(texture: TextureHandle, sampler: SamplerState) {
            events.add("sampler:$sampler")
        }
        override fun generateMipmaps(texture: TextureHandle) {
            events.add("mipmaps")
        }
        override fun bindVertexBuffer(binding: Int, buffer: VertexBufferHandle, offsetBytes: Long) {
            events.add("bindVB:$binding,$offsetBytes")
        }
        override fun bindIndexBuffer(buffer: IndexBufferHandle) {
            events.add("bindIB")
        }
        override fun createRenderTarget(width: Int, height: Int, useDepth: Boolean, format: RenderTargetFormat, sampler: SamplerState, useStencil: Boolean): RenderTargetHandle =
            FakeTarget("created", width, height)
        override fun beginRenderTarget(target: RenderTargetHandle, clearColor: FloatArray?) {
            events.add("beginRT:${(target as FakeTarget).name}:${clearColor?.contentToString()}")
        }
        override fun endRenderTarget() {
            events.add("endRT")
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
            events.add(
                "blit:${(source as FakeTarget).name}->${(destination as? FakeTarget)?.name}:" +
                    "$srcX,$srcY,$srcWidth,$srcHeight->$dstX,$dstY,$dstWidth,$dstHeight:$filter:$copyColor:$copyDepth"
            )
        }
        override fun blend(enabled: Boolean) { events.add("blend:$enabled") }
        override fun blendFunction(function: BlendFunction) { events.add("blendFunc:$function") }
        override fun colorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) {
            maskR = red; maskG = green; maskB = blue; maskA = alpha
            events.add("colorMask:$red,$green,$blue,$alpha")
        }
        override fun depthTest(enabled: Boolean) {
            depthTestVal = enabled
            events.add("depthTest:$enabled")
        }
        override fun depthWrite(enabled: Boolean) {
            depthWriteVal = enabled
            events.add("depthWrite:$enabled")
        }
        override fun depthCompare(compare: DepthCompare) {
            depthCompareVal = compare
            events.add("depthCompare:$compare")
        }
        override fun stencilTest(enabled: Boolean) { events.add("stencilTest:$enabled") }
        override fun stencilFunc(compare: StencilCompare, ref: Int, readMask: Int) {
            events.add("stencilFunc:$compare,$ref,$readMask")
        }
        override fun stencilOp(stencilFail: StencilOp, depthFail: StencilOp, pass: StencilOp) {
            events.add("stencilOp:$stencilFail,$depthFail,$pass")
        }
        override fun stencilMask(writeMask: Int) { events.add("stencilMask:$writeMask") }
        override fun cull(enabled: Boolean) { events.add("cull:$enabled") }
        override fun scissor(x: Int, y: Int, width: Int, height: Int) { events.add("scissor:$x,$y,$width,$height") }
        override fun disableScissor() { events.add("disableScissor") }
        override fun clearColor(red: Float, green: Float, blue: Float, alpha: Float) {
            events.add("clearColor:$red,$green,$blue,$alpha")
        }
        override fun clearDepth(depth: Double) { events.add("clearDepth:$depth") }
        override fun clearStencil(value: Int) { events.add("clearStencil:$value") }
        override fun pushDebugGroup(label: String) { events.add("pushDebug:$label") }
        override fun popDebugGroup() { events.add("popDebug") }

        override fun beginVertices(program: ProgramHandle, vertexCount: Int, cursor: VertexCursor) {
            val stride = program.vertexStrideBytes
            cursor.init(scratchAddr, vertexCount.toLong() * stride, stride)
        }

        override fun endVertices(
            program: ProgramHandle,
            cursor: VertexCursor,
            vertexCount: Int,
            uniforms: ShaderUniforms,
            primitiveType: PrimitiveType
        ) {
            if (vertexCount <= 0) return
            val floatCount = vertexCount * (program.vertexStrideBytes / Float.SIZE_BYTES)
            val copy = FloatArray(floatCount) { scratch.get(it) }
            immediateDraws.add(RecordedImmediateDraw(program, copy, vertexCount, primitiveType, snapshotMap(uniforms)))
        }

        override fun draw(
            program: ProgramHandle,
            uniforms: ShaderUniforms,
            primitiveType: PrimitiveType,
            firstVertex: Int,
            vertexCount: Int,
            instanceCount: Int,
            firstInstance: Int
        ) {
            val uAlpha = (uniforms.registry.entries.firstOrNull { it.name == "uAlpha" }?.handle as? Float1Uniform)?.value
            events.add(
                "drawDirect:${(program as FakeProgram).name},$primitiveType,$firstVertex,$vertexCount,$instanceCount,$firstInstance,uAlpha=$uAlpha"
            )
        }

        override fun drawIndexed(
            program: ProgramHandle,
            uniforms: ShaderUniforms,
            primitiveType: PrimitiveType,
            firstIndex: Int,
            indexCount: Int,
            vertexOffset: Int,
            instanceCount: Int,
            firstInstance: Int
        ) {
            val uAlpha = (uniforms.registry.entries.firstOrNull { it.name == "uAlpha" }?.handle as? Float1Uniform)?.value
            events.add(
                "drawIndexed:${(program as FakeProgram).name},$primitiveType,$firstIndex,$indexCount,$vertexOffset,$instanceCount,$firstInstance,uAlpha=$uAlpha"
            )
        }

        private fun snapshotMap(uniforms: ShaderUniforms): Map<String, Any> {
            val out = LinkedHashMap<String, Any>()
            for (entry in uniforms.registry.entries) {
                out[entry.name] = when (val h = entry.handle) {
                    is Float1Uniform -> h.value
                    is Float2Uniform -> listOf(h.first, h.second)
                    is Float3Uniform -> listOf(h.first, h.second, h.third)
                    is Float4Uniform -> listOf(h.first, h.second, h.third, h.fourth)
                    is Int1Uniform -> h.value
                    is Mat4Uniform -> Matrix4f(h.value)
                }
            }
            return out
        }
    }

    private class NoopZeroAllocBackend : RenderBackend {
        private val scratch = MemoryUtil.memAllocFloat(256)
        private val scratchAddr = MemoryUtil.memAddress(scratch)
        var draws = 0
        override fun beginFrame() {}
        override fun endFrame() {}
        override fun close() { MemoryUtil.memFree(scratch) }
        override fun hasContext(): Boolean = true
        override fun createProgram(vertexSource: String, fragmentSource: String, layouts: VertexInputLayout, reflection: ShaderReflection?): ProgramHandle =
            FakeProgram("noop")
        override fun bindProgram(program: ProgramHandle) {}
        override fun createTexture(image: BufferedImage, mipmap: Boolean, format: TextureFormat, sampler: SamplerState): TextureHandle =
            FakeTexture(1, 1)
        override fun updateTexture(texture: TextureHandle, x: Int, y: Int, image: BufferedImage) {}
        override fun bindTexture(texture: TextureHandle, unit: Int) {}
        override fun createRenderTarget(width: Int, height: Int, useDepth: Boolean, format: RenderTargetFormat, sampler: SamplerState, useStencil: Boolean): RenderTargetHandle =
            FakeTarget("noop", width, height)
        override fun beginRenderTarget(target: RenderTargetHandle, clearColor: FloatArray?) {}
        override fun endRenderTarget() {}
        override fun beginVertices(program: ProgramHandle, vertexCount: Int, cursor: VertexCursor) {
            val stride = program.vertexStrideBytes
            cursor.init(scratchAddr, vertexCount.toLong() * stride, stride)
        }
        override fun endVertices(
            program: ProgramHandle,
            cursor: VertexCursor,
            vertexCount: Int,
            uniforms: ShaderUniforms,
            primitiveType: PrimitiveType
        ) {
            if (vertexCount > 0) draws++
        }
    }
}
