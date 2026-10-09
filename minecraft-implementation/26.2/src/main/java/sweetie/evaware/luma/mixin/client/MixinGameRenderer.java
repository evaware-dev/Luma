package sweetie.evaware.luma.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sweetie.evaware.renderutil.OffscreenDemo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V",
            shift = At.Shift.AFTER
        )
    )
    private void luma$renderGuiRects(DeltaTracker deltaTracker, boolean tick, CallbackInfo callbackInfo) {
        OffscreenDemo.INSTANCE.render();
    }
}
