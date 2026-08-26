package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.entity.Entity;

@Mixin(Entity.class)
public abstract class SlimeEntityHighlightGlowMixin {
    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void slimeform$highlightGlow(CallbackInfoReturnable<Boolean> cir) {
        if (SlimeFormClient.highlightColor((Entity) (Object) this) >= 0) {
            cir.setReturnValue(true);
        }
    }
}
