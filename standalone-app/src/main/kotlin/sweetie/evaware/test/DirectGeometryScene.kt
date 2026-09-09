package sweetie.evaware.test

import java.nio.ByteOrder
import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.BufferUsage
import sweetie.evaware.luma.api.IndexType
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.api.RenderBackend
import sweetie.evaware.luma.api.VertexBufferHandle
import sweetie.evaware.luma.api.IndexBufferHandle
import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.VertexInputLayout
import sweetie.evaware.luma.vertex.VertexLayout
import sweetie.evaware.luma.vertex.ShaderVertType

internal class DirectGeometryScene(private val backend: RenderBackend) : AutoCloseable {
    private val layout = VertexLayout().apply { add(ShaderVertType.FLOAT, 2, 0) }
    private val instanceLayout = VertexLayout().apply { add(ShaderVertType.FLOAT, 2, 1) }
    private val program: ProgramHandle
    private val vertices: VertexBufferHandle = backend.createVertexBuffer(6L * Float.SIZE_BYTES, BufferUsage.STATIC)
    private val instances: VertexBufferHandle = backend.createVertexBuffer(4L * Float.SIZE_BYTES, BufferUsage.STATIC)
    private val indices: IndexBufferHandle = backend.createIndexBuffer(3L * Short.SIZE_BYTES, IndexType.UINT16, BufferUsage.STATIC)
    private val uniforms = ShaderUniforms()
    private var uploaded = false

    init {
        val translated = Luma.shaderTranslator.translate(
            resource("shaders/direct-instanced.vert"),
            resource("shaders/triangle.frag"),
            layout
        )
        program = backend.createProgram(
            translated.vertexSource,
            translated.fragmentSource,
            VertexInputLayout().binding(layout).binding(instanceLayout, 1)
        )
    }

    fun draw() {
        if (!uploaded) {
            upload()
            uploaded = true
        }
        backend.bindVertexBuffer(0, vertices)
        backend.bindVertexBuffer(1, instances)
        backend.bindIndexBuffer(indices)
        backend.drawIndexed(program, uniforms, PrimitiveType.TRIANGLES, 0, 3, instanceCount = 2)
    }

    private fun upload() {
        val vertexData = MemoryUtil.memAlloc(6 * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        val indexData = MemoryUtil.memAlloc(3 * Short.SIZE_BYTES).order(ByteOrder.nativeOrder())
        val instanceData = MemoryUtil.memAlloc(4 * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        try {
            vertexData.asFloatBuffer()
                .put(-0.35f).put(-0.6f)
                .put(0.35f).put(-0.6f)
                .put(0f).put(0.6f)
            vertexData.limit(6 * Float.SIZE_BYTES)
            indexData.asShortBuffer().put(0).put(1).put(2)
            indexData.limit(3 * Short.SIZE_BYTES)
            instanceData.asFloatBuffer().put(-0.5f).put(0f).put(0.5f).put(0f)
            instanceData.limit(4 * Float.SIZE_BYTES)
            backend.updateVertexBuffer(vertices, 0L, vertexData)
            backend.updateVertexBuffer(instances, 0L, instanceData)
            backend.updateIndexBuffer(indices, 0L, indexData)
        } finally {
            MemoryUtil.memFree(instanceData)
            MemoryUtil.memFree(indexData)
            MemoryUtil.memFree(vertexData)
        }
    }

    override fun close() {
        indices.close()
        instances.close()
        vertices.close()
        program.close()
    }

    private fun resource(path: String): String =
        javaClass.classLoader.getResourceAsStream(path)!!.bufferedReader().use { it.readText() }
}
