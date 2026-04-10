package sweetie.evaware.luma.mixin.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sweetie.evaware.luma.platform.minecraft.MinecraftRenderHooks;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Inject(method = "close", at = @At("TAIL"))
    private void luma$closeRenderer(CallbackInfo callbackInfo) {
        MinecraftRenderHooks.INSTANCE.close();
    }
}
