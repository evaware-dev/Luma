package sweetie.evaware.luma.vulkan

import sweetie.evaware.luma.gpu.ManagedGpuRenderHost

interface VulkanRenderHost : ManagedGpuRenderHost {
    fun vulkanRuntime(): VulkanRuntime = managedGpuRuntime()
}
