package sweetie.evaware.luma.shader

import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.resource.GlResources
import sweetie.evaware.luma.shader.translator.ShaderTranslator
import sweetie.evaware.luma.vertex.VertexCursor

open class Shader(
    private val fragmentPath: String,
    private val vertexPath: String
) : AutoCloseable {
    val inputs = ShaderInputs()
    val vertices get() = inputs.vertices
    val uniforms get() = inputs.uniforms

    @PublishedApi
    internal var drawMode = PrimitiveType.TRIANGLES
    private var translator: ShaderTranslator? = null
    private var programHandle: ProgramHandle? = null
    private var loaded = false

    @PublishedApi
    internal val cursor = VertexCursor()

    fun drawMode(mode: PrimitiveType) = apply {
        this.drawMode = mode
    }

    fun instanced(baseVertexCount: Int = 6) = apply {
        vertices.instanced(baseVertexCount)
        this.drawMode = PrimitiveType.TRIANGLES
    }

    fun translator(translator: ShaderTranslator) = apply {
        check(!loaded) { "Cannot change translator after shader loading" }
        this.translator = translator
    }

    open fun load() {
        if (loaded) return
        inputs.requireConfigured()

        val rawVert = loadResource(vertexPath)
        val rawFrag = loadResource(fragmentPath)

        val translated = (translator ?: Luma.shaderTranslator).translate(rawVert, rawFrag, vertices.layout)
        programHandle = Luma.backend.createProgram(
            translated.vertexSource,
            translated.fragmentSource,
            vertices.layout,
            translated.reflection
        )

        GlResources.track(this)
        loaded = true
    }

    fun attach() {
        if (!loaded) load()
        programHandle?.let { Luma.backend.bindProgram(it) }
    }

    /**
     * Zero-copy immediate vertex write & draw.
     *
     * Reserves space for up to [vertexCount] vertices directly in backend GPU or staging memory,
     * invokes [block] with a reusable [VertexCursor] receiver, and submits the exact number of
     * vertices written.
     */
    inline fun draw(vertexCount: Int, block: VertexCursor.() -> Unit): Int {
        if (vertexCount <= 0) return 0
        val handle = prepareDraw()
        val backend = Luma.backend
        val c = cursor
        backend.beginVertices(handle, vertexCount, c)
        var committed = false
        return try {
            c.block()
            val actualVertices = c.finishVertices()
            committed = true
            val token = c.token
            c.reset()
            c.token = token
            try {
                backend.endVertices(handle, c, actualVertices, uniforms, drawMode)
            } finally {
                c.token = 0L
            }
            actualVertices
        } finally {
            if (!committed) {
                val token = c.token
                c.reset()
                c.token = token
                try {
                    backend.endVertices(handle, c, 0, uniforms, drawMode)
                } finally {
                    c.token = 0L
                }
            }
        }
    }

    @PublishedApi
    internal fun prepareDraw(): ProgramHandle {
        if (!loaded) load()
        return checkNotNull(programHandle) { "Shader program is not loaded" }
    }

    override fun close() {
        if (loaded) {
            programHandle?.close()
            programHandle = null
            loaded = false
            GlResources.untrack(this)
        }
    }

    private fun loadResource(path: String): String {
        val stream = javaClass.classLoader.getResourceAsStream(path)
            ?: javaClass.classLoader.getResourceAsStream(path.removePrefix("/"))
            ?: error("Missing shader resource: $path")
        return stream.bufferedReader().use { it.readText() }
    }
}
