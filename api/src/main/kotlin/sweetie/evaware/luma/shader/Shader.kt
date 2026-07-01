package sweetie.evaware.luma.shader

import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.resource.GlResources

open class Shader(
    private val fragmentPath: String,
    private val vertexPath: String
) : AutoCloseable {
    val inputs = ShaderInputs()
    val vertices get() = inputs.vertices
    val uniforms get() = inputs.uniforms

    private var drawMode = PrimitiveType.TRIANGLES
    private var programHandle: ProgramHandle? = null
    private var loaded = false

    fun drawMode(mode: PrimitiveType) = apply {
        this.drawMode = mode
    }

    open fun load() {
        if (loaded) return
        inputs.requireConfigured()

        val rawVert = loadResource(vertexPath)
        val rawFrag = loadResource(fragmentPath)

        val translated = Luma.shaderTranslator.translate(rawVert, rawFrag, vertices.layout)
        programHandle = Luma.backend.createProgram(translated.vertexSource, translated.fragmentSource, vertices.layout)

        GlResources.track(this)
        loaded = true
    }

    fun attach() {
        if (!loaded) load()
        programHandle?.let { Luma.backend.bindProgram(it) }
    }

    fun draw(): Int {
        val stream = vertices.stream
        if (!stream.hasVertices()) return 0

        val currentHandle = programHandle ?: return 0

        Luma.backend.draw(
            program = currentHandle,
            vertices = stream.flipForUpload(),
            vertexCount = stream.vertexCount,
            uniforms = uniforms,
            primitiveType = drawMode
        )

        val count = stream.vertexCount
        stream.clear()
        return count
    }

    override fun close() {
        if (loaded) {
            programHandle?.close()
            programHandle = null
            loaded = false
            GlResources.untrack(this)
        }
        inputs.close()
    }

    private fun loadResource(path: String): String {
        val stream = javaClass.classLoader.getResourceAsStream(path)
            ?: javaClass.classLoader.getResourceAsStream(path.removePrefix("/"))
            ?: error("Missing shader resource: $path")
        return stream.bufferedReader().use { it.readText() }
    }
}
