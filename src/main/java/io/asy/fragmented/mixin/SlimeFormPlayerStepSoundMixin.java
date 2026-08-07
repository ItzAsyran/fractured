package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormState;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class SlimeFormPlayerStepSoundMixin {
    @Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
    private void slimeform$playSlimeStepSound(
            BlockPos pos, BlockState state, CallbackInfo ci) {
        if ((Object) this instanceof Player player
                && SlimeFormState.isActive(player)
                && !player.level().isClientSide()) {
            player.playSound(SoundEvents.SLIME_SQUISH, 0.35F, 1.15F);
            ci.cancel();
        }
    }
}
