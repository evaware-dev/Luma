package sweetie.evaware.luma.platform.minecraft.vulkan

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.CommandEncoder
import com.mojang.blaze3d.systems.GpuDevice
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.*
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.lwjgl.BufferUtils
import sweetie.evaware.luma.vulkan.VulkanRuntime
import sweetie.evaware.luma.wrapper.LumaMetadata
import sweetie.evaware.luma.wrapper.LumaVertexBuffer
import sweetie.evaware.luma.wrapper.RenderApiType
import sweetie.evaware.luma.wrapper.backend.LumaPipeline
import sweetie.evaware.luma.wrapper.frame.FrameInfo
import sweetie.evaware.luma.wrapper.matrix.MatrixControl
import sweetie.evaware.luma.wrapper.resource.LumaRenderTarget
import sweetie.evaware.luma.wrapper.texture.LumaSampler
import sweetie.evaware.luma.wrapper.texture.SampledTexture
import sweetie.evaware.luma.wrapper.texture.TextureBinding
import sweetie.evaware.luma.wrapper.texture.TextureHandle
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.util.*

internal class MinecraftVulkanRuntime : VulkanRuntime {
    private data class UploadedTexture(
        val texture: GpuTexture,
        val view: GpuTextureView
    ) : AutoCloseable {
        override fun close() {
            view.close()
            texture.close()
        }
    }

    private class PipelineState(
        val pipeline: RenderPipeline,
        val vertexFormatBytes: Int
    ) : AutoCloseable {
        private var vertexBuffer: GpuBuffer? = null
        private var vertexSlice: GpuBufferSlice? = null
        private var vertexCapacityBytes = 0

        fun prepareVertexBuffer(device: GpuDevice, encoder: CommandEncoder, vertices: LumaVertexBuffer): GpuBuffer {
            val requiredBytes = vertices.byteCount()
            if (vertexBuffer == null || requiredBytes > vertexCapacityBytes) {
                vertexBuffer?.close()
                vertexCapacityBytes = nextCapacity(requiredBytes, vertexFormatBytes.coerceAtLeast(1))
                vertexBuffer = device.createBuffer(
                    { LumaMetadata.pipelineLabel("${pipeline.location.path}-vertex") },
                    GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST,
                    vertexCapacityBytes.toLong()
                )
                vertexSlice = vertexBuffer?.slice(0, vertexCapacityBytes.toLong())
            }

            val target = vertexBuffer ?: error("Vertex buffer was not created")
            encoder.writeToBuffer(vertexSlice ?: error("Vertex slice was not created"), vertices.byteView())
            return target
        }

        override fun close() {
            vertexBuffer?.close()
            vertexBuffer = null
            vertexSlice = null
            vertexCapacityBytes = 0
        }

        private fun nextCapacity(required: Int, current: Int): Int {
            var capacity = current
            while (capacity < required) {
                capacity = capacity shl 1
            }
            return capacity
        }
    }

    private inner class VulkanRenderTarget(
        override val debugName: String,
        width: Int,
        height: Int
    ) : LumaRenderTarget {
        override val apiType = RenderApiType.VULKAN

        override var width: Int = 0
            private set
        override var height: Int = 0
            private set

        private var texture: GpuTexture? = null
        private var view: GpuTextureView? = null
        private var owningDevice: GpuDevice? = null

        init {
            ensureSize(width, height)
        }

        override fun ensureSize(width: Int, height: Int) {
            if (width <= 0 || height <= 0) return
            val device = ensureDevice()
            if (owningDevice !== device || this.width != width || this.height != height || texture == null || view == null) {
                close()
                owningDevice = device
                this.width = width
                this.height = height
                texture = createRenderTexture(device, debugName, width, height)
                view = device.createTextureView(texture ?: error("Render target texture is missing"))
            }
        }

        override fun textureBinding(): TextureBinding =
            TextureBinding.Vulkan(view ?: error("Render target view is missing"))

        fun colorView(): GpuTextureView = view ?: error("Render target view is missing")

        override fun close() {
            view?.close()
            texture?.close()
            view = null
            texture = null
            owningDevice = null
            width = 0
            height = 0
        }
    }

    private val matrixUploadBytes: ByteBuffer = BufferUtils.createByteBuffer(16 * Float.SIZE_BYTES)
    private val matrixUploadFloats: FloatBuffer = matrixUploadBytes.asFloatBuffer()
    private val matrixUniformSize = 16L * Float.SIZE_BYTES

    private val samplers = EnumMap<LumaSampler, GpuSampler>(LumaSampler::class.java)
    private var matrixUniform: GpuBuffer? = null
    private var matrixUniformSlice: GpuBufferSlice? = null
    private var matrixUniformVersion = Int.MIN_VALUE
    private var device: GpuDevice? = null
    private var activeEncoder: CommandEncoder? = null
    private val pipelines = IdentityHashMap<LumaPipeline, PipelineState>()
    private val textures = IdentityHashMap<TextureHandle, UploadedTexture>()

    override fun beginFrame(frameInfo: FrameInfo) {
        if (activeEncoder != null) return
        activeEncoder = ensureDevice().createCommandEncoder()
    }

    override fun draw(pipeline: LumaPipeline, vertices: LumaVertexBuffer, texture: TextureBinding?) {
        drawInternal(
            targetView = null,
            pipeline = pipeline,
            vertices = vertices,
            texture = texture?.let { SampledTexture(it) }
        )
    }

    override fun createRenderTarget(debugName: String, width: Int, height: Int): LumaRenderTarget =
        VulkanRenderTarget(debugName, width, height)

    override fun drawTo(target: LumaRenderTarget, pipeline: LumaPipeline, vertices: LumaVertexBuffer, texture: SampledTexture?) {
        val resolvedTarget = target as? VulkanRenderTarget
            ?: error("Vulkan backend requires a VulkanRenderTarget")
        resolvedTarget.ensureSize(resolvedTarget.width, resolvedTarget.height)
        drawInternal(
            targetView = resolvedTarget.colorView(),
            pipeline = pipeline,
            vertices = vertices,
            texture = texture
        )
    }

    private fun drawInternal(
        targetView: GpuTextureView?,
        pipeline: LumaPipeline,
        vertices: LumaVertexBuffer,
        texture: SampledTexture?
    ) {
        if (!vertices.hasVertices()) return
        val resolvedDevice = ensureDevice()
        val pipelineState = ensurePipeline(resolvedDevice, pipeline)
        val encoder = activeEncoder ?: resolvedDevice.createCommandEncoder()
        val ownsEncoder = activeEncoder == null
        val vertexBuffer = pipelineState.prepareVertexBuffer(resolvedDevice, encoder, vertices)
        val matrixSlice = prepareMatrixUniform(resolvedDevice, encoder)
        val uploadedTexture = resolveTexture(resolvedDevice, texture?.binding)
        val resolvedSampler = if (pipeline.usesTexture) ensureSampler(resolvedDevice, texture?.sampler ?: LumaSampler.LINEAR_CLAMP) else null

        val pass = createRenderPass(encoder, targetView)

        try {
            configurePass(pass, pipeline, pipelineState.pipeline, matrixSlice)
            if (pipeline.usesTexture) {
                val textureView = uploadedTexture?.view ?: error("Texture is required for textured pipeline")
                pass.bindTexture(
                    pipeline.textureBinding?.vulkan?.sampler ?: error("Missing texture binding"),
                    textureView,
                    resolvedSampler ?: error("Sampler is missing")
                )
            }
            pass.setVertexBuffer(0, vertexBuffer)
            pass.draw(0, vertices.vertexCount())
        } finally {
            pass.close()
        }

        if (ownsEncoder) {
            encoder.submit()
        }
        vertices.clear()
    }

    override fun endFrame() {
        activeEncoder?.submit()
        activeEncoder = null
    }

    override fun close() {
        activeEncoder = null
        matrixUniform?.close()
        matrixUniform = null
        matrixUniformSlice = null
        pipelines.values.forEach(PipelineState::close)
        pipelines.clear()
        samplers.values.forEach(GpuSampler::close)
        samplers.clear()
        matrixUniformVersion = Int.MIN_VALUE
        textures.values.forEach(UploadedTexture::close)
        textures.clear()
        device = null
    }

    private fun ensureDevice(): GpuDevice {
        val currentDevice = RenderSystem.getDevice()
        if (device === currentDevice) {
            return currentDevice
        }

        close()
        device = currentDevice
        return currentDevice
    }

    private fun ensureSampler(device: GpuDevice, sampler: LumaSampler): GpuSampler {
        samplers[sampler]?.let { return it }
        return when (sampler) {
            LumaSampler.LINEAR_CLAMP -> device.createSampler(
                AddressMode.CLAMP_TO_EDGE,
                AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR,
                FilterMode.LINEAR,
                1,
                OptionalDouble.empty()
            )
        }.also {
            samplers[sampler] = it
        }
    }

    private fun ensurePipeline(device: GpuDevice, spec: LumaPipeline): PipelineState {
        pipelines[spec]?.let { return it }

        val vertexIdentifier = Identifier.fromNamespaceAndPath(spec.vulkan.shaderNamespace, spec.vulkan.vertexShader)
        val fragmentIdentifier = Identifier.fromNamespaceAndPath(spec.vulkan.shaderNamespace, spec.vulkan.fragmentShader)
        val identifier = if (vertexIdentifier == fragmentIdentifier) {
            vertexIdentifier
        } else {
            Identifier.fromNamespaceAndPath(spec.vulkan.shaderNamespace, "${spec.debugName}_pipeline")
        }
        val vertexFormat = MinecraftGpuVertexFormats.format(spec)

        val builder = RenderPipeline.builder()
            .withLocation(identifier)
            .withVertexShader(vertexIdentifier)
            .withFragmentShader(fragmentIdentifier)
            .withUniform(spec.vulkan.matrixUniform, com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(Optional.empty())
            .withCull(false)
            .withVertexFormat(vertexFormat, MinecraftGpuVertexFormats.drawMode(spec.drawMode))

        if (spec.usesTexture) {
            builder.withSampler(spec.textureBinding?.vulkan?.sampler ?: error("Missing texture binding"))
        }

        val pipeline = builder.build()
        device.precompilePipeline(pipeline, MinecraftGpuShaderSource)
        return PipelineState(pipeline, pipeline.vertexFormat.vertexSize).also {
            pipelines[spec] = it
        }
    }

    private fun prepareMatrixUniform(device: GpuDevice, encoder: CommandEncoder): GpuBufferSlice {
        val uniform = matrixUniform ?: device.createBuffer(
            { LumaMetadata.pipelineLabel("projection") },
            GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST,
            matrixUniformSize
        ).also {
            matrixUniform = it
            matrixUniformSlice = it.slice(0, matrixUniformSize)
        }

        val matrixVersion = MatrixControl.projectionVersion()
        if (matrixUniformVersion != matrixVersion) {
            matrixUploadFloats.clear()
            MatrixControl.current().get(matrixUploadFloats)
            matrixUploadBytes.position(0)
            matrixUploadBytes.limit(matrixUniformSize.toInt())
            encoder.writeToBuffer(matrixUniformSlice ?: uniform.slice(0, matrixUniformSize), matrixUploadBytes)
            matrixUniformVersion = matrixVersion
        }
        return matrixUniformSlice ?: uniform.slice(0, matrixUniformSize).also {
            matrixUniformSlice = it
        }
    }

    private fun ensureTexture(device: GpuDevice, handle: TextureHandle): UploadedTexture {
        require(!handle.isClosed) { "Texture handle is closed" }
        textures[handle]?.let { return it }

        val image = handle.image()
        val texture = device.createTexture(
            { LumaMetadata.textureLabel(handle.debugName) },
            GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_COPY_DST,
            GpuFormat.RGBA8_UNORM,
            image.width,
            image.height,
            1,
            1
        )

        uploadTexture(device, texture, image)
        return UploadedTexture(texture, device.createTextureView(texture)).also {
            textures[handle] = it
        }
    }

    private fun resolveTexture(device: GpuDevice, binding: TextureBinding?): UploadedTexture? = when (binding) {
        null -> null
        is TextureBinding.Managed -> ensureTexture(device, binding.handle)
        is TextureBinding.OpenGl -> error("Raw OpenGL textures are not supported on the Vulkan backend")
        is TextureBinding.Vulkan -> UploadedTexture(binding.view.texture(), binding.view)
    }

    private fun uploadTexture(device: GpuDevice, texture: GpuTexture, image: BufferedImage) {
        NativeImage(NativeImage.Format.RGBA, image.width, image.height, false).use { nativeImage ->
            val pixels = extractPixels(image)
            var index = 0
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    nativeImage.setPixelABGR(x, y, argbToAbgr(pixels[index++]))
                }
            }

            val encoder = device.createCommandEncoder()
            encoder.writeToTexture(texture, nativeImage)
            encoder.submit()
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

    private fun argbToAbgr(argb: Int): Int {
        val alpha = argb and -0x1000000
        val red = (argb shr 16) and 0xFF
        val green = argb and 0x0000FF00
        val blue = (argb and 0xFF) shl 16
        return alpha or green or blue or red
    }

    private fun createRenderPass(encoder: CommandEncoder, colorOverride: GpuTextureView? = null): RenderPass {
        val target = Minecraft.getInstance().mainRenderTarget
        val colorView = colorOverride ?: RenderSystem.outputColorTextureOverride ?: target.colorTextureView
            ?: error(LumaMetadata.message("could not resolve color target"))
        val depthView = if (colorOverride == null) {
            RenderSystem.outputDepthTextureOverride ?: target.depthTextureView
        } else {
            null
        }

        return if (depthView != null) {
            encoder.createRenderPass(
                { LumaMetadata.vulkanPassLabel },
                colorView,
                OptionalInt.empty(),
                depthView,
                OptionalDouble.empty()
            )
        } else {
            encoder.createRenderPass(
                { LumaMetadata.vulkanPassLabel },
                colorView,
                OptionalInt.empty()
            )
        }
    }

    private fun configurePass(pass: RenderPass, spec: LumaPipeline, pipeline: RenderPipeline, matrixSlice: GpuBufferSlice) {
        pass.setPipeline(pipeline)
        pass.setUniform(spec.vulkan.matrixUniform, matrixSlice)
    }

    private fun createRenderTexture(device: GpuDevice, debugName: String, width: Int, height: Int): GpuTexture =
        device.createTexture(
            { LumaMetadata.textureLabel(debugName) },
            GpuTexture.USAGE_RENDER_ATTACHMENT or GpuTexture.USAGE_TEXTURE_BINDING,
            GpuFormat.RGBA8_UNORM,
            width,
            height,
            1,
            1
        )
}
