package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.monster.cubemob.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class SlimeMorphItemRendererMixin {
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void slimeform$hideMorphedItems(
            float partialTick, PoseStack poseStack, SubmitNodeCollector collector,
            LocalPlayer player, int light, CallbackInfo ci) {
        if (SlimeFormClient.isLocalMorphActive()) {
            ci.cancel();
            return;
        }

        if (player.getVehicle() instanceof Slime) {
            float scale = player.getScale();
            poseStack.pushPose();
            poseStack.scale(scale, scale, scale);
        }
    }

    @Inject(method = "submitHandsWithItems", at = @At("TAIL"))
    private void slimeform$restoreRiderHandScale(
            float partialTick, PoseStack poseStack, SubmitNodeCollector collector,
            LocalPlayer player, int light, CallbackInfo ci) {
        if (player.getVehicle() instanceof Slime) {
            poseStack.popPose();
        }
    }
}
