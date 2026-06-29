package sweetie.evaware.luma.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlStateManager$BooleanState")
public interface BooleanStateAccessor {
    @Accessor("enabled")
    boolean getEnabled();
}
