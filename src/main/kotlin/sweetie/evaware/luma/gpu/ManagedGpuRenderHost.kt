package sweetie.evaware.luma.gpu

import sweetie.evaware.luma.vulkan.VulkanRuntime

interface ManagedGpuRenderHost {
    fun managedGpuRuntime(): VulkanRuntime
}
