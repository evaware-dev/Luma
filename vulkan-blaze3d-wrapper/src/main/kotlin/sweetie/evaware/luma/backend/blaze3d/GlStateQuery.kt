package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.platform.CompareOp
import sweetie.evaware.luma.mixin.accessor.GlStateManagerAccessor
import sweetie.evaware.luma.mixin.accessor.DepthStateAccessor
import sweetie.evaware.luma.mixin.accessor.CullStateAccessor
import sweetie.evaware.luma.mixin.accessor.BooleanStateAccessor

object GlStateQuery {
    val depthEnabled: Boolean
        get() {
            val depth = GlStateManagerAccessor.getDepth() as DepthStateAccessor
            val mode = depth.mode as BooleanStateAccessor
            return mode.enabled
        }

    val depthWrite: Boolean
        get() {
            val depth = GlStateManagerAccessor.getDepth() as DepthStateAccessor
            return depth.mask
        }

    val depthFunc: CompareOp
        get() {
            val depth = GlStateManagerAccessor.getDepth() as DepthStateAccessor
            return when (depth.func) {
                0x200 -> CompareOp.NEVER_PASS
                0x201 -> CompareOp.LESS_THAN
                0x202 -> CompareOp.EQUAL
                0x203 -> CompareOp.LESS_THAN_OR_EQUAL
                0x204 -> CompareOp.GREATER_THAN
                0x205 -> CompareOp.NOT_EQUAL
                0x206 -> CompareOp.GREATER_THAN_OR_EQUAL
                0x207 -> CompareOp.ALWAYS_PASS
                else -> CompareOp.LESS_THAN_OR_EQUAL
            }
        }

    val cullEnabled: Boolean
        get() {
            val cull = GlStateManagerAccessor.getCull() as CullStateAccessor
            val enable = cull.enable as BooleanStateAccessor
            return enable.enabled
        }
}