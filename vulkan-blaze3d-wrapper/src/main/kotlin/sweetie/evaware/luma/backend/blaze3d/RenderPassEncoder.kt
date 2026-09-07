package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.systems.CommandEncoder
import com.mojang.blaze3d.systems.GpuDevice
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.Minecraft
import org.joml.Vector4f
import sweetie.evaware.luma.LumaNames
import sweetie.evaware.luma.api.BlendFunction
import sweetie.evaware.luma.api.TextureHandle
import java.util.Optional
import java.util.OptionalDouble

internal class RenderPassEncoder(
    private val vertexBuffer: VulkanBuffer,
    private val uboBuffer: VulkanBuffer
) : GroupConsumer {

    private var device: GpuDevice? = null
    private var encoder: CommandEncoder? = null
    private var currentPass: RenderPass? = null
    private var currentProgram: Program? = null
    private var currentTargetHasDepth = false
    private var pipelineGeneration = 0L

    fun begin(device: GpuDevice, encoder: CommandEncoder, pipelineGeneration: Long): RenderPassEncoder {
        check(this.encoder == null) { "Render pass encoder is already active" }
        this.device = device
        this.encoder = encoder
        this.pipelineGeneration = pipelineGeneration
        return this
    }

    override fun onTargetChanged(target: VulkanRenderTarget?, clearColor: FloatArray?) {
        currentPass?.close()
        currentPass = null
        currentProgram = null

        val colorView: GpuTextureView
        val depthView: GpuTextureView?

        if (target != null) {
            colorView = target.colorView
            depthView = target.depthView
        } else {
            val mainTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget()
            colorView = mainTarget.colorTextureView ?: return
            depthView = mainTarget.depthTextureView
        }

        val clear = clearColor?.takeIf { it.size >= 4 }
        val depthClear = if (target != null && depthView != null && clear != null) {
            OptionalDouble.of(1.0)
        } else {
            OptionalDouble.empty()
        }
        val pass = checkNotNull(encoder).createRenderPass(
            { LumaNames.RENDER_PASS },
            colorView,
            if (clear != null) Optional.of(Vector4f(clear[0], clear[1], clear[2], clear[3])) else Optional.empty(),
            depthView,
            depthClear
        )

        currentPass = pass
        currentTargetHasDepth = depthView != null
        RenderSystem.bindDefaultUniforms(pass)
    }

    override fun onPipelineChanged(
        program: Program,
        topology: PrimitiveTopology,
        blendEnabled: Boolean,
        blendFunction: BlendFunction,
        depthEnabled: Boolean,
        depthWrite: Boolean,
        depthFunc: CompareOp,
        cullEnabled: Boolean
    ) {
        val pass = currentPass ?: return
        val pipeline = program.getOrCreatePipeline(
            checkNotNull(device),
            topology,
            blendEnabled,
            blendFunction,
            depthEnabled,
            depthWrite,
            depthFunc,
            cullEnabled,
            currentTargetHasDepth,
            pipelineGeneration
        )
        pass.setPipeline(pipeline)
        currentProgram = program
    }

    override fun onUboChanged(offset: Long, size: Long) {
        val pass = currentPass ?: return
        pass.setUniform(LumaNames.UNIFORMS_BLOCK, uboBuffer.slice(offset, size))
    }

    override fun onTextureChanged(program: Program, textures: Array<TextureHandle?>) {
        val pass = currentPass ?: return
        val bindings = program.samplerBindings
        for (index in bindings.indices) {
            val binding = bindings[index]
            val texture = textures.getOrNull(binding.unit) as? VulkanTexture ?: continue
            pass.bindTexture(binding.name, texture.view, texture.sampler)
        }
    }

    override fun onDraw(topology: PrimitiveTopology, vertexStart: Long, vertexBytes: Long, vertexCount: Int) {
        val pass = currentPass ?: return
        pass.setVertexBuffer(0, vertexBuffer.slice(vertexStart, vertexBytes))

        val program = currentProgram
        if (program != null && program.layout.instanced) {
            pass.draw(program.layout.baseVertexCount, vertexCount, 0, 0)
            return
        }

        if (topology == PrimitiveTopology.QUADS) {
            val indexCount = (vertexCount / 4) * 6
            val sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)
            val indexBuffer = sequential.getBuffer(indexCount)
            pass.setIndexBuffer(indexBuffer, sequential.type())
            pass.drawIndexed(indexCount, 1, 0, 0, 0)
        } else {
            pass.draw(vertexCount, 1, 0, 0)
        }
    }

    fun finish() {
        currentPass?.close()
        currentPass = null
        currentProgram = null
        device = null
        encoder = null
    }
}
