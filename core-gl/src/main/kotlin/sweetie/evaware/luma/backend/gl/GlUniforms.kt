package sweetie.evaware.luma.backend.gl

import java.nio.FloatBuffer
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL20
import sweetie.evaware.luma.uniform.*

internal class GlUniformCache(
    private val resolveLocation: (String) -> Int
) {
    private var names: Array<String> = EMPTY_NAMES
    private var locations: IntArray = EMPTY_LOCATIONS
    private var cachedIndices: IntArray = EMPTY_INDICES
    private var resolvedHandles: Array<UniformHandle?> = EMPTY_HANDLES
    private var matrixFloatBuffer: FloatBuffer? = null
    private var lastUniforms: ShaderUniforms? = null
    private var lastEntryCount: Int = -1
    private var lastRevision: Int = 0
    private var initialized: Boolean = false

    fun isClean(uniforms: ShaderUniforms): Boolean =
        lastUniforms === uniforms && lastRevision == uniforms.registry.revision

    fun upload(uniforms: ShaderUniforms) {
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

        val locs = locations
        val handles = resolvedHandles
        val forceUpload = !sameUniforms
        for (index in locs.indices) {
            val handle = handles[index] ?: continue
            if (forceUpload || handle.isDirty) {
                uploadHandleDirect(locs[index], handle)
                handle.isDirty = false
            }
        }

        lastUniforms = uniforms
        lastEntryCount = entries.size
        lastRevision = currentRevision
    }

    private fun uploadHandleDirect(location: Int, handle: UniformHandle) {
        when (handle) {
            is Float1Uniform -> GL20.glUniform1f(location, handle.value)
            is Float2Uniform -> GL20.glUniform2f(location, handle.first, handle.second)
            is Float3Uniform -> GL20.glUniform3f(location, handle.first, handle.second, handle.third)
            is Float4Uniform -> GL20.glUniform4f(location, handle.first, handle.second, handle.third, handle.fourth)
            is Int1Uniform -> GL20.glUniform1i(location, handle.value)
            is Mat4Uniform -> {
                val buf = matrixFloatBuffer ?: BufferUtils.createFloatBuffer(16).also { matrixFloatBuffer = it }
                buf.clear()
                handle.value.get(buf)
                GL20.glUniformMatrix4fv(location, false, buf)
            }
        }
    }

    internal inline fun uploadWith(
        uniforms: ShaderUniforms,
        emit: (String, Int, UniformHandle) -> Unit
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

        val itemNames = names
        val locs = locations
        val handles = resolvedHandles
        val forceUpload = !sameUniforms
        for (index in locs.indices) {
            val handle = handles[index] ?: continue
            if (forceUpload || handle.isDirty) {
                emit(itemNames[index], locs[index], handle)
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
                val builtNames = ArrayList<String>(entries.size)
                val builtLocs = ArrayList<Int>(entries.size)
                val builtIndices = ArrayList<Int>(entries.size)
                val builtHandles = ArrayList<UniformHandle?>(entries.size)
                for (index in entries.indices) {
                    val entry = entries[index]
                    val location = resolveLocation(entry.name)
                    if (location >= 0) {
                        builtNames.add(entry.name)
                        builtLocs.add(location)
                        builtIndices.add(index)
                        builtHandles.add(entry.handle)
                    }
                }
                if (builtNames.isNotEmpty()) {
                    names = builtNames.toTypedArray()
                    locations = IntArray(builtLocs.size) { builtLocs[it] }
                    cachedIndices = IntArray(builtIndices.size) { builtIndices[it] }
                    resolvedHandles = builtHandles.toTypedArray()
                }
            }
            initialized = true
            return
        }

        val itemNames = names
        val indices = cachedIndices
        var handles = resolvedHandles
        if (handles.size != itemNames.size) {
            handles = arrayOfNulls(itemNames.size)
            resolvedHandles = handles
        }
        var matchedActive = 0
        for (i in itemNames.indices) {
            val handle = lookupHandle(entries, itemNames[i], indices, i)
            handles[i] = handle
            if (handle != null) matchedActive++
        }

        if (entries.size > matchedActive) {
            var extraNames: ArrayList<String>? = null
            var extraLocs: ArrayList<Int>? = null
            var extraIndices: ArrayList<Int>? = null
            var extraHandles: ArrayList<UniformHandle?>? = null
            for (entryIndex in entries.indices) {
                val entry = entries[entryIndex]
                var found = false
                for (itemIndex in itemNames.indices) {
                    if (itemNames[itemIndex] == entry.name) {
                        found = true
                        break
                    }
                }
                if (!found) {
                    val location = resolveLocation(entry.name)
                    if (location >= 0) {
                        if (extraNames == null) {
                            val cap = itemNames.size + 4
                            extraNames = ArrayList<String>(cap).apply { for (k in itemNames.indices) add(itemNames[k]) }
                            extraLocs = ArrayList<Int>(cap).apply { for (k in locations.indices) add(locations[k]) }
                            extraIndices = ArrayList<Int>(cap).apply { for (k in indices.indices) add(indices[k]) }
                            extraHandles = ArrayList<UniformHandle?>(cap).apply { for (k in handles.indices) add(handles[k]) }
                        }
                        extraNames.add(entry.name)
                        extraLocs!!.add(location)
                        extraIndices!!.add(entryIndex)
                        extraHandles!!.add(entry.handle)
                    }
                }
            }
            if (extraNames != null) {
                names = extraNames.toTypedArray()
                locations = IntArray(extraLocs!!.size) { extraLocs[it] }
                cachedIndices = IntArray(extraIndices!!.size) { extraIndices[it] }
                resolvedHandles = extraHandles!!.toTypedArray()
            }
        }
    }

    private fun lookupHandle(
        entries: ArrayList<UniformRegistry.Entry>,
        name: String,
        indices: IntArray,
        slot: Int
    ): UniformHandle? {
        val cached = indices[slot]
        if (cached in entries.indices && entries[cached].name == name) {
            return entries[cached].handle
        }
        for (i in entries.indices) {
            if (entries[i].name == name) {
                indices[slot] = i
                return entries[i].handle
            }
        }
        return null
    }

    private companion object {
        val EMPTY_NAMES = emptyArray<String>()
        val EMPTY_LOCATIONS = IntArray(0)
        val EMPTY_INDICES = IntArray(0)
        val EMPTY_HANDLES = emptyArray<UniformHandle?>()
    }
}
