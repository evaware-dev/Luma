package sweetie.evaware.luma.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlStateManager$CullState")
public interface CullStateAccessor {
    @Accessor("enable")
    Object getEnable();
}
