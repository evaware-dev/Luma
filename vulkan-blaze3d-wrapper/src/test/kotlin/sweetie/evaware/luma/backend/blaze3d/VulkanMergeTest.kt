package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.platform.CompareOp
import net.minecraft.resources.Identifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sun.misc.Unsafe
import sweetie.evaware.luma.api.BlendFunction
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.vertex.VertexLayout

class VulkanMergeTest {
    private val unsafe: Unsafe by lazy {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        field.get(null) as Unsafe
    }

    private val shaderSource = "uniform float uValue;\nuniform sampler2D Sampler0;"
    private val layout = VertexLayout()

    private val program1 = Program(
        Identifier.fromNamespaceAndPath("test", "shader1"),
        shaderSource,
        shaderSource,
        layout
    )

    private val program2 = Program(
        Identifier.fromNamespaceAndPath("test", "shader2"),
        shaderSource,
        shaderSource,
        layout
    )

    private val texture1 = object : TextureHandle {
        override val width: Int = 16
        override val height: Int = 16
        override fun close() {}
    }

    private val texture2 = object : TextureHandle {
        override val width: Int = 16
        override val height: Int = 16
        override fun close() {}
    }

    private fun createMockRenderTarget(): VulkanRenderTarget {
        return unsafe.allocateInstance(VulkanRenderTarget::class.java) as VulkanRenderTarget
    }

    private fun DrawCallRecorder.record(
        program: Program,
        vertexOffset: Long,
        vertexBytes: Long,
        vertexCount: Int,
        uboOffset: Long,
        uboBytes: Long,
        texture: TextureHandle?,
        primitiveType: PrimitiveType,
        blendEnabled: Boolean = true,
        blendFunction: BlendFunction = BlendFunction.TRANSLUCENT,
        depthEnabled: Boolean = false,
        depthWrite: Boolean = false,
        depthFunc: CompareOp = CompareOp.ALWAYS_PASS,
        cullEnabled: Boolean = false,
        target: VulkanRenderTarget? = null,
        targetPassId: Int = 0,
        clearColor: FloatArray? = null
    ) {
        val draw = obtain()
        draw.program = program
        draw.vertexOffset = vertexOffset
        draw.vertexBytes = vertexBytes
        draw.vertexCount = vertexCount
        draw.uboOffset = uboOffset
        draw.uboBytes = uboBytes
        draw.textures = if (texture == null) {
            DrawCall.NO_TEXTURES
        } else {
            arrayOfNulls<TextureHandle>(DrawCall.TEXTURE_UNITS).also { it[0] = texture }
        }
        draw.primitiveType = primitiveType
        draw.blendEnabled = blendEnabled
        draw.blendFunction = blendFunction
        draw.depthEnabled = depthEnabled
        draw.depthWrite = depthWrite
        draw.depthFunc = depthFunc
        draw.cullEnabled = cullEnabled
        draw.target = target
        draw.targetPassId = targetPassId
        draw.clearColor = clearColor
    }

    private fun merge(consumer: GroupConsumer, record: DrawCallRecorder.() -> Unit) {
        val recorder = DrawCallRecorder()
        recorder.record()
        DrawCallMerger().run(recorder, consumer)
    }

    private class TestGroupConsumer : GroupConsumer {
        val targetChanges = ArrayList<Pair<VulkanRenderTarget?, FloatArray?>>()
        val pipelineChanges = ArrayList<PipelineParams>()
        val uboChanges = ArrayList<Pair<Long, Long>>()
        val textureChanges = ArrayList<Pair<Program, TextureHandle?>>()
        val draws = ArrayList<DrawParams>()

        class PipelineParams(
            val program: Program,
            val topology: PrimitiveTopology,
            val blendEnabled: Boolean,
            val blendFunction: BlendFunction,
            val depthEnabled: Boolean,
            val depthWrite: Boolean,
            val depthFunc: CompareOp,
            val cullEnabled: Boolean
        )

        class DrawParams(
            val topology: PrimitiveTopology,
            val vertexStart: Long,
            val vertexBytes: Long,
            val vertexCount: Int
        )

        override fun onTargetChanged(target: VulkanRenderTarget?, clearColor: FloatArray?) {
            targetChanges.add(target to clearColor)
        }

        override fun onClear(
            target: VulkanRenderTarget?,
            color: Boolean,
            red: Float,
            green: Float,
            blue: Float,
            alpha: Float,
            depth: Boolean,
            depthValue: Double
        ) {}

        override fun onPipelineChanged(
            program: Program,
            topology: PrimitiveTopology,
            blendEnabled: Boolean,
            blendFunction: BlendFunction,
            depthEnabled: Boolean,
            depthWrite: Boolean,
            depthFunc: CompareOp,
            cullEnabled: Boolean
        ) {
            pipelineChanges.add(
                PipelineParams(program, topology, blendEnabled, blendFunction, depthEnabled, depthWrite, depthFunc, cullEnabled)
            )
        }

        override fun onUboChanged(offset: Long, size: Long) {
            uboChanges.add(offset to size)
        }

        override fun onTextureChanged(program: Program, textures: Array<TextureHandle?>) {
            textureChanges.add(program to textures[0])
        }

        override fun onScissorChanged(enabled: Boolean, x: Int, y: Int, width: Int, height: Int) {}

        override fun onDraw(topology: PrimitiveTopology, vertexStart: Long, vertexBytes: Long, vertexCount: Int) {
            draws.add(DrawParams(topology, vertexStart, vertexBytes, vertexCount))
        }

        override fun onDirectDraw(draw: DrawCall, topology: PrimitiveTopology) {
            draws.add(DrawParams(topology, draw.firstVertex.toLong(), 0L, draw.vertexCount))
        }
    }

    @Test
    fun testEmptyFrame() {
        val consumer = TestGroupConsumer()
        merge(consumer) {}

        assertTrue(consumer.targetChanges.isEmpty())
        assertTrue(consumer.pipelineChanges.isEmpty())
        assertTrue(consumer.draws.isEmpty())
    }

    @Test
    fun testSingleDraw() {
        val consumer = TestGroupConsumer()
        merge(consumer) {
            record(program1, 0L, 64L, 4, 0L, 32L, texture1, PrimitiveType.QUADS)
        }

        assertEquals(1, consumer.targetChanges.size)
        assertEquals(1, consumer.pipelineChanges.size)
        assertEquals(1, consumer.uboChanges.size)
        assertEquals(1, consumer.textureChanges.size)
        assertEquals(1, consumer.draws.size)

        val draw = consumer.draws[0]
        assertEquals(PrimitiveTopology.QUADS, draw.topology)
        assertEquals(0L, draw.vertexStart)
        assertEquals(64L, draw.vertexBytes)
        assertEquals(4, draw.vertexCount)
    }

    @Test
    fun testBasicMerge() {
        val consumer = TestGroupConsumer()
        merge(consumer) {
            record(program1, 0L, 64L, 4, 0L, 32L, texture1, PrimitiveType.QUADS)
            record(program1, 64L, 64L, 4, 0L, 32L, texture1, PrimitiveType.QUADS)
            record(program1, 128L, 64L, 4, 0L, 32L, texture1, PrimitiveType.QUADS)
        }

        assertEquals(1, consumer.targetChanges.size)
        assertEquals(1, consumer.pipelineChanges.size)
        assertEquals(1, consumer.uboChanges.size)
        assertEquals(1, consumer.textureChanges.size)
        assertEquals(1, consumer.draws.size)

        val draw = consumer.draws[0]
        assertEquals(0L, draw.vertexStart)
        assertEquals(192L, draw.vertexBytes)
        assertEquals(12, draw.vertexCount)
    }

    @Test
    fun testSplitByState() {
        val consumer = TestGroupConsumer()
        merge(consumer) {
            record(program1, 0L, 64L, 4, 0L, 32L, texture1, PrimitiveType.QUADS, depthEnabled = false)
            record(program1, 64L, 64L, 4, 0L, 32L, texture1, PrimitiveType.QUADS, depthEnabled = true)
        }

        assertEquals(2, consumer.pipelineChanges.size)
        assertEquals(2, consumer.draws.size)
        assertEquals(4, consumer.draws[0].vertexCount)
        assertEquals(4, consumer.draws[1].vertexCount)
    }

    @Test
    fun testSplitByBlendState() {
        val consumer = TestGroupConsumer()
        merge(consumer) {
            record(program1, 0L, 64L, 4, 0L, 32L, texture1, PrimitiveType.QUADS, blendEnabled = true)
            record(program1, 64L, 64L, 4, 0L, 32L, texture1, PrimitiveType.QUADS, blendEnabled = false)
        }

        assertEquals(2, consumer.pipelineChanges.size)
        assertEquals(true, consumer.pipelineChanges[0].blendEnabled)
        assertEquals(false, consumer.pipelineChanges[1].blendEnabled)
    }

    @Test
    fun testSplitByBlendFunction() {
        val consumer = TestGroupConsumer()
        merge(consumer) {
            record(program1, 0L, 64L, 4, 0L, 32L, texture1, PrimitiveType.QUADS,
                blendFunction = BlendFunction.TRANSLUCENT)
            record(program1, 64L, 64L, 4, 0L, 32L, texture1, PrimitiveType.QUADS,
                blendFunction = BlendFunction.PREMULTIPLIED_ALPHA)
        }

        assertEquals(2, consumer.pipelineChanges.size)
        assertEquals(BlendFunction.TRANSLUCENT, consumer.pipelineChanges[0].blendFunction)
        assertEquals(BlendFunction.PREMULTIPLIED_ALPHA, consumer.pipelineChanges[1].blendFunction)
        assertEquals(2, consumer.draws.size)
    }

    @Test
    fun testSplitByProgram() {
        val consumer = TestGroupConsumer()
        merge(consumer) {
            record(program1, 0L, 64L, 4, 0L, 32L, texture1, PrimitiveType.QUADS)
            record(program2, 64L, 64L, 4, 0L, 32L, texture1, PrimitiveType.QUADS)
        }

        assertEquals(2, consumer.pipelineChanges.size)
        assertEquals(2, consumer.textureChanges.size)
        assertEquals(2, consumer.draws.size)
    }

    @Test
    fun testSplitByTexture() {
        val consumer = TestGroupConsumer()
        merge(consumer) {
            record(program1, 0L, 64L, 4, 0L, 32L, texture1, PrimitiveType.QUADS)
            record(program1, 64L, 64L, 4, 0L, 32L, texture2, PrimitiveType.QUADS)
        }

        assertEquals(2, consumer.textureChanges.size)
        assertEquals(2, consumer.draws.size)
    }

    @Test
    fun testSplitByTopology() {
        val consumer = TestGroupConsumer()
        merge(consumer) {
            record(program1, 0L, 64L, 4, 0L, 32L, texture1, PrimitiveType.QUADS)
            record(program1, 64L, 48L, 3, 0L, 32L, texture1, PrimitiveType.TRIANGLES)
        }

        assertEquals(2, consumer.pipelineChanges.size)
        assertEquals(2, consumer.draws.size)
    }

    @Test
    fun testSplitByNonContiguousVertices() {
        val consumer = TestGroupConsumer()
        merge(consumer) {
            record(program1, 0L, 64L, 4, 0L, 32L, texture1, PrimitiveType.QUADS)
            record(program1, 128L, 64L, 4, 0L, 32L, texture1, PrimitiveType.QUADS)
        }

        assertEquals(2, consumer.draws.size)
        assertEquals(0L, consumer.draws[0].vertexStart)
        assertEquals(128L, consumer.draws[1].vertexStart)
    }

    @Test
    fun testTargetChanges() {
        val target1 = createMockRenderTarget()
        val target2 = createMockRenderTarget()

        val consumer = TestGroupConsumer()
        merge(consumer) {
            record(program1, 0L, 64L, 4, 0L, 32L, texture1, PrimitiveType.QUADS, target = target1)
            record(program1, 64L, 64L, 4, 0L, 32L, texture1, PrimitiveType.QUADS, target = target1)
            record(program1, 128L, 64L, 4, 0L, 32L, texture1, PrimitiveType.QUADS, target = target2)
        }

        assertEquals(2, consumer.targetChanges.size)
        assertEquals(2, consumer.draws.size)
        assertEquals(8, consumer.draws[0].vertexCount)
        assertEquals(4, consumer.draws[1].vertexCount)
    }

    @Test
    fun testClearColorDoesNotSplitCurrentTargetPass() {
        val target = createMockRenderTarget()
        val clearColor = floatArrayOf(0f, 0f, 0f, 0f)
        val consumer = TestGroupConsumer()

        merge(consumer) {
            record(
                program1,
                0L,
                64L,
                4,
                0L,
                32L,
                texture1,
                PrimitiveType.QUADS,
                target = target,
                clearColor = clearColor
            )
            record(
                program1,
                64L,
                64L,
                4,
                0L,
                32L,
                texture1,
                PrimitiveType.QUADS,
                target = target
            )
        }

        assertEquals(1, consumer.targetChanges.size)
        assertTrue(consumer.targetChanges.single().second contentEquals clearColor)
        assertEquals(1, consumer.draws.size)
        assertEquals(8, consumer.draws.single().vertexCount)
    }

    @Test
    fun testNewScopeSplitsSameTargetAndPreservesClear() {
        val target = createMockRenderTarget()
        val clearColor = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        val consumer = TestGroupConsumer()

        merge(consumer) {
            record(program1, 0L, 64L, 4, 0L, 32L, texture1, PrimitiveType.QUADS,
                target = target, targetPassId = 1)
            record(program1, 64L, 64L, 4, 0L, 32L, texture1, PrimitiveType.QUADS,
                target = target, targetPassId = 2, clearColor = clearColor)
        }

        assertEquals(2, consumer.targetChanges.size)
        assertEquals(null, consumer.targetChanges[0].second)
        assertTrue(consumer.targetChanges[1].second contentEquals clearColor)
        assertEquals(2, consumer.draws.size)
    }
}
