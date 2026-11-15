package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeAppearanceRenderStateAccess;
import io.asy.fragmented.SlimeAppearanceRendererAccess;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class SlimeAppearanceRendererMixin implements SlimeAppearanceRendererAccess {
    @Invoker("addLayer")
    @Override
    public abstract boolean slimeform$addAppearanceLayer(RenderLayer<?, ?> layer);

    @Shadow
    public abstract Identifier getTextureLocation(LivingEntityRenderState state);

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void slimeform$useTranslucentPlayerRenderType(
            LivingEntityRenderState state,
            boolean bodyVisible,
            boolean translucent,
            boolean glowing,
            CallbackInfoReturnable<RenderType> cir) {
        if (!(state instanceof AvatarRenderState)
                || !(state instanceof SlimeAppearanceRenderStateAccess appearance)
                || !appearance.slimeform$isAppearanceActive()
                || appearance.slimeform$hasSlimeShell()
                || appearance.slimeform$getTransparencyPercent() == 0
                || state.isInvisible
                || glowing) {
            return;
        }

        cir.setReturnValue(RenderTypes.entityTranslucent(getTextureLocation(state)));
    }

    @Inject(method = "getModelTint", at = @At("RETURN"), cancellable = true)
    private void slimeform$applyPlayerTransparencyAndTint(
            LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
        if (!(state instanceof AvatarRenderState)
                || !(state instanceof SlimeAppearanceRenderStateAccess appearance)
                || !appearance.slimeform$isAppearanceActive()
                || appearance.slimeform$hasSlimeShell()
                || appearance.slimeform$getTransparencyPercent() == 0
                || state.isInvisible) {
            return;
        }

        float opacity = 1.0F - appearance.slimeform$getTransparencyPercent() / 100.0F;
        int tint = ARGB.multiplyAlpha(cir.getReturnValue(), opacity);
        if (appearance.slimeform$hasSlimeTint()) {
            tint = ARGB.multiply(tint, ARGB.color(255, 85, 255, 85));
        }
        cir.setReturnValue(tint);
    }
}
