package io.asy.fragmented.mixin;

import io.asy.fragmented.FlowStateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class FlowStateSlimeMixin {
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void slimeform$mirrorDamage(
            ServerLevel level, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof Slime slime)) {
            return;
        }
        if (!FlowStateManager.isPossessed(slime)) {
            return;
        }
        ServerPlayer player = FlowStateManager.ownerOf(slime);
        if (player == null || !player.isAlive()) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(player.hurtServer(level, source, amount));
    }
}
