package sweetie.evaware.luma.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.resources.model.ModelManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sweetie.evaware.luma.platform.minecraft.MinecraftRenderHooks;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void luma$initializeRenderer(
        Minecraft minecraft,
        ItemInHandRenderer itemInHandRenderer,
        RenderBuffers renderBuffers,
        ModelManager modelManager,
        CallbackInfo callbackInfo
    ) {
        MinecraftRenderHooks.INSTANCE.initialize();
    }
}
