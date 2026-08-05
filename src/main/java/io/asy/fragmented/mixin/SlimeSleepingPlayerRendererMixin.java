package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormState;
import io.asy.fragmented.SlimeFormMod;
import io.asy.fragmented.SlimeSleepingStateAccess;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderState.class)
abstract class SlimeSleepingAvatarRenderStateMixin implements SlimeSleepingStateAccess {
    @Unique
    private boolean slimeform$replaceWithSlime;

    @Override
    public boolean slimeform$shouldReplaceWithSlime() {
        return slimeform$replaceWithSlime;
    }

    @Override
    public void slimeform$setReplaceWithSlime(boolean replace) {
        slimeform$replaceWithSlime = replace;
    }
}

@Mixin(LivingEntityRenderer.class)
public abstract class SlimeSleepingPlayerRendererMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void slimeform$renderSleepingSlime(
            LivingEntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraState,
            CallbackInfo ci) {
        if (state instanceof SlimeSleepingStateAccess sleepingState
                && sleepingState.slimeform$shouldReplaceWithSlime()) {
            ci.cancel();
        }
    }
}
