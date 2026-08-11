package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.BindGroupLayout
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.systems.GpuDevice
import sweetie.evaware.luma.LumaNames
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.vertex.VertexLayout
import sweetie.evaware.luma.uniform.*
import net.minecraft.resources.Identifier

import com.mojang.blaze3d.shaders.ShaderSource
import com.mojang.blaze3d.shaders.ShaderType
import java.util.Optional

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
    val layout: VertexLayout,
    private val scheduleClose: ((() -> Unit) -> Unit) = { action -> action() }
) : ProgramHandle {
    private var pipelineKeys = IntArray(4)
    private var pipelines = arrayOfNulls<RenderPipeline>(4)
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
        depthEnabled: Boolean,
        depthWrite: Boolean,
        depthFunc: CompareOp,
        cullEnabled: Boolean,
        hasDepthAttachment: Boolean
    ): RenderPipeline {
        val key = pipelineKey(topology, depthEnabled, depthWrite, depthFunc, cullEnabled, hasDepthAttachment)
        for (index in 0 until pipelineCount) {
            if (pipelineKeys[index] == key) return pipelines[index]!!
        }

        val pipeline = run {
            val fmt = convertLayout(layout, attributeNames)
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
                .withVertexBinding(0, fmt)
                .withBindGroupLayout(bindGroupLayout)
                .withCull(cullEnabled)
                .withDepthStencilState(
                    if (hasDepthAttachment) Optional.of(depthState) else Optional.empty()
                )
                .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
                .withPrimitiveTopology(topology)
                .build()

            val source = LumaShaderSource(vertexSource, fragmentSource)
            val compiled = device.precompilePipeline(pipelineBuilder, source)
            if (!compiled.isValid) {
                val messages = device.getLastDebugMessages()
                error("Pipeline compilation failed for $id:\n${messages.joinToString("\n")}")
            }
            pipelineBuilder
        }

        if (pipelineCount == pipelines.size) {
            pipelineKeys = pipelineKeys.copyOf(pipelineCount shl 1)
            pipelines = pipelines.copyOf(pipelineCount shl 1)
        }
        pipelineKeys[pipelineCount] = key
        pipelines[pipelineCount] = pipeline
        pipelineCount++
        return pipeline
    }

    fun precompileDefaults(device: GpuDevice) {
        requireOpen()
        for (hasDepthAttachment in booleanArrayOf(false, true)) {
            getOrCreatePipeline(
                device,
                PrimitiveTopology.TRIANGLES,
                false,
                false,
                CompareOp.ALWAYS_PASS,
                false,
                hasDepthAttachment
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
        for (index in 0 until pipelineCount) pipelines[index] = null
        pipelineCount = 0
    }

    private fun pipelineKey(
        topology: PrimitiveTopology,
        depthEnabled: Boolean,
        depthWrite: Boolean,
        depthFunc: CompareOp,
        cullEnabled: Boolean,
        hasDepthAttachment: Boolean
    ): Int {
        var key = topology.ordinal
        key = key * CompareOp.entries.size + depthFunc.ordinal
        key = key * 2 + if (depthEnabled) 1 else 0
        key = key * 2 + if (depthWrite) 1 else 0
        key = key * 2 + if (cullEnabled) 1 else 0
        return key * 2 + if (hasDepthAttachment) 1 else 0
    }

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
