package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BindGroupLayout
import com.mojang.blaze3d.pipeline.BlendFunction as MinecraftBlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.platform.BlendFactor as MinecraftBlendFactor
import com.mojang.blaze3d.platform.BlendOp as MinecraftBlendOp
import com.mojang.blaze3d.shaders.ShaderSource
import com.mojang.blaze3d.shaders.ShaderType
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.systems.GpuDevice
import java.util.Optional
import net.minecraft.resources.Identifier
import sweetie.evaware.luma.LumaNames
import sweetie.evaware.luma.api.BlendFactor
import sweetie.evaware.luma.api.BlendFunction
import sweetie.evaware.luma.api.BlendOp
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.uniform.*
import sweetie.evaware.luma.vertex.VertexLayout
import sweetie.evaware.luma.vertex.VertexInputLayout

class LumaShaderSource(
    val vertexCode: String,
    val fragmentCode: String
) : ShaderSource {
    override fun get(id: Identifier, type: ShaderType): String {
        return if (type == ShaderType.VERTEX) vertexCode else fragmentCode
    }
}

data class SamplerBinding(val name: String, val unit: Int)
class Program(
    val id: Identifier,
    val vertexSource: String,
    val fragmentSource: String,
    val layouts: VertexInputLayout,
    private val scheduleClose: ((() -> Unit) -> Unit) = { action -> action() }
) : ProgramHandle {
    constructor(
        id: Identifier,
        vertexSource: String,
        fragmentSource: String,
        layout: VertexLayout,
        scheduleClose: ((() -> Unit) -> Unit) = { action -> action() }
    ) : this(id, vertexSource, fragmentSource, VertexInputLayout().binding(layout), scheduleClose)

    val layout = layouts.layout(0)
    private val shaderSource = LumaShaderSource(vertexSource, fragmentSource)
    private var pipelineKeys = LongArray(4)
    private var pipelines = arrayOfNulls<RenderPipeline>(4)
    private var pipelineGenerations = LongArray(4) { Long.MIN_VALUE }
    private var pipelineCount = 0
    private var closeScheduled = false
    val uniformInfos = parseUniforms(vertexSource, fragmentSource)
    val attributeNames = parseAttributes(vertexSource)
    val samplers = run {
        val samplerRegex = Regex("""\b${LumaNames.SAMPLER_PREFIX}\d+\b""")
        (vertexSource.lines() + fragmentSource.lines())
            .flatMap { samplerRegex.findAll(it).map { m -> m.value } }
            .distinct()
    }

    val samplerBindings: List<SamplerBinding> = samplers.map { name ->
        SamplerBinding(name, name.removePrefix(LumaNames.SAMPLER_PREFIX).toIntOrNull() ?: 0)
    }

    var uboCacheFrameId: Long = -1L
    var uboCacheOffset: Long = 0L
    var uboCacheBytes: Int = 0

    private var vulkanUniforms: List<VulkanUniform>? = null

    fun getVulkanUniforms(uniforms: ShaderUniforms): List<VulkanUniform> {
        var list = vulkanUniforms
        if (list == null) {
            list = uniformInfos.map { info ->
                when (info.type) {
                    "mat4" -> VulkanMat4Uniform(info.name, info.type)
                    "vec4" -> VulkanFloat4Uniform(info.name, info.type)
                    "vec3" -> VulkanFloat3Uniform(info.name, info.type)
                    "vec2" -> VulkanFloat2Uniform(info.name, info.type)
                    "float" -> VulkanFloat1Uniform(info.name, info.type)
                    "int" -> VulkanInt1Uniform(info.name, info.type)
                    else -> error("Unsupported Vulkan uniform type: ${info.type}")
                }
            }
            vulkanUniforms = list
        }
        return list
    }

    internal fun getOrCreatePipeline(
        device: GpuDevice,
        topology: PrimitiveTopology,
        blendEnabled: Boolean,
        blendFunction: BlendFunction,
        depthEnabled: Boolean,
        depthWrite: Boolean,
        depthFunc: CompareOp,
        cullEnabled: Boolean,
        hasDepthAttachment: Boolean,
        pipelineGeneration: Long
    ): RenderPipeline {
        val key = pipelineKey(topology, blendEnabled, blendFunction, depthEnabled, depthWrite, depthFunc, cullEnabled, hasDepthAttachment)
        for (index in 0 until pipelineCount) {
            if (pipelineKeys[index] == key) {
                val pipeline = pipelines[index]!!
                if (pipelineGenerations[index] != pipelineGeneration) {
                    compile(device, pipeline)
                    pipelineGenerations[index] = pipelineGeneration
                }
                return pipeline
            }
        }

        val pipeline = run {
            val layoutBuilder = BindGroupLayout.builder()
            if (uniformInfos.isNotEmpty()) {
                layoutBuilder.withUniform(LumaNames.UNIFORMS_BLOCK, UniformType.UNIFORM_BUFFER)
            }
            for (sampler in samplers) {
                layoutBuilder.withSampler(sampler)
            }
            val bindGroupLayout = layoutBuilder.build()

            val depthState = DepthStencilState(
                if (depthEnabled) depthFunc else CompareOp.ALWAYS_PASS,
                depthWrite
            )

            val pipelineBuilder = RenderPipeline.builder()
                .withLocation(id)
                .withVertexShader(id)
                .withFragmentShader(id)
                .withBindGroupLayout(bindGroupLayout)
                .withCull(cullEnabled)
                .withDepthStencilState(
                    if (hasDepthAttachment) Optional.of(depthState) else Optional.empty()
                )
                .withColorTargetState(if (blendEnabled) ColorTargetState(blendFunction.toMinecraft()) else ColorTargetState.DEFAULT)
                .withPrimitiveTopology(topology)

            for (binding in 0 until layouts.size()) {
                pipelineBuilder.withVertexBinding(
                    binding,
                    convertLayout(layouts.layout(binding), attributeNames, layouts.stepRate(binding))
                )
            }

            val built = pipelineBuilder.build()
            compile(device, built)
            built
        }

        if (pipelineCount == pipelines.size) {
            pipelineKeys = pipelineKeys.copyOf(pipelineCount shl 1)
            pipelines = pipelines.copyOf(pipelineCount shl 1)
            pipelineGenerations = pipelineGenerations.copyOf(pipelineCount shl 1)
        }
        pipelineKeys[pipelineCount] = key
        pipelines[pipelineCount] = pipeline
        pipelineGenerations[pipelineCount] = pipelineGeneration
        pipelineCount++
        return pipeline
    }

    private fun compile(device: GpuDevice, pipeline: RenderPipeline): RenderPipeline {
        val compiled = device.precompilePipeline(pipeline, shaderSource)
        if (!compiled.isValid) {
            val messages = device.getLastDebugMessages()
            error("Pipeline compilation failed for $id:\n${messages.joinToString("\n")}")
        }
        return pipeline
    }

    fun precompileDefaults(device: GpuDevice, pipelineGeneration: Long) {
        requireOpen()
        for (hasDepthAttachment in booleanArrayOf(false, true)) {
            getOrCreatePipeline(
                device,
                PrimitiveTopology.TRIANGLES,
                true,
                BlendFunction.TRANSLUCENT,
                false,
                false,
                CompareOp.ALWAYS_PASS,
                false,
                hasDepthAttachment,
                pipelineGeneration
            )
        }
    }

    override fun close() {
        if (closeScheduled) return
        closeScheduled = true
        scheduleClose(this::clearPipelines)
    }

    internal fun requireOpen() {
        check(!closeScheduled) { "Program is closed" }
    }

    private fun clearPipelines() {
        for (index in 0 until pipelineCount) {
            pipelines[index] = null
            pipelineGenerations[index] = Long.MIN_VALUE
        }
        pipelineCount = 0
    }

    private fun pipelineKey(
        topology: PrimitiveTopology,
        blendEnabled: Boolean,
        blendFunction: BlendFunction,
        depthEnabled: Boolean,
        depthWrite: Boolean,
        depthFunc: CompareOp,
        cullEnabled: Boolean,
        hasDepthAttachment: Boolean
    ): Long {
        var key = topology.ordinal.toLong()
        key = key * CompareOp.entries.size + depthFunc.ordinal
        key = key * BlendFactor.entries.size + blendFunction.sourceColor.ordinal
        key = key * BlendFactor.entries.size + blendFunction.destinationColor.ordinal
        key = key * BlendFactor.entries.size + blendFunction.sourceAlpha.ordinal
        key = key * BlendFactor.entries.size + blendFunction.destinationAlpha.ordinal
        key = key * BlendOp.entries.size + blendFunction.colorOp.ordinal
        key = key * BlendOp.entries.size + blendFunction.alphaOp.ordinal
        key = key * 2 + if (blendEnabled) 1 else 0
        key = key * 2 + if (depthEnabled) 1 else 0
        key = key * 2 + if (depthWrite) 1 else 0
        key = key * 2 + if (cullEnabled) 1 else 0
        return key * 2 + if (hasDepthAttachment) 1 else 0
    }

    private fun BlendFunction.toMinecraft() = MinecraftBlendFunction(
        sourceColor.toMinecraft(),
        destinationColor.toMinecraft(),
        colorOp.toMinecraft(),
        sourceAlpha.toMinecraft(),
        destinationAlpha.toMinecraft(),
        alphaOp.toMinecraft()
    )

    private fun BlendFactor.toMinecraft(): MinecraftBlendFactor =
        MinecraftBlendFactor.valueOf(name)

    private fun BlendOp.toMinecraft(): MinecraftBlendOp = MinecraftBlendOp.valueOf(name)

    private fun parseAttributes(vertex: String): Map<Int, String> {
        val map = HashMap<Int, String>()
        val directive = Regex.escape(LumaNames.ATTRIBUTE_DIRECTIVE)
        val regex = Regex("""^\s*//\s*$directive\s+(\d+)\s+[a-zA-Z0-9_]+\s+[a-zA-Z0-9_]+\s+([a-zA-Z0-9_]+)""")
        for (line in vertex.lines()) {
            val match = regex.find(line)
            if (match != null) {
                val loc = match.groupValues[1].toInt()
                val name = match.groupValues[2]
                map[loc] = name
            }
        }
        return map
    }
}
