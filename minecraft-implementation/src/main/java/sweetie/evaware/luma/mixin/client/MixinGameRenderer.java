package sweetie.evaware.luma.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.resources.model.ModelManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sweetie.evaware.renderutil.OffscreenDemo;
import sweetie.evaware.renderutil.RenderUtil;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void luma$loadRenderUtils(
        Minecraft minecraft,
        ItemInHandRenderer itemInHandRenderer,
        ModelManager modelManager,
        CallbackInfo callbackInfo
    ) {
        RenderUtil.INSTANCE.load();
    }

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
