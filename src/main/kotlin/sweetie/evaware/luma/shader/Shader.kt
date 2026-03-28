package sweetie.evaware.luma.shader

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.api.Attachable
import sweetie.evaware.luma.api.Loadable
import sweetie.evaware.luma.api.Unloadable
import sweetie.evaware.luma.resource.LumaResources
import sweetie.evaware.luma.uniform.ShaderUniforms
import sweetie.evaware.luma.vertex.ShaderVertices

class Shader(
    private val fragmentPath: String,
    private val vertexPath: String
) : Loadable, Attachable, Unloadable, AutoCloseable {
    val vertices = ShaderVertices()
    val uniforms = ShaderUniforms()

    private var drawMode = GL11.GL_TRIANGLES
    private var programId = 0
    private var vao = 0
    private var vbo = 0

    fun drawMode(drawMode: Int) = apply {
        this.drawMode = drawMode
    }

    override fun load() {
        if (programId != 0) return
        vertices.requireConfigured()

        var vertexShaderId = 0
        var fragmentShaderId = 0
        var createdProgramId = 0
        var createdVao = 0
        var createdVbo = 0
        var loaded = false

        try {
            vertexShaderId = compile(GL20.GL_VERTEX_SHADER, vertexPath)
            fragmentShaderId = compile(GL20.GL_FRAGMENT_SHADER, fragmentPath)

            createdProgramId = GL20.glCreateProgram()
            GL20.glAttachShader(createdProgramId, vertexShaderId)
            GL20.glAttachShader(createdProgramId, fragmentShaderId)
            vertices.bindLocations(createdProgramId)
            GL20.glLinkProgram(createdProgramId)

            if (GL20.glGetProgrami(createdProgramId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                val log = GL20.glGetProgramInfoLog(createdProgramId)
                error("Failed to link shader program: $log")
            }

            createdVao = GL30.glGenVertexArrays()
            createdVbo = GL15.glGenBuffers()
            vertices.bind(createdVao, createdVbo)
            uniforms.resolve(createdProgramId)

            programId = createdProgramId
            vao = createdVao
            vbo = createdVbo
            LumaResources.track(this)
            loaded = true
        } finally {
            if (fragmentShaderId != 0) {
                GL20.glDeleteShader(fragmentShaderId)
            }
            if (vertexShaderId != 0) {
                GL20.glDeleteShader(vertexShaderId)
            }
            if (!loaded) {
                if (createdVbo != 0) {
                    GL15.glDeleteBuffers(createdVbo)
                    Luma.onArrayBufferDeleted(createdVbo)
                }
                if (createdVao != 0) {
                    GL30.glDeleteVertexArrays(createdVao)
                    Luma.onVertexArrayDeleted(createdVao)
                }
                if (createdProgramId != 0) {
                    GL20.glDeleteProgram(createdProgramId)
                    Luma.onProgramDeleted(createdProgramId)
                }
            }
        }
    }

    override fun attach() {
        if (programId == 0) load()
        Luma.useProgram(programId)
        Luma.bindVertexArray(vao)
    }

    override fun detach() = Unit

    fun draw(): Int = vertices.upload(vbo, drawMode)

    override fun unload() {
        close()
    }

    override fun close() {
        vertices.close()
        if (vbo != 0) {
            GL15.glDeleteBuffers(vbo)
            Luma.onArrayBufferDeleted(vbo)
            vbo = 0
        }
        if (vao != 0) {
            GL30.glDeleteVertexArrays(vao)
            Luma.onVertexArrayDeleted(vao)
            vao = 0
        }
        if (programId != 0) {
            GL20.glDeleteProgram(programId)
            Luma.onProgramDeleted(programId)
            programId = 0
        }
        LumaResources.untrack(this)
    }

    private fun compile(type: Int, path: String): Int {
        val shaderId = GL20.glCreateShader(type)
        GL20.glShaderSource(shaderId, LumaGlslLibrary.resolve(loadResource(path)))
        GL20.glCompileShader(shaderId)

        if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            val log = GL20.glGetShaderInfoLog(shaderId)
            GL20.glDeleteShader(shaderId)
            error("Failed to compile shader: $log")
        }

        return shaderId
    }

    private fun loadResource(path: String) = javaClass.classLoader.getResourceAsStream(path)
        ?.bufferedReader()
        ?.use { it.readText() }
        ?: error("Missing shader resource: $path")
}
