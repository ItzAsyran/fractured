package io.asy.fragmented.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.asy.fragmented.SlimeAppearanceHandShell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives {@link SlimeAppearanceHandShell} the frame's pose stack, collector and player. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class SlimeAppearanceHandContextMixin {
    // 26.3 no longer passes a light value here, so the shell is drawn fully lit.
    private static final int SLIMEFORM_FULL_BRIGHT = 0xF000F0;

    @Inject(method = "submitHandsWithItems", at = @At("HEAD"))
    private void slimeform$beginHandShell(
            float partialTick, PoseStack poseStack, SubmitNodeCollector collector,
            PlayerRenderState player, FirstPersonHandsAndItemsRenderState renderState,
            CallbackInfo ci) {
        SlimeAppearanceHandShell.begin(
                poseStack, collector, Minecraft.getInstance().player, SLIMEFORM_FULL_BRIGHT);
    }

    @Inject(method = "submitHandsWithItems", at = @At("TAIL"))
    private void slimeform$endHandShell(
            float partialTick, PoseStack poseStack, SubmitNodeCollector collector,
            PlayerRenderState player, FirstPersonHandsAndItemsRenderState renderState,
            CallbackInfo ci) {
        SlimeAppearanceHandShell.end();
    }
}
