package io.asy.fragmented.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.asy.fragmented.SlimeAbsorbRenderStateAccess;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SlimeOuterLayer.class)
public abstract class SlimeAbsorbOuterLayerMixin {
    @Redirect(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/SlimeRenderState;FF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;order(I)Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;"))
    private OrderedSubmitNodeCollector slimeform$adjustSlimeOuterLayerOrder(
            SubmitNodeCollector collector,
            int order,
            PoseStack poseStack,
            SubmitNodeCollector collectorArg,
            int lightCoords,
            SlimeRenderState state,
            float yRot,
            float xRot) {
        if (state instanceof SlimeAbsorbRenderStateAccess access && access.slimeform$isHoldingAbsorbed()) {
            return collector.order(order + 1);
        }
        return collector.order(order);
    }
}
