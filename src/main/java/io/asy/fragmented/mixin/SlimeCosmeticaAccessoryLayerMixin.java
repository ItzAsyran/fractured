package io.asy.fragmented.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.asy.fragmented.SlimeCosmeticaCompat;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cosmetica compat (1/2): remembers which avatar Cosmetica is drawing accessories for.
 * Pseudo + require = 0: harmless when Cosmetica is not installed or its internals change.
 */
@Pseudo
@Mixin(targets = "cc.cosmetica.core.render.HumanoidAccessoriesLayer")
public abstract class SlimeCosmeticaAccessoryLayerMixin {
    private static final String SUBMIT_METHOD = "submit("
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V";

    @Inject(method = SUBMIT_METHOD, at = @At("HEAD"), require = 0)
    private void slimeform$beginAccessories(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int light,
            HumanoidRenderState state,
            float a,
            float b,
            CallbackInfo ci) {
        SlimeCosmeticaCompat.begin(state);
    }

    @Inject(method = SUBMIT_METHOD, at = @At("RETURN"), require = 0)
    private void slimeform$endAccessories(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int light,
            HumanoidRenderState state,
            float a,
            float b,
            CallbackInfo ci) {
        SlimeCosmeticaCompat.end();
    }
}
