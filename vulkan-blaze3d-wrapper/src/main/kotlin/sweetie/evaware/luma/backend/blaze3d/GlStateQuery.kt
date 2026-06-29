package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.platform.CompareOp

object GlStateQuery {
    val depthEnabled: Boolean
        get() = GlStateManager.DEPTH.mode.enabled

    val depthWrite: Boolean
        get() = GlStateManager.DEPTH.mask

    val depthFunc: CompareOp
        get() = when (GlStateManager.DEPTH.func) {
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

    val cullEnabled: Boolean
        get() = GlStateManager.CULL.enable.enabled
}