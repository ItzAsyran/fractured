package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class SlimeTargetingMixin {

    @Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
    private void slimeform$noTargetPlayers(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Mob mob
                && target instanceof Player player
                && (player.getTags().contains(SlimeFormMod.SLIME_DORMANT_TAG)
                || (mob instanceof Slime
                && player.getTags().contains(SlimeFormMod.SLIME_FORM_TAG)))) {
            cir.setReturnValue(false);
        }
    }
}
