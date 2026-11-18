package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeAbsorbClientState;
import io.asy.fragmented.SlimeFormClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class SlimeMorphInputMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void slimeform$disableCrouchDuringMorph(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && SlimeAbsorbClientState.isTrapped(client.player.getId())) {
            SlimeMorphClientInputAccess input = (SlimeMorphClientInputAccess) this;
            input.slimeform$setKeyPresses(new Input(false, false, false, false, false, false, false));
            return;
        }

        if (!SlimeFormClient.isLocalMorphActive()) {
            return;
        }

        SlimeMorphClientInputAccess input = (SlimeMorphClientInputAccess) this;
        Input current = input.slimeform$getKeyPresses();
        input.slimeform$setKeyPresses(new Input(
                current.forward(), current.backward(), current.left(), current.right(),
                current.jump(), false, current.sprint()));
    }
}
