package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.MouseHandler")
public abstract class FlowStateMouseMixin {
    @Inject(method = "onMove", at = @At("HEAD"))
    private void slimeform$captureFlowLook(long window, double deltaX, double deltaY, CallbackInfo ci) {
        SlimeFormClient.recordFlowMouse(deltaX, deltaY);
    }
}
