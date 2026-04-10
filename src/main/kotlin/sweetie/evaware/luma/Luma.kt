package sweetie.evaware.luma

import org.lwjgl.glfw.GLFW
import sweetie.evaware.luma.opengl.OpenGlBackend
import sweetie.evaware.luma.opengl.OpenGlStandaloneRenderHost
import sweetie.evaware.luma.runtime.GlBindingTracker
import sweetie.evaware.luma.runtime.GlStateGuard
import sweetie.evaware.luma.runtime.RendererSession
import sweetie.evaware.luma.wrapper.LumaBackendRouter
import sweetie.evaware.luma.wrapper.LumaVertexBuffer
import sweetie.evaware.luma.wrapper.RenderApiType
import sweetie.evaware.luma.wrapper.backend.LumaPipeline
import sweetie.evaware.luma.wrapper.backend.RenderHost
import sweetie.evaware.luma.wrapper.frame.FrameState
import sweetie.evaware.luma.wrapper.matrix.MatrixControl
import sweetie.evaware.luma.wrapper.resource.LumaResources
import sweetie.evaware.luma.wrapper.shader.Shader
import sweetie.evaware.luma.wrapper.texture.TextureHandle
import sweetie.evaware.luma.wrapper.uniform.Mat4Uniform
import sweetie.evaware.luma.wrapper.uniform.ShaderUniforms

object Luma {
    private var renderHost: RenderHost = OpenGlStandaloneRenderHost
    private var renderTypeResolver: () -> RenderApiType = { renderHost.renderType() }
    private val bindingTracker = GlBindingTracker()
    private val stateGuard = GlStateGuard(
        bindingTracker = bindingTracker,
        updateFrameState = { FrameState.update(renderHost.frameInfo()) }
    )
    private val backendRouter = LumaBackendRouter(
        renderTypeResolver = { renderTypeResolver() },
        renderHostProvider = { renderHost }
    )
    private val session = RendererSession(
        backendRouter = backendRouter,
        renderHostProvider = { renderHost },
        stateGuard = stateGuard
    )

    internal var contextProvider: () -> Boolean = { GLFW.glfwGetCurrentContext() != 0L }

    fun hasContext() = contextProvider()

    fun installHost(host: RenderHost) {
        renderHost = host
    }

    var renderType: RenderApiType
        get() = renderTypeResolver()
        set(value) {
            renderTypeResolver = { value }
        }

    var renderTypePredicate: () -> RenderApiType
        get() = renderTypeResolver
        set(value) {
            renderTypeResolver = value
        }

    fun isVulkanBackend() = renderType == RenderApiType.VULKAN

    fun isOpenGlBackend() = renderType == RenderApiType.OPEN_GL

    fun isFrameActive() = session.isFrameActive()

    fun beginMainFramebufferFrame() {
        session.beginMainFramebufferFrame()
    }

    fun beginCurrentFramebufferFrame() {
        session.beginCurrentFramebufferFrame()
    }

    fun endFrame() {
        session.endFrame()
    }

    internal fun render(action: () -> Unit) {
        session.render(action)
    }

    internal fun renderToMainFramebuffer(action: () -> Unit) {
        session.renderToMainFramebuffer(action)
    }

    internal fun useProgram(programId: Int) {
        bindingTracker.useProgram(programId)
    }

    internal fun bindVertexArray(vertexArrayId: Int) {
        bindingTracker.bindVertexArray(vertexArrayId)
    }

    internal fun bindArrayBuffer(bufferId: Int) {
        bindingTracker.bindArrayBuffer(bufferId)
    }

    fun bindTexture(texture: TextureHandle, unit: Int = 0) {
        val backend = backendRouter.resolve()
        if (backend is OpenGlBackend) {
            backend.bind(texture, unit)
        }
    }

    internal fun bindTexture(textureId: Int, unit: Int = 0) {
        bindingTracker.bindTexture(textureId, unit)
    }

    internal fun onTextureDeleted(textureId: Int) {
        bindingTracker.onTextureDeleted(textureId)
    }

    internal fun onProgramDeleted(programId: Int) {
        bindingTracker.onProgramDeleted(programId)
    }

    internal fun onVertexArrayDeleted(vertexArrayId: Int) {
        bindingTracker.onVertexArrayDeleted(vertexArrayId)
    }

    internal fun onArrayBufferDeleted(bufferId: Int) {
        bindingTracker.onArrayBufferDeleted(bufferId)
    }

    internal fun invalidateBindings() {
        stateGuard.invalidateBindings()
    }

    fun applyGameMatrix(uniforms: ShaderUniforms, uniform: Mat4Uniform) {
        uniforms.projectionMat4(uniform, MatrixControl.projection(), MatrixControl.projectionVersion())
    }

    fun drawShader(shader: Shader): Int = shader.draw()

    fun draw(pipeline: LumaPipeline, vertices: LumaVertexBuffer, texture: TextureHandle? = null) {
        session.draw(pipeline, vertices, texture)
    }

    fun closeBackends() {
        session.closeBackends()
    }

    fun close() {
        LumaResources.closeAll()
        closeBackends()
    }
}
