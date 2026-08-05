package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormMod;
import io.asy.fragmented.SlimeFormState;
import net.minecraft.core.BlockPos;
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
            ci.cancel();
        }
    }

    @Inject(method = "jumpFromGround", at = @At("TAIL"))
    private void slimeform$playSlimeJumpSound(CallbackInfo ci) {
        if ((Object) this instanceof Player player
                && SlimeFormState.isActive(player)
                && !player.level().isClientSide()) {
            player.playSound(SoundEvents.SLIME_JUMP, 1.0F, 1.0F);
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
