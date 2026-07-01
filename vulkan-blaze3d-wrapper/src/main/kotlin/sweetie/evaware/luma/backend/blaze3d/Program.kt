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

class LumaShaderSource(
    val vertexCode: String,
    val fragmentCode: String
) : ShaderSource {
    override fun get(id: Identifier, type: ShaderType): String {
        return if (type == ShaderType.VERTEX) vertexCode else fragmentCode
    }
}

private fun RenderPipeline.close() {
    (this as? AutoCloseable)?.close()
}

data class PipelineKey(
    val topology: PrimitiveTopology,
    val depthEnabled: Boolean,
    val depthWrite: Boolean,
    val depthFunc: CompareOp,
    val cullEnabled: Boolean
)

data class SamplerBinding(val name: String, val unit: Int)
class Program(
    val id: Identifier,
    val vertexSource: String,
    val fragmentSource: String,
    val layout: VertexLayout
) : ProgramHandle {
    private val pipelines = HashMap<PipelineKey, RenderPipeline>()
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

    fun getOrCreatePipeline(device: GpuDevice, key: PipelineKey): RenderPipeline {
        return pipelines.getOrPut(key) {
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
                if (key.depthEnabled) key.depthFunc else CompareOp.ALWAYS_PASS,
                key.depthWrite
            )

            val pipelineBuilder = RenderPipeline.builder()
                .withLocation(id)
                .withVertexShader(id)
                .withFragmentShader(id)
                .withVertexBinding(0, fmt)
                .withBindGroupLayout(bindGroupLayout)
                .withCull(key.cullEnabled)
                .withDepthStencilState(depthState)
                .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
                .withPrimitiveTopology(key.topology)
                .build()

            val source = LumaShaderSource(vertexSource, fragmentSource)
            val compiled = device.precompilePipeline(pipelineBuilder, source)
            if (!compiled.isValid) {
                val messages = device.getLastDebugMessages()
                error("Pipeline compilation failed for $id:\n${messages.joinToString("\n")}")
            }
            pipelineBuilder
        }
    }

    fun precompile(device: GpuDevice) {
        val topologies = listOf(PrimitiveTopology.TRIANGLES, PrimitiveTopology.QUADS, PrimitiveTopology.LINES)
        for (topology in topologies) {
            getOrCreatePipeline(device, PipelineKey(topology, false, false, CompareOp.ALWAYS_PASS, false))
            getOrCreatePipeline(device, PipelineKey(topology, false, false, CompareOp.ALWAYS_PASS, true))
            getOrCreatePipeline(device, PipelineKey(topology, true, true, CompareOp.LESS_THAN_OR_EQUAL, false))
            getOrCreatePipeline(device, PipelineKey(topology, true, true, CompareOp.LESS_THAN_OR_EQUAL, true))
        }
    }

    override fun close() {
        pipelines.values.forEach { it.close() }
        pipelines.clear()
    }

    private fun parseAttributes(vertex: String): Map<Int, String> {
        val map = HashMap<Int, String>()
        val regex = Regex("""^\s*//\s*@in\s+(\d+)\s+[a-zA-Z0-9_]+\s+[a-zA-Z0-9_]+\s+([a-zA-Z0-9_]+)""")
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
