package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Player overrides playStepSound and routes water, combination and regular
 * steps from there, so hooking it replaces every footstep variant. It runs on
 * both sides: the walking client hears its own steps, and the server's
 * Player#playSound broadcasts them to everyone else.
 */
@Mixin(Player.class)
public abstract class SlimeFormPlayerStepSoundMixin {
    @Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
    private void slimeform$playSlimeStepSound(
            BlockPos pos, BlockState state, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (SlimeFormSounds.isSlime(player)) {
            player.playSound(SlimeFormSounds.squish(player), 0.35F, 1.15F);
            ci.cancel();
        }
    }
}
