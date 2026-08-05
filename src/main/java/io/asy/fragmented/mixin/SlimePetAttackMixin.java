package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class SlimePetAttackMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void slimeform$blockDormantAttack(Entity target, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (SlimeFormMod.isDormant(player)) {
            if (!player.level().isClientSide()
                    && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                SlimeFormMod.wakeDormant(serverPlayer);
            }
            ci.cancel();
        } else if (!player.level().isClientSide()
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            SlimeFormMod.recordCombat(serverPlayer);
        }
    }

    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    private void slimeform$blockDormantEntityInteraction(
            Entity target,
            net.minecraft.world.InteractionHand hand,
            CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir) {
        Player player = (Player) (Object) this;
        if (SlimeFormMod.isDormant(player)) {
            if (!player.level().isClientSide()
                    && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                SlimeFormMod.wakeDormant(serverPlayer);
            }
            cir.setReturnValue(net.minecraft.world.InteractionResult.FAIL);
        }
    }

    @Inject(method = "attack", at = @At("TAIL"))
    private void slimeform$commandNearbySlimes(Entity target, CallbackInfo ci) {
        Player player = (Player) (Object) this;

        // Player.attack is called on both sides; changing mob AI belongs on the server.
        if (player.level().isClientSide()
                || !player.getTags().contains(SlimeFormMod.SLIME_FORM_TAG)
                || !(target instanceof LivingEntity livingTarget)
                || livingTarget instanceof Player
                || livingTarget instanceof Slime) {
            return;
        }

        SlimeFormMod.commandNearbySlimesToAttack(player, livingTarget);
    }
}
