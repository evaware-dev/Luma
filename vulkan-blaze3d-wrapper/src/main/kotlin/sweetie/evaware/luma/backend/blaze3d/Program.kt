package sweetie.evaware.luma.backend.blaze3d

import sweetie.evaware.luma.LumaNames
import sweetie.evaware.luma.api.BlendFactor
import sweetie.evaware.luma.api.BlendFunction
import sweetie.evaware.luma.api.BlendOp
import sweetie.evaware.luma.api.DepthCompare
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.backend.blaze3d.preset.PresetPipeline
import sweetie.evaware.luma.backend.blaze3d.preset.PresetPipelineDescriptor
import sweetie.evaware.luma.backend.blaze3d.preset.VulkanPreset
import sweetie.evaware.luma.uniform.*
import sweetie.evaware.luma.vertex.VertexLayout
import sweetie.evaware.luma.vertex.VertexInputLayout

data class SamplerBinding(val name: String, val unit: Int)
class Program(
    val id: Any,
    val vertexSource: String,
    val fragmentSource: String,
    val layouts: VertexInputLayout,
    private val scheduleClose: ((() -> Unit) -> Unit) = { action -> action() }
) : ProgramHandle {
    constructor(
        id: Any,
        vertexSource: String,
        fragmentSource: String,
        layout: VertexLayout,
        scheduleClose: ((() -> Unit) -> Unit) = { action -> action() }
    ) : this(id, vertexSource, fragmentSource, VertexInputLayout().binding(layout), scheduleClose)

    val layout = layouts.layout(0)
    private var pipelineKeys = LongArray(32)
    private var pipelines = arrayOfNulls<PresetPipeline>(32)
    private var pipelineGenerations = LongArray(32) { Long.MIN_VALUE }
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
    var uboCacheUniforms: ShaderUniforms? = null

    internal val vulkanUniforms = Array<VulkanUniform>(uniformInfos.size) { index ->
        val info = uniformInfos[index]
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
    internal val uniformBlockBytes = run {
        var bytes = 0
        for (index in vulkanUniforms.indices) {
            val uniform = vulkanUniforms[index]
            bytes = Math.addExact(align(bytes, uniform.alignmentBytes), uniform.sizeBytes)
        }
        bytes
    }

    internal fun getOrCreatePipeline(
        preset: VulkanPreset,
        topology: PrimitiveType,
        blendEnabled: Boolean,
        blendFunction: BlendFunction,
        depthEnabled: Boolean,
        depthWrite: Boolean,
        depthFunc: DepthCompare,
        cullEnabled: Boolean,
        hasDepthAttachment: Boolean,
        pipelineGeneration: Long
    ): PresetPipeline {
        val key = pipelineKey(topology, blendEnabled, blendFunction, depthEnabled, depthWrite, depthFunc, cullEnabled, hasDepthAttachment)
        for (index in 0 until pipelineCount) {
            if (pipelineKeys[index] == key) {
                val pipeline = pipelines[index]!!
                if (pipelineGenerations[index] != pipelineGeneration) {
                    compile(preset, pipeline)
                    pipelineGenerations[index] = pipelineGeneration
                }
                return pipeline
            }
        }

        val pipeline = run {
            val descriptor = PresetPipelineDescriptor(
                id = id,
                topology = topology,
                blendEnabled = blendEnabled,
                blendFunction = blendFunction,
                depthEnabled = depthEnabled,
                depthWrite = depthWrite,
                depthFunc = depthFunc,
                cullEnabled = cullEnabled,
                hasDepthAttachment = hasDepthAttachment,
                layouts = layouts,
                attributeNames = attributeNames,
                samplers = samplers,
                hasUniforms = uniformInfos.isNotEmpty()
            )
            val built = preset.createPipeline(descriptor)
            compile(preset, built)
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

    private fun compile(preset: VulkanPreset, pipeline: PresetPipeline): PresetPipeline {
        val compiled = preset.precompilePipeline(pipeline, vertexSource, fragmentSource)
        if (!compiled.isValid) {
            val messages = preset.getLastDebugMessages()
            error("Pipeline compilation failed for $id:\n${messages.joinToString("\n")}")
        }
        return pipeline
    }

    fun precompileDefaults(preset: VulkanPreset, pipelineGeneration: Long) {
        requireOpen()
        for (hasDepthAttachment in booleanArrayOf(false, true)) {
            getOrCreatePipeline(
                preset,
                PrimitiveType.TRIANGLES,
                true,
                BlendFunction.TRANSLUCENT,
                false,
                false,
                DepthCompare.ALWAYS,
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
        uboCacheUniforms = null
        uboCacheFrameId = -1L
    }

    private fun pipelineKey(
        topology: PrimitiveType,
        blendEnabled: Boolean,
        blendFunction: BlendFunction,
        depthEnabled: Boolean,
        depthWrite: Boolean,
        depthFunc: DepthCompare,
        cullEnabled: Boolean,
        hasDepthAttachment: Boolean
    ): Long {
        var key = topology.ordinal.toLong()
        key = (key shl 3) or depthFunc.ordinal.toLong()
        key = (key shl 4) or blendFunction.sourceColor.ordinal.toLong()
        key = (key shl 4) or blendFunction.destinationColor.ordinal.toLong()
        key = (key shl 4) or blendFunction.sourceAlpha.ordinal.toLong()
        key = (key shl 4) or blendFunction.destinationAlpha.ordinal.toLong()
        key = (key shl 3) or blendFunction.colorOp.ordinal.toLong()
        key = (key shl 3) or blendFunction.alphaOp.ordinal.toLong()
        key = (key shl 1) or (if (blendEnabled) 1L else 0L)
        key = (key shl 1) or (if (depthEnabled) 1L else 0L)
        key = (key shl 1) or (if (depthWrite) 1L else 0L)
        key = (key shl 1) or (if (cullEnabled) 1L else 0L)
        key = (key shl 1) or (if (hasDepthAttachment) 1L else 0L)
        return key
    }

    private fun align(value: Int, alignment: Int): Int {
        val remainder = value % alignment
        return if (remainder == 0) value else Math.addExact(value, alignment - remainder)
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
