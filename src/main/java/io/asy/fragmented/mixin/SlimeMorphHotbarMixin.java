package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Hud.class)
public abstract class SlimeMorphHotbarMixin {
    @Inject(method = "getCameraPlayer", at = @At("RETURN"), cancellable = true)
    private void slimeform$useLocalPlayerForHotbar(CallbackInfoReturnable<Player> cir) {
        if (SlimeFormClient.isLocalMorphActive()) {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                cir.setReturnValue(player);
            }
        }
    }
}
