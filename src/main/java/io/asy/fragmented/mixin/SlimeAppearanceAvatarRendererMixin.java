package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeAppearanceShellLayer;
import io.asy.fragmented.SlimeAppearanceRendererAccess;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class SlimeAppearanceAvatarRendererMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void slimeform$addSlimeAppearanceShell(
            EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        @SuppressWarnings("unchecked")
        RenderLayerParent<AvatarRenderState, PlayerModel> parent =
                (RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) this;
        ((SlimeAppearanceRendererAccess) this).slimeform$addAppearanceLayer(
                new SlimeAppearanceShellLayer(parent));
    }
}
