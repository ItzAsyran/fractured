package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Slime.class)
public abstract class SlimeCombatMixin {
    @Shadow
    protected abstract boolean isDealsDamage();

    @Shadow
    protected abstract void dealDamage(LivingEntity target);

    @Unique
    private int slimeform$attackCooldown;

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void slimeform$doNotDamageActivatedPlayer(Player player, CallbackInfo ci) {
        if (player.getTags().contains(SlimeFormMod.SLIME_FORM_TAG)) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void slimeform$attackCommandedTarget(CallbackInfo ci) {
        Slime slime = (Slime) (Object) this;
        if (slime.level().isClientSide() || !isDealsDamage()) {
            return;
        }

        if (slimeform$attackCooldown > 0) {
            slimeform$attackCooldown--;
        }

        LivingEntity target = slime.getTarget();
        if (target != null && !target.isAlive()) {
            slime.setTarget(null);
            return;
        }

        if (slimeform$attackCooldown == 0
                && target != null
                && slime.isWithinMeleeAttackRange(target)) {
            dealDamage(target);
            slimeform$attackCooldown = 10;

            if (!target.isAlive()) {
                slime.setTarget(null);
            }
        }
    }
}
