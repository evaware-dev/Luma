package sweetie.evaware.luma.backend.blaze3d

import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.platform.CompareOp

object GlStateQuery {
    private val depthField = GlStateManager::class.java.getDeclaredField("DEPTH").apply { isAccessible = true }
    private val cullField = GlStateManager::class.java.getDeclaredField("CULL").apply { isAccessible = true }

    private val depthStateClass = Class.forName("com.mojang.blaze3d.opengl.GlStateManager\$DepthState")
    private val cullStateClass = Class.forName("com.mojang.blaze3d.opengl.GlStateManager\$CullState")
    private val booleanStateClass = Class.forName("com.mojang.blaze3d.opengl.GlStateManager\$BooleanState")

    private val depthModeField = depthStateClass.getDeclaredField("mode").apply { isAccessible = true }
    private val depthMaskField = depthStateClass.getDeclaredField("mask").apply { isAccessible = true }
    private val depthFuncField = depthStateClass.getDeclaredField("func").apply { isAccessible = true }

    private val cullEnableField = cullStateClass.getDeclaredField("enable").apply { isAccessible = true }
    private val booleanStateEnabledField = booleanStateClass.getDeclaredField("enabled").apply { isAccessible = true }

    val depthEnabled: Boolean
        get() {
            val depth = depthField.get(null)
            val mode = depthModeField.get(depth)
            return booleanStateEnabledField.getBoolean(mode)
        }

    val depthWrite: Boolean
        get() {
            val depth = depthField.get(null)
            return depthMaskField.getBoolean(depth)
        }

    val depthFunc: CompareOp
        get() {
            val depth = depthField.get(null)
            return when (depthFuncField.getInt(depth)) {
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
            val cull = cullField.get(null)
            val enable = cullEnableField.get(cull)
            return booleanStateEnabledField.getBoolean(enable)
        }
}