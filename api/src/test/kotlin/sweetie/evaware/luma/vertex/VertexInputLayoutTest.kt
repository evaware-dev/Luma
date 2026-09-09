package sweetie.evaware.luma.vertex

import kotlin.test.Test
import kotlin.test.assertEquals

class VertexInputLayoutTest {
    @Test
    fun `keeps independent vertex and instance bindings`() {
        val vertices = VertexLayout().apply {
            add(ShaderVertType.FLOAT, 3, 0)
            add(ShaderVertType.FLOAT, 2, 1)
        }
        val instances = VertexLayout().apply {
            add(ShaderVertType.FLOAT, 4, 2)
        }

        val inputs = VertexInputLayout()
            .binding(vertices)
            .binding(instances, stepRate = 1)

        assertEquals(2, inputs.size())
        assertEquals(0, inputs.stepRate(0))
        assertEquals(1, inputs.stepRate(1))
        assertEquals(5, inputs.layout(0).strideFloats)
        assertEquals(20, inputs.layout(0).strideBytes)
        assertEquals(4, inputs.layout(1).strideFloats)
        assertEquals(16, inputs.layout(1).strideBytes)
    }

    @Test
    fun `computes packed byte stride independently from component count`() {
        val layout = VertexLayout().apply {
            add(ShaderVertType.FLOAT32, 3, 0)
            add(ShaderVertType.UINT8, 4, 1, normalized = true)
            add(ShaderVertType.FLOAT16, 2, 2)
        }

        assertEquals(9, layout.strideFloats)
        assertEquals(20, layout.strideBytes)
        assertEquals(12, layout.byteSize(0))
        assertEquals(4, layout.byteSize(1))
        assertEquals(4, layout.byteSize(2))
    }
}
