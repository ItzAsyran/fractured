package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormMod;
import io.asy.fragmented.SlimeFormState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class SlimeMountMixin {
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void slimeform$mountActivatedPlayer(
            Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (hand == InteractionHand.MAIN_HAND
                && SlimeFormState.isActive(player)
                && (Object) this instanceof Slime slime
                && slime.getTags().stream().noneMatch(tag -> tag.startsWith("slimeform.visual."))
                && !player.isPassenger()
                && slime.getPassengers().isEmpty()) {
            if (player.level().isClientSide()) {
                cir.setReturnValue(InteractionResult.SUCCESS);
            } else if (player.startRiding(slime, true, true)) {
                SlimeFormMod.LOGGER.info(
                        "[slimeform] Player {} mounted slime {}",
                        player.getUUID(), slime.getUUID());
                cir.setReturnValue(InteractionResult.SUCCESS_SERVER);
            } else {
                SlimeFormMod.LOGGER.warn(
                        "[slimeform] Player {} failed to mount slime {}",
                        player.getUUID(), slime.getUUID());
            }
        }
    }

}
