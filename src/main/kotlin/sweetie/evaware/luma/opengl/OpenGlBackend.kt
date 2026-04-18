package sweetie.evaware.luma.opengl

import org.joml.Matrix4f
import org.lwjgl.opengl.*
import org.lwjgl.system.MemoryUtil
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.wrapper.LumaBackend
import sweetie.evaware.luma.wrapper.LumaVertexBuffer
import sweetie.evaware.luma.wrapper.RenderApiType
import sweetie.evaware.luma.wrapper.backend.LumaPipeline
import sweetie.evaware.luma.wrapper.backend.RenderHost
import sweetie.evaware.luma.wrapper.frame.FrameInfo
import sweetie.evaware.luma.wrapper.matrix.MatrixControl
import sweetie.evaware.luma.wrapper.resource.LumaRenderTarget
import sweetie.evaware.luma.wrapper.shader.LumaGlslLibrary
import sweetie.evaware.luma.wrapper.texture.LumaSampler
import sweetie.evaware.luma.wrapper.texture.SampledTexture
import sweetie.evaware.luma.wrapper.texture.TextureBinding
import sweetie.evaware.luma.wrapper.texture.TextureHandle
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.FloatBuffer
import java.util.*

internal class OpenGlBackend : LumaBackend {
    private data class OpenGlStateSnapshot(
        val drawFramebuffer: Int,
        val readFramebuffer: Int,
        val viewportX: Int,
        val viewportY: Int,
        val viewportWidth: Int,
        val viewportHeight: Int
    )

    private val textures = IdentityHashMap<TextureHandle, Int>()
    private val pipelines = IdentityHashMap<LumaPipeline, OpenGlPipeline>()

    override val type = RenderApiType.OPEN_GL

    override fun beginFrame(frameInfo: FrameInfo, host: RenderHost) = Unit

    override fun draw(pipeline: LumaPipeline, vertices: LumaVertexBuffer, texture: TextureBinding?) {
        if (!vertices.hasVertices()) return
        val textureId = when (texture) {
            null -> null
            is TextureBinding.Managed -> ensureTexture(texture.handle)
            is TextureBinding.OpenGl -> texture.textureId
            is TextureBinding.Vulkan -> error("Vulkan textures are not supported on the OpenGL backend")
        }
        this.pipeline(pipeline).draw(
            vertices = vertices,
            matrix = MatrixControl.current(),
            matrixVersion = MatrixControl.projectionVersion(),
            textureId = textureId
        )
    }

    override fun createRenderTarget(debugName: String, width: Int, height: Int): LumaRenderTarget =
        OpenGlRenderTarget(debugName, width, height, LumaSampler.LINEAR_CLAMP)

    override fun drawTo(target: LumaRenderTarget, pipeline: LumaPipeline, vertices: LumaVertexBuffer, texture: SampledTexture?) {
        if (!vertices.hasVertices()) return
        val resolvedTarget = target as? OpenGlRenderTarget
            ?: error("OpenGL backend requires an OpenGlRenderTarget")
        resolvedTarget.ensureSize(resolvedTarget.width, resolvedTarget.height)
        val state = captureState()
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, resolvedTarget.framebufferId())
            GL11.glViewport(0, 0, resolvedTarget.width, resolvedTarget.height)
            draw(pipeline, vertices, texture?.binding)
        } finally {
            restoreState(state)
        }
    }

    fun bind(texture: TextureHandle, unit: Int = 0) {
        val textureId = ensureTexture(texture)
        Luma.bindTexture(textureId, unit)
    }

    override fun close() {
        pipelines.values.forEach(OpenGlPipeline::close)
        pipelines.clear()
        if (Luma.hasContext()) {
            textures.values.forEach { textureId ->
                GL11.glDeleteTextures(textureId)
                Luma.onTextureDeleted(textureId)
            }
        }
        textures.clear()
    }

    private fun ensureTexture(handle: TextureHandle): Int {
        require(!handle.isClosed) { "Texture handle is closed" }
        textures[handle]?.let { return it }

        val textureId = GL11.glGenTextures()
        Luma.bindTexture(textureId)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1)

        uploadTexture(handle.image())
        textures[handle] = textureId
        return textureId
    }

    private fun uploadTexture(image: BufferedImage) {
        val pixels = extractPixels(image)
        val buffer = MemoryUtil.memAlloc(image.width * image.height * 4)
        try {
            for (pixel in pixels) {
                buffer.put((pixel shr 16 and 0xFF).toByte())
                buffer.put((pixel shr 8 and 0xFF).toByte())
                buffer.put((pixel and 0xFF).toByte())
                buffer.put((pixel ushr 24).toByte())
            }
            buffer.flip()
            GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGBA8,
                image.width,
                image.height,
                0,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                buffer
            )
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }

    private fun extractPixels(image: BufferedImage): IntArray {
        val dataBuffer = image.raster.dataBuffer
        if (image.type == BufferedImage.TYPE_INT_ARGB && dataBuffer is DataBufferInt) {
            return dataBuffer.data
        }

        return IntArray(image.width * image.height).also {
            image.getRGB(0, 0, image.width, image.height, it, 0, image.width)
        }
    }

    private fun pipeline(spec: LumaPipeline): OpenGlPipeline = pipelines.getOrPut(spec) {
        OpenGlPipeline(spec)
    }

    private fun captureState(): OpenGlStateSnapshot {
        val viewport = MemoryUtil.memAllocInt(4)
        return try {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport)
            OpenGlStateSnapshot(
                drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                viewportX = viewport.get(0),
                viewportY = viewport.get(1),
                viewportWidth = viewport.get(2),
                viewportHeight = viewport.get(3)
            )
        } finally {
            MemoryUtil.memFree(viewport)
        }
    }

    private fun restoreState(snapshot: OpenGlStateSnapshot) {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, snapshot.drawFramebuffer)
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, snapshot.readFramebuffer)
        GL11.glViewport(snapshot.viewportX, snapshot.viewportY, snapshot.viewportWidth, snapshot.viewportHeight)
    }

    private inner class OpenGlRenderTarget(
        override val debugName: String,
        width: Int,
        height: Int,
        private val sampler: LumaSampler
    ) : LumaRenderTarget {
        override val apiType = RenderApiType.OPEN_GL

        override var width: Int = 0
            private set
        override var height: Int = 0
            private set

        private var framebufferId = 0
        private var textureId = 0

        init {
            ensureSize(width, height)
        }

        override fun ensureSize(width: Int, height: Int) {
            if (width <= 0 || height <= 0) return
            if (this.width == width && this.height == height && framebufferId != 0 && textureId != 0) return

            close()
            this.width = width
            this.height = height

            textureId = GL11.glGenTextures()
            Luma.bindTexture(textureId)
            applySampler(sampler)
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L)

            framebufferId = GL30.glGenFramebuffers()
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferId)
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, textureId, 0)
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                close()
                error("Failed to create OpenGL render target: $debugName")
            }
        }

        override fun textureBinding(): TextureBinding = TextureBinding.OpenGl(textureId)

        fun framebufferId(): Int = framebufferId

        override fun close() {
            if (framebufferId != 0 && Luma.hasContext()) {
                GL30.glDeleteFramebuffers(framebufferId)
            }
            if (textureId != 0 && Luma.hasContext()) {
                GL11.glDeleteTextures(textureId)
                Luma.onTextureDeleted(textureId)
            }
            framebufferId = 0
            textureId = 0
            width = 0
            height = 0
        }

        private fun applySampler(sampler: LumaSampler) {
            when (sampler) {
                LumaSampler.LINEAR_CLAMP -> {
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
                }
            }
        }
    }
}

private class OpenGlPipeline(
    private val spec: LumaPipeline
) : AutoCloseable {
    private val matrixBuffer: FloatBuffer = MemoryUtil.memAllocFloat(16)
    private var closed = false

    private var programId = 0
    private var vertexShaderId = 0
    private var fragmentShaderId = 0
    private var vaoId = 0
    private var vboId = 0
    private var gpuFloatCapacity = 0
    private var uMatrixLocation = -1
    private var textureUniformLocation = -1
    private var projectionVersion = Int.MIN_VALUE

    fun draw(vertices: LumaVertexBuffer, matrix: Matrix4f, matrixVersion: Int, textureId: Int?) {
        ensureLoaded()

        Luma.useProgram(programId)
        Luma.bindVertexArray(vaoId)
        Luma.bindArrayBuffer(vboId)
        if (ensureGpuCapacity(vertices.byteCount())) {
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, gpuFloatCapacity.toLong(), GL15.GL_STREAM_DRAW)
        }
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, vertices.byteView())

        if (uMatrixLocation >= 0 && this.projectionVersion != matrixVersion) {
            matrix.get(matrixBuffer.clear())
            matrixBuffer.flip()
            GL20.glUniformMatrix4fv(uMatrixLocation, false, matrixBuffer)
            this.projectionVersion = matrixVersion
        }

        if (spec.usesTexture) {
            val resolvedTexture = textureId ?: error("Texture is required for textured pipeline")
            Luma.bindTexture(resolvedTexture, 0)
        }

        GL11.glDrawArrays(OpenGlMappings.drawMode(spec.drawMode), 0, vertices.vertexCount())
        vertices.clear()
    }

    private fun ensureLoaded() {
        if (programId != 0) return

        vertexShaderId = compile(GL20.GL_VERTEX_SHADER, spec.openGl.vertexShader)
        fragmentShaderId = compile(GL20.GL_FRAGMENT_SHADER, spec.openGl.fragmentShader)
        programId = GL20.glCreateProgram()
        GL20.glAttachShader(programId, vertexShaderId)
        GL20.glAttachShader(programId, fragmentShaderId)
        GL20.glLinkProgram(programId)

        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            error("Failed to link OpenGL pipeline: ${GL20.glGetProgramInfoLog(programId)}")
        }

        vaoId = GL30.glGenVertexArrays()
        vboId = GL15.glGenBuffers()
        Luma.bindVertexArray(vaoId)
        Luma.bindArrayBuffer(vboId)

        var offsetBytes = 0L
        val strideBytes = spec.layout.strideBytes
        for (index in spec.layout.attributes.indices) {
            val attribute = spec.layout.attributes[index]
            val size = attribute.components
            GL20.glEnableVertexAttribArray(index)
            if (attribute.type.integer && !attribute.normalized) {
                GL30.glVertexAttribIPointer(index, size, OpenGlMappings.vertexType(attribute.type), strideBytes, offsetBytes)
            } else {
                GL20.glVertexAttribPointer(index, size, OpenGlMappings.vertexType(attribute.type), attribute.normalized, strideBytes, offsetBytes)
            }
            offsetBytes += attribute.byteSize.toLong()
        }

        uMatrixLocation = GL20.glGetUniformLocation(programId, spec.openGl.matrixUniform)
        textureUniformLocation = if (spec.usesTexture) {
            GL20.glGetUniformLocation(programId, spec.textureBinding?.openGl?.uniform ?: error("Missing texture binding"))
        } else {
            -1
        }
        if (textureUniformLocation >= 0) {
            Luma.useProgram(programId)
            GL20.glUniform1i(textureUniformLocation, 0)
        }
    }

    private fun ensureGpuCapacity(requiredBytes: Int): Boolean {
        if (requiredBytes <= gpuFloatCapacity) return false
        var capacity = gpuFloatCapacity.coerceAtLeast(spec.layout.strideBytes)
        while (capacity < requiredBytes) {
            capacity = capacity shl 1
        }
        gpuFloatCapacity = capacity
        return true
    }

    private fun compile(type: Int, path: String): Int {
        val shaderId = GL20.glCreateShader(type)
        GL20.glShaderSource(shaderId, LumaGlslLibrary.resolveResource(path))
        GL20.glCompileShader(shaderId)
        if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            val log = GL20.glGetShaderInfoLog(shaderId)
            GL20.glDeleteShader(shaderId)
            error("Failed to compile OpenGL shader $path: $log")
        }
        return shaderId
    }

    override fun close() {
        if (closed) return
        closed = true
        if (Luma.hasContext()) {
            if (vboId != 0) {
                GL15.glDeleteBuffers(vboId)
                Luma.onArrayBufferDeleted(vboId)
            }
            if (vaoId != 0) {
                GL30.glDeleteVertexArrays(vaoId)
                Luma.onVertexArrayDeleted(vaoId)
            }
            if (programId != 0) {
                GL20.glDeleteProgram(programId)
                Luma.onProgramDeleted(programId)
            }
            if (fragmentShaderId != 0) {
                GL20.glDeleteShader(fragmentShaderId)
            }
            if (vertexShaderId != 0) {
                GL20.glDeleteShader(vertexShaderId)
            }
        }

        programId = 0
        vertexShaderId = 0
        fragmentShaderId = 0
        vaoId = 0
        vboId = 0
        gpuFloatCapacity = 0
        MemoryUtil.memFree(matrixBuffer)
    }
}
