package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormMod;
import io.asy.fragmented.SlimeFormState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class SlimeFormPlayerMixin {
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void slimeform$blockDormantDamage(
            ServerLevel level, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && SlimeFormMod.isDormant(player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "knockback", at = @At("HEAD"), cancellable = true)
    private void slimeform$blockDormantKnockback(
            double strength, double x, double z, CallbackInfo ci) {
        if ((Object) this instanceof Player player && SlimeFormMod.isDormant(player)) {
            ci.cancel();
        }
    }
    @Inject(method = "playHurtSound", at = @At("HEAD"), cancellable = true)
    private void slimeform$playSlimeHurtSound(DamageSource source, CallbackInfo ci) {
        if ((Object) this instanceof Player player
                && SlimeFormState.isActive(player)
                && !player.level().isClientSide()) {
            player.playSound(SoundEvents.SLIME_HURT, 1.0F, 1.0F);
            ((ServerLevel) player.level()).sendParticles(
                    ParticleTypes.ITEM_SLIME,
                    player.getX(), player.getY() + 0.7D, player.getZ(),
                    8, 0.25D, 0.35D, 0.25D, 0.04D);
            ci.cancel();
        }
    }

    @Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true)
    private void slimeform$useSlimeDeathSound(CallbackInfoReturnable<SoundEvent> cir) {
        if ((Object) this instanceof Player player && SlimeFormState.isActive(player)) {
            cir.setReturnValue(SoundEvents.SLIME_DEATH);
        }
    }

    @Inject(method = "jumpFromGround", at = @At("TAIL"))
    private void slimeform$playSlimeJumpSound(CallbackInfo ci) {
        if ((Object) this instanceof Player player
                && SlimeFormState.isActive(player)
                && !player.level().isClientSide()) {
            player.playSound(SoundEvents.SLIME_JUMP, 1.0F, 1.0F);
            ((ServerLevel) player.level()).sendParticles(
                    ParticleTypes.ITEM_SLIME,
                    player.getX(), player.getY() + 0.25D, player.getZ(),
                    10, 0.25D, 0.15D, 0.25D, 0.04D);
        }
    }

    @Inject(method = "checkFallDamage", at = @At("HEAD"))
    private void slimeform$playSlimeLandSound(
            double y, boolean onGround, BlockState state, BlockPos pos, CallbackInfo ci) {
        if ((Object) this instanceof Player player
                && SlimeFormState.isActive(player)
                && onGround
                && player.fallDistance > 0.0D
                && !player.level().isClientSide()) {
            player.playSound(SoundEvents.SLIME_SQUISH, 1.0F, 1.0F);
            ((ServerLevel) player.level()).sendParticles(
                    ParticleTypes.ITEM_SLIME,
                    player.getX(), player.getY() + 0.1D, player.getZ(),
                    12, 0.3D, 0.08D, 0.3D, 0.04D);
        }
    }

    @ModifyVariable(method = "causeFallDamage", at = @At("HEAD"), argsOnly = true)
    private float slimeform$reduceFallDamage(float damageMultiplier) {
        if ((Object) this instanceof Player player && SlimeFormState.isActive(player)) {
            return damageMultiplier * 0.25F;
        }
        return damageMultiplier;
    }
}
