package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.monster.cubemob.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class SlimeMorphCameraMixin {
    @Invoker("setRotation")
    protected abstract void slimeform$setRotation(float yRot, float xRot);

    @Inject(
            method = "alignWithEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;move(FFF)V"))
    private void slimeform$usePlayerViewRotationForThirdPerson(
            float partialTick, CallbackInfo ci) {
        Camera camera = (Camera) (Object) this;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && camera.entity() instanceof Slime
                && SlimeFormClient.isLocalMorphBodyActive()) {
            boolean mirror = false;
            float yRot = player.getViewYRot(partialTick) + (mirror ? 180.0F : 0.0F);
            float xRot = mirror ? -player.getViewXRot(partialTick) : player.getViewXRot(partialTick);
            slimeform$setRotation(yRot, xRot);
        }
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void slimeform$usePlayerPitchInFirstPerson(
            DeltaTracker deltaTracker, CallbackInfo ci) {
        Camera camera = (Camera) (Object) this;
        LocalPlayer player = Minecraft.getInstance().player;
        if (!camera.isDetached() && player != null && camera.entity() instanceof Slime
                && SlimeFormClient.isLocalMorphBodyActive()) {
            float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
            slimeform$setRotation(
                    player.getViewYRot(partialTick), player.getViewXRot(partialTick));
        }
    }
}
