package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.buffers.Std140Builder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import net.minecraft.resources.Identifier
import org.joml.Matrix4f
import org.junit.Assert.assertEquals
import org.junit.Test
import sweetie.evaware.luma.vertex.VertexLayout

class Std140WriterTest {
    @Test
    fun `writer follows scalar vector and matrix alignment`() {
        val buffer = ByteBuffer.allocateDirect(128).order(ByteOrder.nativeOrder())
        val writer = Std140Writer()
        writer.begin(buffer)

        writer.putFloat(1f)
        writer.putVec2(2f, 3f)
        writer.putVec3(4f, 5f, 6f)
        writer.putFloat(7f)
        writer.putMat4(Matrix4f())

        assertEquals(112, writer.size())
        assertEquals(1f, buffer.getFloat(0))
        assertEquals(2f, buffer.getFloat(8))
        assertEquals(3f, buffer.getFloat(12))
        assertEquals(4f, buffer.getFloat(16))
        assertEquals(5f, buffer.getFloat(20))
        assertEquals(6f, buffer.getFloat(24))
        assertEquals(7f, buffer.getFloat(32))
        assertEquals(1f, buffer.getFloat(48))
        assertEquals(1f, buffer.getFloat(68))
        assertEquals(1f, buffer.getFloat(88))
        assertEquals(1f, buffer.getFloat(108))
    }

    @Test
    fun `writer matches Blaze3D std140 layout`() {
        val expectedBuffer = ByteBuffer.allocateDirect(128).order(ByteOrder.nativeOrder())
        val actualBuffer = ByteBuffer.allocateDirect(128).order(ByteOrder.nativeOrder())
        val matrix = Matrix4f().translation(1f, 2f, 3f)

        val expected = Std140Builder.intoBuffer(expectedBuffer)
            .putFloat(1f)
            .putVec2(2f, 3f)
            .putVec3(4f, 5f, 6f)
            .putFloat(7f)
            .putMat4f(matrix)
            .get()

        val writer = Std140Writer()
        writer.begin(actualBuffer)
        writer.putFloat(1f)
        writer.putVec2(2f, 3f)
        writer.putVec3(4f, 5f, 6f)
        writer.putFloat(7f)
        writer.putMat4(matrix)

        assertEquals(expected.remaining(), writer.size())
        for (index in 0 until expected.remaining()) {
            assertEquals(expected.get(index), actualBuffer.get(index))
        }
    }

    @Test
    fun `uniform metadata matches writer size`() {
        val program = Program(
            Identifier.fromNamespaceAndPath("test", "uniform-layout"),
            """
                layout(std140) uniform LumaUniforms {
                    float first;
                    vec2 second;
                    vec3 third;
                    float fourth;
                    mat4 matrix;
                };
            """.trimIndent(),
            "",
            VertexLayout()
        )

        assertEquals(112, program.uniformBlockBytes)
    }
}
