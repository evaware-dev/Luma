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
import sweetie.evaware.luma.wrapper.backend.LumaPipeline
import sweetie.evaware.luma.wrapper.frame.FrameInfo
import sweetie.evaware.luma.wrapper.matrix.MatrixControl
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
        private var vertexCapacityBytes = 0

        fun prepareVertexBuffer(device: GpuDevice, encoder: CommandEncoder, vertices: LumaVertexBuffer): GpuBuffer {
            val requiredBytes = vertices.floatCount() * Float.SIZE_BYTES
            if (vertexBuffer == null || requiredBytes > vertexCapacityBytes) {
                vertexBuffer?.close()
                vertexCapacityBytes = nextCapacity(requiredBytes, vertexFormatBytes.coerceAtLeast(1))
                vertexBuffer = device.createBuffer(
                    { LumaMetadata.pipelineLabel("${pipeline.location.path}-vertex") },
                    GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST,
                    vertexCapacityBytes.toLong()
                )
            }

            val target = vertexBuffer ?: error("Vertex buffer was not created")
            encoder.writeToBuffer(target.slice(0, requiredBytes.toLong()), vertices.byteView())
            return target
        }

        override fun close() {
            vertexBuffer?.close()
            vertexBuffer = null
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

    private val matrixUploadBytes: ByteBuffer = BufferUtils.createByteBuffer(16 * Float.SIZE_BYTES)
    private val matrixUploadFloats: FloatBuffer = matrixUploadBytes.asFloatBuffer()
    private val matrixUniformSize = 16L * Float.SIZE_BYTES

    private var sampler: GpuSampler? = null
    private var matrixUniform: GpuBuffer? = null
    private var matrixUniformSlice: GpuBufferSlice? = null
    private var matrixUniformVersion = Int.MIN_VALUE
    private var device: GpuDevice? = null
    private val pipelines = IdentityHashMap<LumaPipeline, PipelineState>()
    private val textures = IdentityHashMap<TextureHandle, UploadedTexture>()

    override fun beginFrame(frameInfo: FrameInfo) = Unit

    override fun draw(pipeline: LumaPipeline, vertices: LumaVertexBuffer, texture: TextureHandle?) {
        if (!vertices.hasVertices()) return
        val resolvedDevice = ensureDevice()
        val pipelineState = ensurePipeline(resolvedDevice, pipeline)
        val encoder = resolvedDevice.createCommandEncoder()
        val vertexBuffer = pipelineState.prepareVertexBuffer(resolvedDevice, encoder, vertices)
        val matrixSlice = prepareMatrixUniform(resolvedDevice, encoder)
        val uploadedTexture = texture?.let { ensureTexture(resolvedDevice, it) }
        val resolvedSampler = if (pipeline.usesTexture) ensureSampler(resolvedDevice) else null

        createRenderPass(encoder).use { pass ->
            configurePass(pass, pipeline, pipelineState.pipeline, matrixSlice)
            if (pipeline.usesTexture) {
                val textureView = uploadedTexture?.view ?: error("Texture is required for textured pipeline")
                pass.bindTexture(
                    pipeline.textureBinding?.vulkanSampler ?: error("Missing texture binding"),
                    textureView,
                    resolvedSampler ?: error("Sampler is missing")
                )
            }
            pass.setVertexBuffer(0, vertexBuffer)
            pass.draw(0, vertices.vertexCount())
        }

        encoder.submit()
        vertices.clear()
    }

    override fun close() {
        matrixUniform?.close()
        matrixUniform = null
        matrixUniformSlice = null
        pipelines.values.forEach(PipelineState::close)
        pipelines.clear()
        sampler?.close()
        sampler = null
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

    private fun ensureSampler(device: GpuDevice): GpuSampler {
        sampler?.let { return it }
        return device.createSampler(
            AddressMode.CLAMP_TO_EDGE,
            AddressMode.CLAMP_TO_EDGE,
            FilterMode.LINEAR,
            FilterMode.LINEAR,
            1,
            OptionalDouble.empty()
        ).also {
            sampler = it
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
            builder.withSampler(spec.textureBinding?.vulkanSampler ?: error("Missing texture binding"))
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

        val projectionVersion = MatrixControl.projectionVersion()
        if (matrixUniformVersion != projectionVersion) {
            matrixUploadFloats.clear()
            MatrixControl.projection().get(matrixUploadFloats)
            matrixUploadBytes.position(0)
            matrixUploadBytes.limit(matrixUniformSize.toInt())
            encoder.writeToBuffer(matrixUniformSlice ?: uniform.slice(0, matrixUniformSize), matrixUploadBytes)
            matrixUniformVersion = projectionVersion
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

    private fun createRenderPass(encoder: CommandEncoder): RenderPass {
        val target = Minecraft.getInstance().mainRenderTarget
        val colorView = RenderSystem.outputColorTextureOverride ?: target.colorTextureView
            ?: error(LumaMetadata.message("could not resolve color target"))
        val depthView = RenderSystem.outputDepthTextureOverride ?: target.depthTextureView

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
}
