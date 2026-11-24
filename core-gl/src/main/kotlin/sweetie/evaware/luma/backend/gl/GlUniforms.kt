package sweetie.evaware.luma.backend.gl

import org.joml.Matrix4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL20
import sweetie.evaware.luma.uniform.*

abstract class GlUniform(name: String) : UniformBinding(name) {
    abstract fun upload(location: Int, handle: UniformHandle?)

    fun upload(location: Int, uniforms: ShaderUniforms) {
        upload(location, getHandle(uniforms))
    }
}

class GlFloat1Uniform(name: String) : GlUniform(name) {
    override fun upload(location: Int, handle: UniformHandle?) {
        val typed = handle as? Float1Uniform
        GL20.glUniform1f(location, typed?.value ?: 0f)
    }
}

class GlFloat2Uniform(name: String) : GlUniform(name) {
    override fun upload(location: Int, handle: UniformHandle?) {
        val typed = handle as? Float2Uniform
        if (typed != null) {
            GL20.glUniform2f(location, typed.first, typed.second)
        } else {
            GL20.glUniform2f(location, 0f, 0f)
        }
    }
}

class GlFloat3Uniform(name: String) : GlUniform(name) {
    override fun upload(location: Int, handle: UniformHandle?) {
        val typed = handle as? Float3Uniform
        if (typed != null) {
            GL20.glUniform3f(location, typed.first, typed.second, typed.third)
        } else {
            GL20.glUniform3f(location, 0f, 0f, 0f)
        }
    }
}

class GlFloat4Uniform(name: String) : GlUniform(name) {
    override fun upload(location: Int, handle: UniformHandle?) {
        val typed = handle as? Float4Uniform
        if (typed != null) {
            GL20.glUniform4f(location, typed.first, typed.second, typed.third, typed.fourth)
        } else {
            GL20.glUniform4f(location, 0f, 0f, 0f, 0f)
        }
    }
}

class GlInt1Uniform(name: String) : GlUniform(name) {
    override fun upload(location: Int, handle: UniformHandle?) {
        val typed = handle as? Int1Uniform
        GL20.glUniform1i(location, typed?.value ?: 0)
    }
}

private val IDENTITY_MATRIX = Matrix4f()

class GlMat4Uniform(name: String) : GlUniform(name) {
    private val matrixFloatBuffer = BufferUtils.createFloatBuffer(16)

    override fun upload(location: Int, handle: UniformHandle?) {
        val typed = handle as? Mat4Uniform
        val matrix = typed?.value ?: IDENTITY_MATRIX
        matrixFloatBuffer.clear()
        matrix.get(matrixFloatBuffer)
        GL20.glUniformMatrix4fv(location, false, matrixFloatBuffer)
    }
}

class PreparedGlUniform(val uniform: GlUniform, val location: Int)

internal class GlUniformCache(
    private val resolveLocation: (String) -> Int
) {
    private var prepared: Array<PreparedGlUniform> = EMPTY_PREPARED
    private var resolvedHandles: Array<UniformHandle?> = EMPTY_HANDLES
    private var lastUniforms: ShaderUniforms? = null
    private var lastEntryCount: Int = -1
    private var lastRevision: Int = 0
    private var initialized: Boolean = false

    fun getPreparedUniforms(uniforms: ShaderUniforms): List<PreparedGlUniform> {
        ensureResolved(uniforms)
        return prepared.asList()
    }

    fun isClean(uniforms: ShaderUniforms): Boolean =
        lastUniforms === uniforms && lastRevision == uniforms.registry.revision

    fun upload(uniforms: ShaderUniforms) {
        uploadWith(uniforms) { uniform, location, handle ->
            uniform.upload(location, handle)
        }
    }

    internal inline fun uploadWith(
        uniforms: ShaderUniforms,
        emit: (GlUniform, Int, UniformHandle?) -> Unit
    ) {
        val registry = uniforms.registry
        val currentRevision = registry.revision
        val sameUniforms = lastUniforms === uniforms
        if (sameUniforms && lastRevision == currentRevision) {
            return
        }

        val entries = registry.entries
        if (!sameUniforms || !initialized || lastEntryCount != entries.size) {
            ensureResolved(uniforms)
        }

        val items = prepared
        val handles = resolvedHandles
        val forceUpload = !sameUniforms
        for (index in items.indices) {
            val handle = handles[index] ?: continue
            if (forceUpload || handle.isDirty) {
                val item = items[index]
                emit(item.uniform, item.location, handle)
                handle.isDirty = false
            }
        }

        lastUniforms = uniforms
        lastEntryCount = entries.size
        lastRevision = currentRevision
    }

    private fun ensureResolved(uniforms: ShaderUniforms) {
        val entries = uniforms.registry.entries
        if (!initialized) {
            if (entries.isNotEmpty()) {
                val built = ArrayList<PreparedGlUniform>(entries.size)
                val handles = ArrayList<UniformHandle?>(entries.size)
                for (index in entries.indices) {
                    val entry = entries[index]
                    val location = resolveLocation(entry.name)
                    if (location >= 0) {
                        built.add(PreparedGlUniform(createUniform(entry), location))
                        handles.add(entry.handle)
                    }
                }
                if (built.isNotEmpty()) {
                    prepared = built.toTypedArray()
                    resolvedHandles = handles.toTypedArray()
                }
            }
            initialized = true
            return
        }

        val items = prepared
        var handles = resolvedHandles
        if (handles.size != items.size) {
            handles = arrayOfNulls(items.size)
            resolvedHandles = handles
        }
        var matchedActive = 0
        for (index in items.indices) {
            val handle = items[index].uniform.getHandle(uniforms)
            handles[index] = handle
            if (handle != null) matchedActive++
        }

        if (entries.size > matchedActive) {
            var extraPrepared: ArrayList<PreparedGlUniform>? = null
            var extraHandles: ArrayList<UniformHandle?>? = null
            for (entryIndex in entries.indices) {
                val entry = entries[entryIndex]
                var found = false
                for (itemIndex in items.indices) {
                    if (items[itemIndex].uniform.name == entry.name) {
                        found = true
                        break
                    }
                }
                if (!found) {
                    val location = resolveLocation(entry.name)
                    if (location >= 0) {
                        if (extraPrepared == null) {
                            extraPrepared = ArrayList<PreparedGlUniform>(items.size + 4).apply {
                                for (i in items.indices) add(items[i])
                            }
                            extraHandles = ArrayList<UniformHandle?>(items.size + 4).apply {
                                for (i in handles.indices) add(handles[i])
                            }
                        }
                        extraPrepared.add(PreparedGlUniform(createUniform(entry), location))
                        extraHandles!!.add(entry.handle)
                    }
                }
            }
            if (extraPrepared != null) {
                prepared = extraPrepared.toTypedArray()
                resolvedHandles = extraHandles!!.toTypedArray()
            }
        }
    }

    private fun createUniform(entry: UniformRegistry.Entry): GlUniform = when (entry.handle) {
        is Float1Uniform -> GlFloat1Uniform(entry.name)
        is Float2Uniform -> GlFloat2Uniform(entry.name)
        is Float3Uniform -> GlFloat3Uniform(entry.name)
        is Float4Uniform -> GlFloat4Uniform(entry.name)
        is Int1Uniform -> GlInt1Uniform(entry.name)
        is Mat4Uniform -> GlMat4Uniform(entry.name)
    }

    private companion object {
        val EMPTY_PREPARED = emptyArray<PreparedGlUniform>()
        val EMPTY_HANDLES = emptyArray<UniformHandle?>()
    }
}
