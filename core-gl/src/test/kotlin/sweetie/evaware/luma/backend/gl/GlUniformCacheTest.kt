package sweetie.evaware.luma.backend.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import sweetie.evaware.luma.uniform.Float1Uniform
import sweetie.evaware.luma.uniform.Float4Uniform
import sweetie.evaware.luma.uniform.ShaderUniforms

class GlUniformCacheTest {
    @Test
    fun skipsCleanDrawsAndUploadsOnlyDirtyUniforms() {
        val locations = mapOf("uAlpha" to 1, "uColor" to 2, "uUnused" to -1)
        val cache = GlUniformCache { locations[it] ?: -1 }

        val uniforms = ShaderUniforms()
        val alpha = uniforms.float1("uAlpha")
        val color = uniforms.float4("uColor")
        uniforms.float1("uUnused")
        uniforms.float1(alpha, 0.5f)
        uniforms.vec4(color, 1f, 0f, 0f, 1f)

        val uploaded = ArrayList<Pair<String, Int>>()
        cache.uploadWith(uniforms) { name, location, _ -> uploaded.add(name to location) }

        assertEquals(listOf("uAlpha" to 1, "uColor" to 2), uploaded)
        assertFalse(alpha.isDirty)
        assertFalse(color.isDirty)

        // Clean draw: O(1) revision skip
        uploaded.clear()
        cache.uploadWith(uniforms) { name, location, _ -> uploaded.add(name to location) }
        assertEquals(emptyList(), uploaded)

        // Mutate only uAlpha: only uAlpha uploads
        uniforms.float1(alpha, 0.75f)
        cache.uploadWith(uniforms) { name, location, _ -> uploaded.add(name to location) }
        assertEquals(listOf("uAlpha" to 1), uploaded)
        assertFalse(alpha.isDirty)

        // Clean again
        uploaded.clear()
        cache.uploadWith(uniforms) { name, location, _ -> uploaded.add(name to location) }
        assertEquals(emptyList(), uploaded)
    }

    @Test
    fun alternatingUniformSetsReuploadsValuesOnSwitchEvenWhenClean() {
        val cache = GlUniformCache { if (it == "uColor") 4 else -1 }

        val first = ShaderUniforms()
        val firstColor = first.float4("uColor")
        first.vec4(firstColor, 1f, 0f, 0f, 1f)

        val second = ShaderUniforms()
        val secondColor = second.float4("uColor")
        second.vec4(secondColor, 0f, 1f, 0f, 1f)

        val uploadedRedValues = ArrayList<Float>()
        cache.uploadWith(first) { _, _, handle -> uploadedRedValues.add((handle as Float4Uniform).first) }
        cache.uploadWith(second) { _, _, handle -> uploadedRedValues.add((handle as Float4Uniform).first) }
        // Switching back to 'first' must re-upload even though firstColor.isDirty is false
        assertFalse(firstColor.isDirty)
        cache.uploadWith(first) { _, _, handle -> uploadedRedValues.add((handle as Float4Uniform).first) }
        // Repeated draw with 'first' is clean and skips
        cache.uploadWith(first) { _, _, handle -> uploadedRedValues.add((handle as Float4Uniform).first) }

        assertEquals(listOf(1f, 0f, 1f), uploadedRedValues)
    }

    @Test
    fun discoversUniformRegisteredAfterFirstDraw() {
        val locations = mapOf("uFirst" to 1, "uSecond" to 2)
        val cache = GlUniformCache { locations[it] ?: -1 }

        val uniforms = ShaderUniforms()
        val first = uniforms.float1("uFirst")
        uniforms.float1(first, 1f)

        val uploaded = ArrayList<Pair<String, Float>>()
        cache.uploadWith(uniforms) { name, _, handle ->
            uploaded.add(name to (handle as Float1Uniform).value)
        }
        assertEquals(listOf("uFirst" to 1f), uploaded)

        val second = uniforms.float1("uSecond")
        uniforms.float1(second, 2f)
        uploaded.clear()
        cache.uploadWith(uniforms) { name, _, handle ->
            uploaded.add(name to (handle as Float1Uniform).value)
        }
        assertEquals(listOf("uSecond" to 2f), uploaded)
    }
}
