package sweetie.evaware.luma.backend.blaze3d.preset

import java.util.concurrent.ConcurrentHashMap

object VulkanPresets {
    private val presets = ConcurrentHashMap<String, VulkanPreset>()
    private var activePreset: VulkanPreset? = null

    var active: VulkanPreset
        get() = activePreset ?: error("VulkanPreset has not been initialized")
        set(value) {
            activePreset = value
            register(value.name, value)
        }

    val isInitialized: Boolean
        get() = activePreset != null

    fun register(name: String, preset: VulkanPreset) {
        presets[name] = preset
    }

    fun get(name: String): VulkanPreset? = presets[name]
}
