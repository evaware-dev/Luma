package sweetie.evaware.luma.mixin.client;

import com.mojang.blaze3d.vulkan.VulkanDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sweetie.evaware.renderutil.RenderUtil;

@Mixin(VulkanDevice.class)
public class MixinVulkanDevice {
    @Inject(method = "clearPipelineCache", at = @At("HEAD"))
    private void luma$onClearPipelineCache(CallbackInfo callbackInfo) {
        RenderUtil.INSTANCE.close();
    }
}
