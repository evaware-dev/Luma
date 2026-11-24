package sweetie.evaware.luma.backend.gl

import org.lwjgl.opengl.EXTTextureFilterAnisotropic
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL33
import org.lwjgl.opengl.GL46
import sweetie.evaware.luma.api.SamplerState
import sweetie.evaware.luma.api.TextureFilter
import sweetie.evaware.luma.api.TextureWrap

internal class GlSamplerCache : AutoCloseable {
    private data class Key(val sampler: SamplerState, val mipmap: Boolean)

    // Fast path for common samplers with maxAnisotropy == 1 && maxLod == null (32 combinations)
    private val fastTable = IntArray(32)
    private val samplers = HashMap<Key, Int>()

    fun get(sampler: SamplerState, mipmap: Boolean): Int {
        if (sampler.maxAnisotropy == 1 && sampler.maxLod == null) {
            val slot = fastSlot(sampler, mipmap)
            val existing = fastTable[slot]
            if (existing != 0) return existing
            val created = createOwnedSampler(sampler, mipmap)
            fastTable[slot] = created
            return created
        }
        return samplers.getOrPut(Key(sampler, mipmap)) {
            createOwnedSampler(sampler, mipmap)
        }
    }

    override fun close() {
        for (index in fastTable.indices) {
            val id = fastTable[index]
            if (id != 0) {
                GL33.glDeleteSamplers(id)
                fastTable[index] = 0
            }
        }
        if (samplers.isNotEmpty()) {
            for (id in samplers.values) {
                GL33.glDeleteSamplers(id)
            }
            samplers.clear()
        }
    }

    companion object {
        private fun fastSlot(sampler: SamplerState, mipmap: Boolean): Int {
            var bits = 0
            if (sampler.minFilter == TextureFilter.LINEAR) bits = bits or 1
            if (sampler.magFilter == TextureFilter.LINEAR) bits = bits or 2
            if (sampler.wrapU == TextureWrap.REPEAT) bits = bits or 4
            if (sampler.wrapV == TextureWrap.REPEAT) bits = bits or 8
            if (mipmap) bits = bits or 16
            return bits
        }

        internal fun createOwnedSampler(sampler: SamplerState, mipmap: Boolean): Int {
            val id = GL33.glGenSamplers()
            configureSampler(id, sampler, mipmap)
            return id
        }

        internal fun configureSampler(samplerId: Int, sampler: SamplerState, mipmap: Boolean) {
            val minFilter = if (mipmap) {
                when (sampler.minFilter) {
                    TextureFilter.NEAREST -> GL11.GL_NEAREST_MIPMAP_NEAREST
                    TextureFilter.LINEAR -> GL11.GL_LINEAR_MIPMAP_LINEAR
                }
            } else {
                when (sampler.minFilter) {
                    TextureFilter.NEAREST -> GL11.GL_NEAREST
                    TextureFilter.LINEAR -> GL11.GL_LINEAR
                }
            }
            val magFilter = when (sampler.magFilter) {
                TextureFilter.NEAREST -> GL11.GL_NEAREST
                TextureFilter.LINEAR -> GL11.GL_LINEAR
            }
            val wrapS = when (sampler.wrapU) {
                TextureWrap.CLAMP_TO_EDGE -> GL12.GL_CLAMP_TO_EDGE
                TextureWrap.REPEAT -> GL11.GL_REPEAT
            }
            val wrapT = when (sampler.wrapV) {
                TextureWrap.CLAMP_TO_EDGE -> GL12.GL_CLAMP_TO_EDGE
                TextureWrap.REPEAT -> GL11.GL_REPEAT
            }
            GL33.glSamplerParameteri(samplerId, GL11.GL_TEXTURE_MIN_FILTER, minFilter)
            GL33.glSamplerParameteri(samplerId, GL11.GL_TEXTURE_MAG_FILTER, magFilter)
            GL33.glSamplerParameteri(samplerId, GL11.GL_TEXTURE_WRAP_S, wrapS)
            GL33.glSamplerParameteri(samplerId, GL11.GL_TEXTURE_WRAP_T, wrapT)
            val maxLod = sampler.maxLod
            if (maxLod != null) {
                GL33.glSamplerParameterf(samplerId, GL12.GL_TEXTURE_MAX_LOD, maxLod.toFloat())
            }
            if (sampler.maxAnisotropy > 1) {
                applyAnisotropy(samplerId, sampler.maxAnisotropy)
            }
        }

        private fun applyAnisotropy(samplerId: Int, requested: Int) {
            val caps = try {
                GL.getCapabilities()
            } catch (_: IllegalStateException) {
                return
            }
            when {
                caps.OpenGL46 -> {
                    val max = GL11.glGetFloat(GL46.GL_MAX_TEXTURE_MAX_ANISOTROPY)
                    GL33.glSamplerParameterf(samplerId, GL46.GL_TEXTURE_MAX_ANISOTROPY, requested.toFloat().coerceIn(1f, max))
                }
                caps.GL_EXT_texture_filter_anisotropic -> {
                    val max = GL11.glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT)
                    GL33.glSamplerParameterf(
                        samplerId,
                        EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT,
                        requested.toFloat().coerceIn(1f, max)
                    )
                }
            }
        }
    }
}
