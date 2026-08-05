package io.asy.fragmented.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class SlimeDeathScreenMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void slimeform$skipDeathScreen(Screen screen, CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (minecraft.player != null
                && minecraft.player.isDeadOrDying()
                && minecraft.gameMode != null
                && minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR
                && (screen == null || screen instanceof DeathScreen)) {
            ci.cancel();
        }
    }
}
