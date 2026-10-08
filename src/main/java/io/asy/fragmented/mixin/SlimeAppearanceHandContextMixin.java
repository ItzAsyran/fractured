package io.asy.fragmented.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.asy.fragmented.SlimeAppearanceHandShell;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives {@link SlimeAppearanceHandShell} the frame's pose stack, collector, player and light. */
@Mixin(ItemInHandRenderer.class)
public abstract class SlimeAppearanceHandContextMixin {
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"))
    private void slimeform$beginHandShell(
            float partialTick, PoseStack poseStack, SubmitNodeCollector collector,
            LocalPlayer player, int light, CallbackInfo ci) {
        SlimeAppearanceHandShell.begin(poseStack, collector, player, light);
    }

    @Inject(method = "submitHandsWithItems", at = @At("TAIL"))
    private void slimeform$endHandShell(
            float partialTick, PoseStack poseStack, SubmitNodeCollector collector,
            LocalPlayer player, int light, CallbackInfo ci) {
        SlimeAppearanceHandShell.end();
    }
}
