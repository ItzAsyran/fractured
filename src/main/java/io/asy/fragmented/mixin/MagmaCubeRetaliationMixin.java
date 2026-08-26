package io.asy.fragmented.mixin;

import io.asy.fragmented.MagmaCubeRetaliationAccess;
import io.asy.fragmented.SlimeFormMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(LivingEntity.class)
public abstract class MagmaCubeRetaliationMixin implements MagmaCubeRetaliationAccess {
    @Unique
    private UUID slimeform$retaliationTarget;

    @Override
    public void slimeform$setRetaliationTarget(Player player) {
        slimeform$retaliationTarget = player.getUUID();
    }

    @Override
    public boolean slimeform$isRetaliatingAgainst(Player player) {
        return slimeform$retaliationTarget != null
                && slimeform$retaliationTarget.equals(player.getUUID());
    }

    @Inject(method = "hurtServer", at = @At("TAIL"))
    private void slimeform$rememberSlimeFormAttacker(
            ServerLevel level, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof MagmaCube
                && cir.getReturnValue()
                && source.getEntity() instanceof Player player
                && player.getTags().contains(SlimeFormMod.SLIME_FORM_TAG)) {
            slimeform$setRetaliationTarget(player);
            ((MagmaCube) (Object) this).setTarget(player);
        }
    }
}
