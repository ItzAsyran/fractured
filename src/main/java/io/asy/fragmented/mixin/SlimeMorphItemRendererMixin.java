package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.monster.cubemob.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class SlimeMorphItemRendererMixin {
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void slimeform$hideMorphedItems(
            float partialTick, PoseStack poseStack, SubmitNodeCollector collector,
            PlayerRenderState player, FirstPersonHandsAndItemsRenderState renderState, CallbackInfo ci) {
        if (SlimeFormClient.isLocalMorphActive()) {
            ci.cancel();
            return;
        }

        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null && localPlayer.getVehicle() instanceof Slime) {
            float scale = localPlayer.getScale();
            poseStack.pushPose();
            poseStack.scale(scale, scale, scale);
        }
    }

    @Inject(method = "submitHandsWithItems", at = @At("TAIL"))
    private void slimeform$restoreRiderHandScale(
            float partialTick, PoseStack poseStack, SubmitNodeCollector collector,
            PlayerRenderState player, FirstPersonHandsAndItemsRenderState renderState, CallbackInfo ci) {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null && localPlayer.getVehicle() instanceof Slime) {
            poseStack.popPose();
        }
    }
}
