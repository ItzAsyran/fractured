package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormClient;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class SlimeEntityHighlightColorMixin {
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void slimeform$highlightColor(CallbackInfoReturnable<Integer> cir) {
        int color = SlimeFormClient.highlightColor((Entity) (Object) this);
        if (color >= 0) {
            cir.setReturnValue(color);
        }
    }
}
