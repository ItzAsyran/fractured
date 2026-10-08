package io.asy.fragmented;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;

/**
 * Draws the slime shell on the first-person arm (the viewmodel).
 *
 * <p>The third-person shell is a render layer on the whole player model, which the first-person
 * hand path never runs. Instead, {@code ItemInHandRenderer.submitHandsWithItems} hands us the
 * pose stack, collector, player and light for the frame ({@link #begin}); when vanilla has just
 * submitted the arm, the hand-renderer hook calls {@link #submit}, which submits the same arm
 * part a second time, slightly enlarged, with the shell texture.
 */
public final class SlimeAppearanceHandShell {
    private static PoseStack poseStack;
    private static SubmitNodeCollector collector;
    private static LocalPlayer player;
    private static int light;
    private static boolean logged;

    private SlimeAppearanceHandShell() {
    }

    public static void begin(PoseStack stack, SubmitNodeCollector nodeCollector, LocalPlayer localPlayer, int packedLight) {
        poseStack = stack;
        collector = nodeCollector;
        player = localPlayer;
        light = packedLight;
    }

    public static void end() {
        poseStack = null;
        collector = null;
        player = null;
    }

    public static void submit(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, boolean rightHand) {
        if (poseStack == null || collector == null || player == null
                || !SlimeFormState.isClientVisualSlimeForm(player)) {
            return;
        }
        SlimeAppearanceSettings settings = SlimeFormClient.getPlayerAppearanceSettings(player);
        if (!settings.slimeShell()) {
            return;
        }
        float opacity = 1.0F - settings.transparencyPercent() / 100.0F;
        if (opacity <= 0.0F) {
            return;
        }

        PlayerModel model = renderer.getModel();
        ModelPart arm = rightHand ? model.rightArm : model.leftArm;
        poseStack.pushPose();
        poseStack.scale(
                SlimeAppearanceShellLayer.SHELL_SCALE,
                SlimeAppearanceShellLayer.SHELL_SCALE,
                SlimeAppearanceShellLayer.SHELL_SCALE);
        collector.order(1).submitModelPart(
                arm,
                poseStack,
                RenderTypes.entityTranslucent(SlimeAppearanceShellLayer.SHELL_TEXTURE),
                light,
                OverlayTexture.NO_OVERLAY,
                null,
                ARGB.white(opacity),
                null);
        poseStack.popPose();
        if (!logged) {
            logged = true;
            SlimeFormMod.LOGGER.info("[slimeform] First-person slime shell is being drawn");
        }
    }
}
