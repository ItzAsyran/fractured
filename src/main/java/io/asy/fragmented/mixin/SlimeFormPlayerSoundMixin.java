package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class SlimeFormPlayerSoundMixin {
    @Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
    private void slimeform$useSlimeHurtSound(
            DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
        Player player = (Player) (Object) this;
        if (SlimeFormSounds.isSlime(player)) {
            cir.setReturnValue(SlimeFormSounds.hurt(player));
        }
    }

    @Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true)
    private void slimeform$useSlimeDeathSound(CallbackInfoReturnable<SoundEvent> cir) {
        Player player = (Player) (Object) this;
        if (SlimeFormSounds.isSlime(player)) {
            cir.setReturnValue(SlimeFormSounds.death(player));
        }
    }

    @Inject(method = "getFallSounds", at = @At("HEAD"), cancellable = true)
    private void slimeform$useSlimeFallSounds(
            CallbackInfoReturnable<LivingEntity.Fallsounds> cir) {
        Player player = (Player) (Object) this;
        if (SlimeFormSounds.isSlime(player)) {
            cir.setReturnValue(new LivingEntity.Fallsounds(
                    SlimeFormSounds.squish(player),
                    SlimeFormSounds.squish(player)));
        }
    }

    /** Covers crit, strong, weak, knockback, sweep and no-damage attack sounds. */
    @ModifyVariable(method = "playServerSideSound", at = @At("HEAD"), argsOnly = true)
    private SoundEvent slimeform$useSlimeAttackSound(SoundEvent sound) {
        Player player = (Player) (Object) this;
        return SlimeFormSounds.isSlime(player) ? SlimeFormSounds.attack(sound) : sound;
    }

    @ModifyArg(
            method = "deflectProjectile",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;playSound("
                            + "Lnet/minecraft/world/entity/Entity;DDD"
                            + "Lnet/minecraft/sounds/SoundEvent;"
                            + "Lnet/minecraft/sounds/SoundSource;)V"),
            index = 4)
    private SoundEvent slimeform$useSlimeDeflectSound(SoundEvent sound) {
        Player player = (Player) (Object) this;
        return SlimeFormSounds.isSlime(player) ? SlimeFormSounds.attack(sound) : sound;
    }
}
