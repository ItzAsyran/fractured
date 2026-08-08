package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormMod;
import io.asy.fragmented.FlowStateManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(Mob.class)
public abstract class SlimeMobTargetMixin {
    @Unique
    private UUID slimeform$lastRejectedTarget;

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void slimeform$rejectSlimeFormPlayer(LivingEntity target, CallbackInfo ci) {
        if ((Object) this instanceof Mob mob
                && target instanceof Player player
                && (player.getTags().contains(SlimeFormMod.SLIME_DORMANT_TAG)
                || FlowStateManager.isPossessed(player)
                || (mob instanceof Slime
                && player.getTags().contains(SlimeFormMod.SLIME_FORM_TAG)))) {
            if (!player.getUUID().equals(slimeform$lastRejectedTarget)) {
                slimeform$lastRejectedTarget = player.getUUID();
                SlimeFormMod.LOGGER.warn(
                        "[slimeform] Blocked mob {} from targeting protected player {} ({}) in setTarget",
                        mob.getUUID(), player.getName().getString(), player.getUUID());
            }
            ci.cancel();
            ((Mob) (Object) this).setTarget(null);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void slimeform$clearExistingSlimeFormPlayerTarget(CallbackInfo ci) {
        Mob mob = (Mob) (Object) this;
        if (mob.getTarget() instanceof Player player
                && (player.getTags().contains(SlimeFormMod.SLIME_DORMANT_TAG)
                || FlowStateManager.isPossessed(player)
                || (mob instanceof Slime
                && player.getTags().contains(SlimeFormMod.SLIME_FORM_TAG)))) {
            if (!player.getUUID().equals(slimeform$lastRejectedTarget)) {
                slimeform$lastRejectedTarget = player.getUUID();
                SlimeFormMod.LOGGER.warn(
                        "[slimeform] Clearing existing target: mob {} was targeting protected player {} ({})",
                        mob.getUUID(), player.getName().getString(), player.getUUID());
            }
            mob.setTarget(null);
        }
    }
}
