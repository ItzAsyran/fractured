package io.asy.fragmented.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.asy.fragmented.SlimeAbsorbRenderStateAccess;
import io.asy.fragmented.SlimeAbsorbRendererHelper;
import io.asy.fragmented.SlimeFormConfig;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class SlimeAbsorbRendererMixin {
    @Shadow
    public abstract Identifier getTextureLocation(LivingEntityRenderState state);

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void slimeform$useTranslucentSlimeCoreRenderType(
            LivingEntityRenderState state,
            boolean bodyVisible,
            boolean translucent,
            boolean glowing,
            CallbackInfoReturnable<RenderType> cir) {
        if (glowing) {
            return;
        }
        if (state instanceof SlimeAbsorbRenderStateAccess access && access.slimeform$isHoldingAbsorbed()) {
            cir.setReturnValue(RenderTypes.entityTranslucent(getTextureLocation(state)));
        }
    }

    @Inject(method = "getModelTint", at = @At("RETURN"), cancellable = true)
    private void slimeform$applySlimeCoreTransparency(
            LivingEntityRenderState state,
            CallbackInfoReturnable<Integer> cir) {
        if (state instanceof SlimeAbsorbRenderStateAccess access && access.slimeform$isHoldingAbsorbed()) {
            float opacity = SlimeFormConfig.get().effectiveAbsorbInnerOpacityPercent() / 100.0F;
            int tint = ARGB.multiplyAlpha(cir.getReturnValue(), opacity);
            cir.setReturnValue(tint);
        }
    }

    @Redirect(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"))
    private <M extends net.minecraft.client.model.Model<? super Object>> void slimeform$orderSlimeCoreModel(
            SubmitNodeCollector collector,
            M model,
            Object modelState,
            PoseStack poseStack,
            RenderType renderType,
            int lightCoords,
            int overlayCoords,
            int tint,
            net.minecraft.client.renderer.texture.UvMapping uvMapping,
            int outlineColor,
            LivingEntityRenderState state,
            PoseStack poseStackArg,
            SubmitNodeCollector collectorArg,
            CameraRenderState cameraState) {
        if (state instanceof SlimeAbsorbRenderStateAccess access && access.slimeform$isHoldingAbsorbed()) {
            collector.order(1).submitModel(model, modelState, poseStack, renderType, lightCoords, overlayCoords, tint, uvMapping, outlineColor);
        } else {
            collector.submitModel(model, modelState, poseStack, renderType, lightCoords, overlayCoords, tint, uvMapping, outlineColor);
        }
    }

    @Inject(method = "submit", at = @At("TAIL"))
    private void slimeform$renderAbsorbedEntities(
            LivingEntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraState,
            CallbackInfo ci) {
        if (state instanceof SlimeRenderState slimeState
                && state instanceof SlimeAbsorbRenderStateAccess access
                && access.slimeform$isHoldingAbsorbed()) {
            SlimeAbsorbRendererHelper.renderTrappedEntities(slimeState, poseStack, collector, cameraState);
        }
    }
}
