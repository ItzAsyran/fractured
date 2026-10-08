package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeAppearanceHandShell;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs right after vanilla submitted a first-person arm. The handlers take only the
 * {@link CallbackInfo} (the hand arguments are not needed; the frame context comes from
 * {@link SlimeAppearanceHandContextMixin}) and use {@code require = 0}, so if a hand method is
 * renamed in a Minecraft update the shell simply stops showing on the viewmodel instead of
 * crashing the game; Mixin logs a warning about the missing target.
 */
@Mixin(AvatarRenderer.class)
public abstract class SlimeAppearanceHandRendererMixin {
    @Inject(method = "renderRightHand", at = @At("TAIL"), require = 0)
    private void slimeform$shellRightHand(CallbackInfo ci) {
        SlimeAppearanceHandShell.submit(self(), true);
    }

    @Inject(method = "renderLeftHand", at = @At("TAIL"), require = 0)
    private void slimeform$shellLeftHand(CallbackInfo ci) {
        SlimeAppearanceHandShell.submit(self(), false);
    }

    @SuppressWarnings("unchecked")
    private RenderLayerParent<AvatarRenderState, PlayerModel> self() {
        return (RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) this;
    }
}
