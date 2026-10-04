package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormClient;
import io.asy.fragmented.SlimeMorphRenderStateAccess;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public abstract class SlimeMorphAvatarRendererMixin {
    @Inject(method = "shouldRenderLayers", at = @At("HEAD"), cancellable = true)
    private void slimeform$hideMorphedLayers(
            AvatarRenderState state, CallbackInfoReturnable<Boolean> cir) {
        if (state instanceof SlimeMorphRenderStateAccess morphState
                && morphState.slimeform$isMorphed()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void slimeform$extractMorphState(
            Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        if (avatar instanceof Player player) {
            SlimeMorphRenderStateAccess morphState = (SlimeMorphRenderStateAccess) state;
            boolean morphed = SlimeFormClient.isClientMorphVisible(player);
            morphState.slimeform$setMorphed(morphed);
            morphState.slimeform$setSize(SlimeFormClient.getClientMorphSize(player));

            // LivingEntityRenderer uses these render-state flags to skip the
            // player model. The state is still submitted afterward so vanilla
            // nameplates and dispatcher bookkeeping remain intact.
            state.isInvisible = morphed;
            state.isInvisibleToPlayer = morphed;
        }
    }
}
