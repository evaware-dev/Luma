package sweetie.evaware.luma.mixin.accessor;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GlStateManager.class)
public interface GlStateManagerAccessor {
    @Accessor("DEPTH")
    static Object getDepth() {
        throw new UnsupportedOperationException();
    }

    @Accessor("CULL")
    static Object getCull() {
        throw new UnsupportedOperationException();
    }
}
