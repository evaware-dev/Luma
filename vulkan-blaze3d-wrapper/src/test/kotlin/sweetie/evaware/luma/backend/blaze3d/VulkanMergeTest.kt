package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.platform.CompareOp
import net.minecraft.resources.Identifier
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.vertex.VertexLayout
import sun.misc.Unsafe
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

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

    private class TestGroupConsumer : GroupConsumer {
        val targetChanges = ArrayList<Pair<VulkanRenderTarget?, FloatArray?>>()
        val pipelineChanges = ArrayList<PipelineParams>()
        val uboChanges = ArrayList<Pair<Long, Long>>()
        val textureChanges = ArrayList<Pair<Program, TextureHandle?>>()
        val draws = ArrayList<DrawParams>()

        class PipelineParams(
            val program: Program,
            val topology: PrimitiveTopology,
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

        override fun onPipelineChanged(
            program: Program,
            topology: PrimitiveTopology,
            depthEnabled: Boolean,
            depthWrite: Boolean,
            depthFunc: CompareOp,
            cullEnabled: Boolean
        ) {
            pipelineChanges.add(
                PipelineParams(program, topology, depthEnabled, depthWrite, depthFunc, cullEnabled)
            )
        }

        override fun onUboChanged(offset: Long, size: Long) {
            uboChanges.add(offset to size)
        }

        override fun onTextureChanged(program: Program, texture: TextureHandle?) {
            textureChanges.add(program to texture)
        }

        override fun onDraw(topology: PrimitiveTopology, vertexStart: Long, vertexBytes: Long, vertexCount: Int) {
            draws.add(DrawParams(topology, vertexStart, vertexBytes, vertexCount))
        }
    }

    @Test
    fun testEmptyFrame() {
        val backend = unsafe.allocateInstance(Backend::class.java) as Backend
        val consumer = TestGroupConsumer()
        backend.runMergeLoop(consumer)

        assertTrue(consumer.targetChanges.isEmpty())
        assertTrue(consumer.pipelineChanges.isEmpty())
        assertTrue(consumer.draws.isEmpty())
    }

    @Test
    fun testSingleDraw() {
        val backend = Backend()
        backend.beginFrame()

        backend.addDrawCallForTest(
            program = program1,
            vertexOffset = 0L,
            vertexBytes = 64L,
            vertexCount = 4,
            uboOffset = 0L,
            uboBytes = 32L,
            texture = texture1,
            primitiveType = 2
        )

        val consumer = TestGroupConsumer()
        backend.runMergeLoop(consumer)

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
        val backend = Backend()
        backend.beginFrame()

        backend.addDrawCallForTest(
            program = program1,
            vertexOffset = 0L,
            vertexBytes = 64L,
            vertexCount = 4,
            uboOffset = 0L,
            uboBytes = 32L,
            texture = texture1,
            primitiveType = 2
        )

        backend.addDrawCallForTest(
            program = program1,
            vertexOffset = 64L,
            vertexBytes = 64L,
            vertexCount = 4,
            uboOffset = 0L,
            uboBytes = 32L,
            texture = texture1,
            primitiveType = 2
        )

        backend.addDrawCallForTest(
            program = program1,
            vertexOffset = 128L,
            vertexBytes = 64L,
            vertexCount = 4,
            uboOffset = 0L,
            uboBytes = 32L,
            texture = texture1,
            primitiveType = 2
        )

        val consumer = TestGroupConsumer()
        backend.runMergeLoop(consumer)

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
        val backend = Backend()
        backend.beginFrame()

        backend.addDrawCallForTest(
            program = program1,
            vertexOffset = 0L,
            vertexBytes = 64L,
            vertexCount = 4,
            uboOffset = 0L,
            uboBytes = 32L,
            texture = texture1,
            primitiveType = 2,
            depthEnabled = false
        )

        backend.addDrawCallForTest(
            program = program1,
            vertexOffset = 64L,
            vertexBytes = 64L,
            vertexCount = 4,
            uboOffset = 0L,
            uboBytes = 32L,
            texture = texture1,
            primitiveType = 2,
            depthEnabled = true
        )

        val consumer = TestGroupConsumer()
        backend.runMergeLoop(consumer)

        assertEquals(2, consumer.pipelineChanges.size)
        assertEquals(2, consumer.draws.size)
        assertEquals(4, consumer.draws[0].vertexCount)
        assertEquals(4, consumer.draws[1].vertexCount)
    }

    @Test
    fun testSplitByProgram() {
        val backend = Backend()
        backend.beginFrame()

        backend.addDrawCallForTest(
            program = program1,
            vertexOffset = 0L,
            vertexBytes = 64L,
            vertexCount = 4,
            uboOffset = 0L,
            uboBytes = 32L,
            texture = texture1,
            primitiveType = 2
        )

        backend.addDrawCallForTest(
            program = program2,
            vertexOffset = 64L,
            vertexBytes = 64L,
            vertexCount = 4,
            uboOffset = 0L,
            uboBytes = 32L,
            texture = texture1,
            primitiveType = 2
        )

        val consumer = TestGroupConsumer()
        backend.runMergeLoop(consumer)

        assertEquals(2, consumer.pipelineChanges.size)
        assertEquals(2, consumer.draws.size)
    }

    @Test
    fun testSplitByTexture() {
        val backend = Backend()
        backend.beginFrame()

        backend.addDrawCallForTest(
            program = program1,
            vertexOffset = 0L,
            vertexBytes = 64L,
            vertexCount = 4,
            uboOffset = 0L,
            uboBytes = 32L,
            texture = texture1,
            primitiveType = 2
        )

        backend.addDrawCallForTest(
            program = program1,
            vertexOffset = 64L,
            vertexBytes = 64L,
            vertexCount = 4,
            uboOffset = 0L,
            uboBytes = 32L,
            texture = texture2,
            primitiveType = 2
        )

        val consumer = TestGroupConsumer()
        backend.runMergeLoop(consumer)

        assertEquals(2, consumer.textureChanges.size)
        assertEquals(2, consumer.draws.size)
    }

    @Test
    fun testSplitByTopology() {
        val backend = Backend()
        backend.beginFrame()

        backend.addDrawCallForTest(
            program = program1,
            vertexOffset = 0L,
            vertexBytes = 64L,
            vertexCount = 4,
            uboOffset = 0L,
            uboBytes = 32L,
            texture = texture1,
            primitiveType = 2
        )

        backend.addDrawCallForTest(
            program = program1,
            vertexOffset = 64L,
            vertexBytes = 48L,
            vertexCount = 3,
            uboOffset = 0L,
            uboBytes = 32L,
            texture = texture1,
            primitiveType = 0
        )

        val consumer = TestGroupConsumer()
        backend.runMergeLoop(consumer)

        assertEquals(2, consumer.pipelineChanges.size)
        assertEquals(2, consumer.draws.size)
    }

    @Test
    fun testSplitByNonContiguousVertices() {
        val backend = Backend()
        backend.beginFrame()

        backend.addDrawCallForTest(
            program = program1,
            vertexOffset = 0L,
            vertexBytes = 64L,
            vertexCount = 4,
            uboOffset = 0L,
            uboBytes = 32L,
            texture = texture1,
            primitiveType = 2
        )

        backend.addDrawCallForTest(
            program = program1,
            vertexOffset = 128L,
            vertexBytes = 64L,
            vertexCount = 4,
            uboOffset = 0L,
            uboBytes = 32L,
            texture = texture1,
            primitiveType = 2
        )

        val consumer = TestGroupConsumer()
        backend.runMergeLoop(consumer)

        assertEquals(2, consumer.draws.size)
        assertEquals(0L, consumer.draws[0].vertexStart)
        assertEquals(128L, consumer.draws[1].vertexStart)
    }

    @Test
    fun testTargetChanges() {
        val backend = Backend()
        backend.beginFrame()

        val target1 = createMockRenderTarget()
        val target2 = createMockRenderTarget()

        backend.addDrawCallForTest(
            program = program1,
            vertexOffset = 0L,
            vertexBytes = 64L,
            vertexCount = 4,
            uboOffset = 0L,
            uboBytes = 32L,
            texture = texture1,
            primitiveType = 2,
            target = target1
        )

        backend.addDrawCallForTest(
            program = program1,
            vertexOffset = 64L,
            vertexBytes = 64L,
            vertexCount = 4,
            uboOffset = 0L,
            uboBytes = 32L,
            texture = texture1,
            primitiveType = 2,
            target = target1
        )

        backend.addDrawCallForTest(
            program = program1,
            vertexOffset = 128L,
            vertexBytes = 64L,
            vertexCount = 4,
            uboOffset = 0L,
            uboBytes = 32L,
            texture = texture1,
            primitiveType = 2,
            target = target2
        )

        val consumer = TestGroupConsumer()
        backend.runMergeLoop(consumer)

        assertEquals(2, consumer.targetChanges.size)
        assertEquals(2, consumer.draws.size)
        assertEquals(8, consumer.draws[0].vertexCount)
        assertEquals(4, consumer.draws[1].vertexCount)
    }
}
