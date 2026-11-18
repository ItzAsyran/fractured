package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeAbsorbManager;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Creeper.class)
public abstract class SlimeCreeperAbsorbMixin {
    @Inject(method = "explodeCreeper", at = @At("HEAD"), cancellable = true)
    private void slimeform$cancelAbsorbedExplosion(CallbackInfo ci) {
        Creeper creeper = (Creeper) (Object) this;
        if (creeper.entityTags().contains(SlimeAbsorbManager.ABSORBED_TAG)) {
            creeper.setSwellDir(-1);
            ci.cancel();
        }
    }

    @Inject(method = "ignite", at = @At("HEAD"), cancellable = true)
    private void slimeform$cancelAbsorbedIgnite(CallbackInfo ci) {
        Creeper creeper = (Creeper) (Object) this;
        if (creeper.entityTags().contains(SlimeAbsorbManager.ABSORBED_TAG)) {
            ci.cancel();
        }
    }
}
