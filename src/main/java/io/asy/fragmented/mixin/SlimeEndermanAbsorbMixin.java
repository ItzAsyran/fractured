package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeAbsorbManager;
import net.minecraft.world.entity.monster.Enderman;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Enderman.class)
public abstract class SlimeEndermanAbsorbMixin {
    @Inject(method = "teleport()Z", at = @At("HEAD"), cancellable = true)
    private void slimeform$cancelAbsorbedTeleport(CallbackInfoReturnable<Boolean> cir) {
        Enderman enderman = (Enderman) (Object) this;
        if (enderman.entityTags().contains(SlimeAbsorbManager.ABSORBED_TAG)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "teleport(DDD)Z", at = @At("HEAD"), cancellable = true)
    private void slimeform$cancelAbsorbedTeleportCoords(double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        Enderman enderman = (Enderman) (Object) this;
        if (enderman.entityTags().contains(SlimeAbsorbManager.ABSORBED_TAG)) {
            cir.setReturnValue(false);
        }
    }
}
