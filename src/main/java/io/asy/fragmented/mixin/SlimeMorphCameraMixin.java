package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeMorphManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class SlimeMorphCameraMixin {
    @Invoker("setRotation")
    protected abstract void slimeform$setRotation(float xRot, float yRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void slimeform$usePlayerViewRotation(
            Level level, Entity entity, boolean detached, boolean mirror,
            float partialTick, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && entity.getTags().contains(SlimeMorphManager.MORPH_TAG)) {
            slimeform$setRotation(player.getViewXRot(partialTick), player.getViewYRot(partialTick));
        }
    }
}
