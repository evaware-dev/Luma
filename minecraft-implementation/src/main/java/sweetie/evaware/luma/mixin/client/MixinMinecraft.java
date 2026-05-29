package sweetie.evaware.luma.mixin.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sweetie.evaware.renderutil.RenderUtil;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Inject(method = "close", at = @At("TAIL"))
    private void luma$closeRenderUtil(CallbackInfo callbackInfo) {
        RenderUtil.INSTANCE.close();
    }
}
