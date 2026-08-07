package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormState;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class SlimeFormPlayerSoundMixin {
    @Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
    private void slimeform$useSlimeHurtSound(
            DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
        if ((Object) this instanceof Player player && SlimeFormState.isActive(player)) {
            cir.setReturnValue(SoundEvents.SLIME_HURT);
        }
    }

    @Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true)
    private void slimeform$useSlimeDeathSound(CallbackInfoReturnable<SoundEvent> cir) {
        if ((Object) this instanceof Player player && SlimeFormState.isActive(player)) {
            cir.setReturnValue(SoundEvents.SLIME_DEATH);
        }
    }

    @Inject(method = "getFallSounds", at = @At("HEAD"), cancellable = true)
    private void slimeform$useSlimeFallSounds(
            CallbackInfoReturnable<LivingEntity.Fallsounds> cir) {
        if ((Object) this instanceof Player player && SlimeFormState.isActive(player)) {
            cir.setReturnValue(new LivingEntity.Fallsounds(
                    SoundEvents.SLIME_SQUISH,
                    SoundEvents.SLIME_SQUISH));
        }
    }

}
