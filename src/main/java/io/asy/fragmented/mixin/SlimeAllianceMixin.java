package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class SlimeAllianceMixin {
    /** Treat slimes and players as allies, like members of the same team. */
    @Inject(method = "isAlliedTo", at = @At("HEAD"), cancellable = true)
    private void slimeform$slimesAreAllied(Entity other, CallbackInfoReturnable<Boolean> cir) {
        if (((Object) this instanceof Slime && other instanceof Player otherPlayer
                        && otherPlayer.getTags().contains(SlimeFormMod.SLIME_FORM_TAG))
                || ((Object) this instanceof Player thisPlayer
                        && thisPlayer.getTags().contains(SlimeFormMod.SLIME_FORM_TAG)
                        && other instanceof Slime)) {
            cir.setReturnValue(true);
        }
    }
}
