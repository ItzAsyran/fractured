package io.asy.fragmented;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Wraps the whole player in a translucent slime shell: the player model drawn again, slightly
 * expanded, with a texture that covers every part of the skin layout.
 *
 * <p>The vanilla slime texture only lines up with the head area of the player model (the body,
 * arms and legs map onto empty parts of it), so it left most of the body uncovered. The mod's own
 * {@code slime_shell.png} is filled across the whole 64x64 layout, so every face is covered.
 */
public final class SlimeAppearanceShellLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier SHELL_TEXTURE = Identifier.fromNamespaceAndPath(
            SlimeFormMod.MOD_ID, "textures/entity/slime_shell.png");

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
                RenderTypes.entityTranslucent(SHELL_TEXTURE),
                packedLight,
                LivingEntityRenderer.getOverlayCoords(state, 0.0F),
                ARGB.white(opacity),
                null,
                state.outlineColor,
                null);
        poseStack.popPose();
    }
}
