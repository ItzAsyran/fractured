package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeMorphManager;
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
public abstract class SlimeMorphDamageMixin {
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void slimeform$forwardMorphDamage(
            ServerLevel level, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> callback) {
        if (!((Object) this instanceof Slime body) || !SlimeMorphManager.isMorphBody(body)) {
            return;
        }
        ServerPlayer owner = SlimeMorphManager.ownerOf(body);
        if (owner == null || !owner.isAlive()) {
            callback.setReturnValue(false);
            return;
        }
        callback.setReturnValue(owner.hurtServer(level, source, amount));
    }
}
