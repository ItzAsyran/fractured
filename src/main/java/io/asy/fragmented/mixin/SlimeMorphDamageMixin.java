package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormPhaseableBlocks;
import io.asy.fragmented.SlimeFormPhaseDebug;
import io.asy.fragmented.SlimeFormState;
import io.asy.fragmented.SlimeMorphManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class SlimeMorphDamageMixin {
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void slimeform$forwardMorphDamage(
            ServerLevel level, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> callback) {
        if (!((Object) this instanceof Slime body) || !SlimeMorphManager.isMorphBody(body)) {
            return;
        }
        if (source.is(DamageTypes.IN_WALL)
                && SlimeFormState.isPhaseEnabled(body)
                && SlimeFormPhaseableBlocks.shouldIgnoreInWallDamage(body)) {
            SlimeFormPhaseDebug.recordDamage(body, "in_wall", true);
            callback.setReturnValue(false);
            return;
        }
        SlimeFormPhaseDebug.recordDamage(body, source.type().toString(), false);
        ServerPlayer owner = SlimeMorphManager.ownerOf(body);
        if (owner == null || !owner.isAlive()) {
            callback.setReturnValue(false);
            return;
        }
        callback.setReturnValue(owner.hurtServer(level, source, amount));
    }

    @Inject(
            method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V",
            at = @At("HEAD"),
            cancellable = true)
    private void slimeform$preventMorphBodyKnockback(
            double strength, double x, double z,
            net.minecraft.world.damagesource.DamageSource source,
            float amount, boolean flag, CallbackInfo callback) {
        if ((Object) this instanceof Slime body && SlimeMorphManager.isMorphBody(body)) {
            callback.cancel();
        }
    }
}
