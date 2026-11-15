package io.asy.fragmented;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import com.mojang.blaze3d.vertex.PoseStack;

/** Adds the vanilla translucent slime texture over a slightly expanded player silhouette. */
public final class SlimeAppearanceShellLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public SlimeAppearanceShellLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState state, float bodyYaw, float headYaw) {
        if (!(state instanceof SlimeAppearanceRenderStateAccess appearance)
                || !appearance.slimeform$isAppearanceActive()
                || !appearance.slimeform$hasSlimeShell()
                || state.isInvisible) {
            return;
        }

        float opacity = 1.0F - appearance.slimeform$getTransparencyPercent() / 100.0F;
        if (opacity <= 0.0F) {
            return;
        }

        poseStack.pushPose();
        poseStack.scale(1.035F, 1.035F, 1.035F);
        collector.order(1).submitModel(
                getParentModel(),
                state,
                poseStack,
                RenderTypes.entityTranslucent(SlimeRenderer.SLIME_LOCATION),
                packedLight,
                LivingEntityRenderer.getOverlayCoords(state, 0.0F),
                ARGB.white(opacity),
                null,
                state.outlineColor);
        poseStack.popPose();
    }
}
