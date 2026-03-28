package sweetie.evaware.luma.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sweetie.evaware.luma.matrix.MatrixControl;
import sweetie.evaware.renderutil.RenderStats;
import sweetie.evaware.renderutil.RenderTest;
import sweetie.evaware.renderutil.RenderUtil;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void luma$loadRenderUtils(
        Minecraft minecraft,
        ItemInHandRenderer itemInHandRenderer,
        RenderBuffers renderBuffers,
        BlockRenderDispatcher blockRenderDispatcher,
        CallbackInfo callbackInfo
    ) {
        RenderUtil.INSTANCE.load();
    }

    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
            shift = At.Shift.AFTER
        )
    )
    private void luma$renderGuiRects(DeltaTracker deltaTracker, boolean tick, CallbackInfo callbackInfo) {
        RenderStats.INSTANCE.beginFrame();
        MatrixControl.INSTANCE.beginGuiFrame();
        RenderTest.INSTANCE.renderGui();
    }
}
