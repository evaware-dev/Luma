package sweetie.evaware.luma.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClipMaskAndSamplerTest {
    @Test
    fun samplerStatePresetsAndValidation() {
        assertEquals(TextureFilter.LINEAR, SamplerState.LINEAR_CLAMP.minFilter)
        assertEquals(TextureFilter.LINEAR, SamplerState.LINEAR_CLAMP.magFilter)
        assertEquals(TextureWrap.CLAMP_TO_EDGE, SamplerState.LINEAR_CLAMP.wrapU)
        assertEquals(TextureWrap.CLAMP_TO_EDGE, SamplerState.LINEAR_CLAMP.wrapV)

        assertEquals(TextureFilter.NEAREST, SamplerState.NEAREST_CLAMP.minFilter)
        assertEquals(TextureWrap.REPEAT, SamplerState.LINEAR_REPEAT.wrapU)
        assertEquals(TextureWrap.REPEAT, SamplerState.NEAREST_REPEAT.wrapV)

        assertFailsWith<IllegalArgumentException> { SamplerState(maxAnisotropy = 0) }
        assertFailsWith<IllegalArgumentException> { SamplerState(maxLod = -1.0) }
        assertFailsWith<IllegalArgumentException> { SamplerState(maxLod = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { SamplerState(maxLod = Double.POSITIVE_INFINITY) }
    }

    @Test
    fun clipMaskScopedWriteInsideOutsideAndRestoresPriorState() {
        val state = FakeStateBackend()
        // Configure non-default prior state
        state.colorMask(true, false, true, false)
        state.depthTest(true)
        state.depthWrite(true)
        state.depthCompare(DepthCompare.GREATER_OR_EQUAL)

        ClipMask.write(clear = true, backend = state) {
            assertEquals(listOf(1.0), state.clearedDepths)
            assertFalse(state.colorMaskRed)
            assertFalse(state.colorMaskGreen)
            assertFalse(state.colorMaskBlue)
            assertFalse(state.colorMaskAlpha)
            assertTrue(state.isDepthTestEnabled)
            assertTrue(state.isDepthWriteEnabled)
            assertEquals(DepthCompare.ALWAYS, state.currentDepthCompare)
        }

        // Exact prior state restored
        assertTrue(state.colorMaskRed)
        assertFalse(state.colorMaskGreen)
        assertTrue(state.colorMaskBlue)
        assertFalse(state.colorMaskAlpha)
        assertTrue(state.isDepthTestEnabled)
        assertTrue(state.isDepthWriteEnabled)
        assertEquals(DepthCompare.GREATER_OR_EQUAL, state.currentDepthCompare)

        ClipMask.inside(backend = state) {
            assertTrue(state.colorMaskRed && state.colorMaskGreen && state.colorMaskBlue && state.colorMaskAlpha)
            assertTrue(state.isDepthTestEnabled)
            assertFalse(state.isDepthWriteEnabled)
            assertEquals(DepthCompare.EQUAL, state.currentDepthCompare)
        }

        ClipMask.outside(backend = state) {
            assertTrue(state.colorMaskRed && state.colorMaskGreen && state.colorMaskBlue && state.colorMaskAlpha)
            assertTrue(state.isDepthTestEnabled)
            assertFalse(state.isDepthWriteEnabled)
            assertEquals(DepthCompare.LESS, state.currentDepthCompare)
        }

        // Prior state still restored even after exception
        assertFailsWith<IllegalStateException> {
            ClipMask.clip(
                mode = ClipMode.INSIDE,
                clear = false,
                backend = state,
                mask = { assertFalse(state.colorMaskRed) },
                content = { error("boom") }
            )
        }
        assertEquals(1, state.clearedDepths.size)
        assertTrue(state.colorMaskRed)
        assertFalse(state.colorMaskGreen)
        assertTrue(state.colorMaskBlue)
        assertFalse(state.colorMaskAlpha)
        assertEquals(DepthCompare.GREATER_OR_EQUAL, state.currentDepthCompare)
    }

    @Test
    fun clipMaskImperativeSessionRestoresOriginalState() {
        val state = FakeStateBackend()
        state.colorMask(false, true, false, true)
        state.depthTest(false)
        state.depthWrite(false)
        state.depthCompare(DepthCompare.LESS_OR_EQUAL)

        ClipMask.beginWrite(clear = true, backend = state)
        assertFalse(state.colorMaskGreen)
        assertEquals(DepthCompare.ALWAYS, state.currentDepthCompare)

        ClipMask.beginTest(ClipMode.INSIDE, backend = state)
        assertTrue(state.colorMaskRed)
        assertEquals(DepthCompare.EQUAL, state.currentDepthCompare)

        ClipMask.beginTest(ClipMode.OUTSIDE, backend = state)
        assertEquals(DepthCompare.LESS, state.currentDepthCompare)

        ClipMask.end(backend = state)
        assertFalse(state.colorMaskRed)
        assertTrue(state.colorMaskGreen)
        assertFalse(state.colorMaskBlue)
        assertTrue(state.colorMaskAlpha)
        assertFalse(state.isDepthTestEnabled)
        assertFalse(state.isDepthWriteEnabled)
        assertEquals(DepthCompare.LESS_OR_EQUAL, state.currentDepthCompare)

        assertFailsWith<IllegalStateException> { ClipMask.end(backend = state) }
    }

    private class FakeStateBackend : StateBackend {
        val clearedDepths = ArrayList<Double>()
        override var isDepthTestEnabled = false
        override var isDepthWriteEnabled = false
        override var currentDepthCompare = DepthCompare.ALWAYS
        override var colorMaskRed = true
        override var colorMaskGreen = true
        override var colorMaskBlue = true
        override var colorMaskAlpha = true

        override fun colorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) {
            colorMaskRed = red
            colorMaskGreen = green
            colorMaskBlue = blue
            colorMaskAlpha = alpha
        }

        override fun depthTest(enabled: Boolean) { isDepthTestEnabled = enabled }
        override fun depthWrite(enabled: Boolean) { isDepthWriteEnabled = enabled }
        override fun depthCompare(compare: DepthCompare) { currentDepthCompare = compare }
        override fun clearDepth(depth: Double) { clearedDepths.add(depth) }
    }
}
