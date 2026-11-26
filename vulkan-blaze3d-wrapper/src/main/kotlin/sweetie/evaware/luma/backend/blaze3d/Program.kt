package sweetie.evaware.luma.backend.blaze3d

import sweetie.evaware.luma.LumaNames
import sweetie.evaware.luma.api.BlendFunction
import sweetie.evaware.luma.api.DepthCompare
import sweetie.evaware.luma.api.PrimitiveType
import sweetie.evaware.luma.api.ProgramHandle
import sweetie.evaware.luma.backend.blaze3d.preset.PresetPipeline
import sweetie.evaware.luma.backend.blaze3d.preset.PresetPipelineDescriptor
import sweetie.evaware.luma.backend.blaze3d.preset.VulkanPreset
import sweetie.evaware.luma.shader.translator.ShaderReflection
import sweetie.evaware.luma.uniform.*
import sweetie.evaware.luma.vertex.VertexInputLayout
import sweetie.evaware.luma.vertex.VertexLayout

data class SamplerBinding(val name: String, val unit: Int)

class Program internal constructor(
    val id: Any,
    val vertexSource: String,
    val fragmentSource: String,
    val layouts: VertexInputLayout,
    private val scheduleClose: ((() -> Unit) -> Unit),
    private val retirement: VulkanRetirementQueue?,
    val reflection: ShaderReflection? = null
) : ProgramHandle {
    constructor(
        id: Any,
        vertexSource: String,
        fragmentSource: String,
        layouts: VertexInputLayout,
        reflection: ShaderReflection? = null,
        scheduleClose: ((() -> Unit) -> Unit) = { action -> action() }
    ) : this(id, vertexSource, fragmentSource, layouts, scheduleClose, null, reflection)

    constructor(
        id: Any,
        vertexSource: String,
        fragmentSource: String,
        layout: VertexLayout,
        reflection: ShaderReflection? = null,
        scheduleClose: ((() -> Unit) -> Unit) = { action -> action() }
    ) : this(id, vertexSource, fragmentSource, VertexInputLayout().binding(layout), scheduleClose, null, reflection)

    val layout = layouts.layout(0)
    internal val strideBytes = layout.strideFloats * Float.SIZE_BYTES
    override val vertexStrideBytes: Int get() = strideBytes
    private var pipelineKeys = LongArray(32)
    private var pipelines = arrayOfNulls<PresetPipeline>(32)
    private var pipelineGenerations = LongArray(32) { Long.MIN_VALUE }
    private var pipelineCount = 0
    private var closeScheduled = false
    val uniformInfos: List<UniformInfo> = reflection?.uniforms?.map { UniformInfo(it.name, it.type) }
        ?: parseUniforms(vertexSource, fragmentSource)
    internal val hasUniforms = uniformInfos.isNotEmpty()
    val attributeNames: Map<Int, String> = reflection?.attributes?.let { attrs ->
        val map = HashMap<Int, String>(attrs.size)
        for (index in attrs.indices) {
            val attr = attrs[index]
            map[attr.location] = attr.backendName
        }
        map
    } ?: parseAttributes(vertexSource)
    val samplers: List<String> = reflection?.samplers?.map { it.backendName } ?: run {
        val samplerRegex = Regex("""\b${LumaNames.SAMPLER_PREFIX}\d+\b""")
        (vertexSource.lines() + fragmentSource.lines())
            .flatMap { samplerRegex.findAll(it).map { m -> m.value } }
            .distinct()
    }

    val samplerBindings: List<SamplerBinding> = reflection?.samplers?.map {
        SamplerBinding(it.backendName, it.unit)
    } ?: samplers.map { name ->
        SamplerBinding(name, name.removePrefix(LumaNames.SAMPLER_PREFIX).toIntOrNull() ?: 0)
    }
    internal val samplerBindingArray: Array<SamplerBinding> = samplerBindings.toTypedArray()

    var uboCacheFrameId: Long = -1L
    var uboCacheOffset: Long = 0L
    var uboCacheBytes: Int = 0
    var uboCacheUniforms: ShaderUniforms? = null
    var uboCacheRevision: Int = 0

    internal val uniformLayout = VulkanUniformLayout(uniformInfos)
    internal val uniformBlockBytes: Int get() = uniformLayout.blockBytes

    internal fun getOrCreatePipeline(
        preset: VulkanPreset,
        pipelineKey: Long,
        hasDepthAttachment: Boolean,
        pipelineGeneration: Long
    ): PresetPipeline {
        val fullKey = if (hasDepthAttachment) {
            pipelineKey or PipelineStateKey.DEPTH_ATTACHMENT_BIT
        } else {
            pipelineKey
        }
        val keys = pipelineKeys
        val count = pipelineCount
        for (index in 0 until count) {
            if (keys[index] == fullKey) {
                val pipeline = pipelines[index]!!
                if (pipelineGenerations[index] != pipelineGeneration) {
                    compile(preset, pipeline)
                    pipelineGenerations[index] = pipelineGeneration
                }
                return pipeline
            }
        }

        return createAndCachePipeline(preset, pipelineKey, fullKey, hasDepthAttachment, pipelineGeneration)
    }

    private fun createAndCachePipeline(
        preset: VulkanPreset,
        pipelineKey: Long,
        fullKey: Long,
        hasDepthAttachment: Boolean,
        pipelineGeneration: Long
    ): PresetPipeline {
        val descriptor = PresetPipelineDescriptor(
            id = id,
            topology = PipelineStateKey.unpackTopology(pipelineKey),
            blendEnabled = PipelineStateKey.unpackBlendEnabled(pipelineKey),
            blendFunction = PipelineStateKey.unpackBlendFunction(pipelineKey),
            colorWriteMask = PipelineStateKey.unpackColorWriteMask(pipelineKey),
            depthEnabled = PipelineStateKey.unpackDepthEnabled(pipelineKey),
            depthWrite = PipelineStateKey.unpackDepthWrite(pipelineKey),
            depthFunc = PipelineStateKey.unpackDepthFunc(pipelineKey),
            cullEnabled = PipelineStateKey.unpackCullEnabled(pipelineKey),
            hasDepthAttachment = hasDepthAttachment,
            layouts = layouts,
            attributeNames = attributeNames,
            samplers = samplers,
            hasUniforms = hasUniforms
        )
        val built = preset.createPipeline(descriptor)
        compile(preset, built)

        if (pipelineCount == pipelines.size) {
            pipelineKeys = pipelineKeys.copyOf(pipelineCount shl 1)
            pipelines = pipelines.copyOf(pipelineCount shl 1)
            pipelineGenerations = pipelineGenerations.copyOf(pipelineCount shl 1)
        }
        pipelineKeys[pipelineCount] = fullKey
        pipelines[pipelineCount] = built
        pipelineGenerations[pipelineCount] = pipelineGeneration
        pipelineCount++
        return built
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
        val defaultKey = PipelineStateKey.pack(
            PrimitiveType.TRIANGLES,
            blendEnabled = true,
            blendFunction = BlendFunction.TRANSLUCENT,
            depthEnabled = false,
            depthWrite = false,
            depthFunc = DepthCompare.ALWAYS,
            cullEnabled = false,
            colorWriteMask = PipelineStateKey.DEFAULT_COLOR_WRITE_MASK
        )
        getOrCreatePipeline(preset, defaultKey, false, pipelineGeneration)
        getOrCreatePipeline(preset, defaultKey, true, pipelineGeneration)
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
        var failure: Throwable? = null
        val queue = retirement
        for (index in 0 until pipelineCount) {
            val pipeline = pipelines[index]
            pipelines[index] = null
            pipelineGenerations[index] = Long.MIN_VALUE
            if (pipeline != null) {
                if (queue != null) {
                    queue.defer(pipeline)
                } else {
                    try {
                        pipeline.close()
                    } catch (throwable: Throwable) {
                        if (failure == null) failure = throwable else failure.addSuppressed(throwable)
                    }
                }
            }
        }
        pipelineCount = 0
        uboCacheUniforms = null
        uboCacheFrameId = -1L
        uboCacheRevision = 0
        uniformLayout.clearCache()
        if (failure != null) throw failure
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
