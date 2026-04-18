package sweetie.evaware.luma.wrapper.vertex

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.opengl.OpenGlMappings
import sweetie.evaware.luma.wrapper.LumaVertexBuffer
import sweetie.evaware.luma.wrapper.api.Clearable
import sweetie.evaware.luma.wrapper.api.DrawMode

class VertexStream : Clearable, AutoCloseable {
    private var nextLayoutIndex = 0
    private var vertexCount = 0
    private var gpuFloatCapacity = 0
    private val buffer = LumaVertexBuffer(1)
    private var closed = false

    fun hasVertices() = vertexCount > 0

    fun put(layouts: VertexLayout, layoutPos: Int, type: ShaderVertType, count: Int, args: Array<out Number>) {
        val nextLayoutPos = layouts.layoutPos(nextLayoutIndex)
        val nextType = layouts.type(nextLayoutIndex)
        val nextCount = layouts.count(nextLayoutIndex)

        require(nextLayoutPos == layoutPos) { "Expected layout $nextLayoutPos, got $layoutPos" }
        require(nextType == type) { "Expected layout type $nextType, got $type" }
        require(nextCount == count) { "Expected layout count $nextCount, got $count" }
        require(args.size == count) { "Expected $count args, got ${args.size}" }

        for (arg in args) {
            buffer.put(type, arg)
        }

        advance(layouts)
    }

    fun put2(first: Float, second: Float) {
        buffer.put2(first, second)
        completeVertex()
    }

    fun put6(first: Float, second: Float, third: Float, fourth: Float, fifth: Float, sixth: Float) {
        buffer.putFloat(first)
        buffer.putFloat(second)
        buffer.putFloat(third)
        buffer.putFloat(fourth)
        buffer.putFloat(fifth)
        buffer.putFloat(sixth)
        completeVertex()
    }

    fun put8(first: Float, second: Float, third: Float, fourth: Float, fifth: Float, sixth: Float, seventh: Float, eighth: Float) {
        buffer.putFloat(first)
        buffer.putFloat(second)
        buffer.putFloat(third)
        buffer.putFloat(fourth)
        buffer.putFloat(fifth)
        buffer.putFloat(sixth)
        buffer.putFloat(seventh)
        buffer.putFloat(eighth)
        completeVertex()
    }

    fun put10(
        first: Float,
        second: Float,
        third: Float,
        fourth: Float,
        fifth: Float,
        sixth: Float,
        seventh: Float,
        eighth: Float,
        ninth: Float,
        tenth: Float
    ) {
        buffer.putFloat(first)
        buffer.putFloat(second)
        buffer.putFloat(third)
        buffer.putFloat(fourth)
        buffer.putFloat(fifth)
        buffer.putFloat(sixth)
        buffer.putFloat(seventh)
        buffer.putFloat(eighth)
        buffer.putFloat(ninth)
        buffer.putFloat(tenth)
        completeVertex()
    }

    fun putAttribute2(layouts: VertexLayout, layoutPos: Int, first: Float, second: Float) {
        val nextLayoutPos = layouts.layoutPos(nextLayoutIndex)
        require(nextLayoutPos == layoutPos) { "Expected layout $nextLayoutPos, got $layoutPos" }
        buffer.put2(first, second)
        advance(layouts)
    }

    fun putAttribute4(layouts: VertexLayout, layoutPos: Int, first: Float, second: Float, third: Float, fourth: Float) {
        val nextLayoutPos = layouts.layoutPos(nextLayoutIndex)
        require(nextLayoutPos == layoutPos) { "Expected layout $nextLayoutPos, got $layoutPos" }
        buffer.put4(first, second, third, fourth)
        advance(layouts)
    }

    fun upload(vbo: Int, drawMode: Int): Int = upload(vbo, OpenGlMappings.fromGl(drawMode))

    fun upload(vbo: Int, drawMode: DrawMode): Int {
        if (!hasVertices()) return 0

        Luma.bindArrayBuffer(vbo)
        if (ensureGpuCapacity(buffer.byteCount())) {
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, gpuFloatCapacity.toLong(), GL15.GL_STREAM_DRAW)
        }
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, buffer.byteView())
        GL11.glDrawArrays(OpenGlMappings.drawMode(drawMode), 0, vertexCount)
        val uploadedVertices = vertexCount
        clear()
        return uploadedVertices
    }

    private fun advance(layouts: VertexLayout) {
        nextLayoutIndex++
        if (nextLayoutIndex == layouts.size()) {
            completeVertex()
        }
    }

    private fun completeVertex() {
        vertexCount++
        nextLayoutIndex = 0
    }

    override fun clear() {
        nextLayoutIndex = 0
        vertexCount = 0
        buffer.clear()
    }

    private fun ensureGpuCapacity(requiredBytes: Int): Boolean {
        if (requiredBytes <= gpuFloatCapacity) return false
        gpuFloatCapacity = nextCapacity(requiredBytes, gpuFloatCapacity.coerceAtLeast(1))
        return true
    }

    private fun nextCapacity(required: Int, current: Int): Int {
        var capacity = current.coerceAtLeast(1)
        while (capacity < required) {
            capacity = capacity shl 1
        }
        return capacity
    }

    override fun close() {
        if (closed) return
        buffer.close()
        gpuFloatCapacity = 0
        vertexCount = 0
        nextLayoutIndex = 0
        closed = true
    }
}
