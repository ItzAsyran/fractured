package io.asy.fragmented.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class SlimeClientRecoveryMixin {
    @Unique
    private boolean slimeform$initialRespawnBlocked;

    @Inject(method = "respawn", at = @At("HEAD"), cancellable = true)
    private void slimeform$blockEarlyClientRespawn(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (player.isDeadOrDying()
                && player.gameMode() == GameType.SPECTATOR
                && !slimeform$initialRespawnBlocked) {
            slimeform$initialRespawnBlocked = true;
            ci.cancel();
        }
    }
}
