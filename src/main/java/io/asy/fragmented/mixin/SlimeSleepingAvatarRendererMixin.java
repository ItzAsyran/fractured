package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormMod;
import io.asy.fragmented.SlimeFormState;
import io.asy.fragmented.SlimeSleepingStateAccess;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class SlimeSleepingAvatarRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void slimeform$prepareSleepingSlime(
            Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        boolean replace = false;
        if (avatar instanceof Player player) {
            boolean dormant = SlimeFormMod.isDormant(player)
                    || (player.isInvisible() && SlimeFormState.isClientVisualSlimeForm(player));
            boolean sleeping = SlimeFormState.isClientVisualSlimeForm(player)
                    && (player.isSleeping() || state.hasPose(Pose.SLEEPING));
            replace = dormant || sleeping;
        }
        ((SlimeSleepingStateAccess) state).slimeform$setReplaceWithSlime(replace);
    }
}
