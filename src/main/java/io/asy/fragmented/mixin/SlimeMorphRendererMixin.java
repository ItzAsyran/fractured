package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeMorphRenderStateAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.cubemob.Slime;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class SlimeMorphRendererMixin {
    @Inject(method = "submit", at = @At("HEAD"))
    private void slimeform$renderMorphedSlime(
            LivingEntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraState,
            CallbackInfo ci) {
        if (!(state instanceof SlimeMorphRenderStateAccess morphState)
                || !morphState.slimeform$isMorphed()
                || Minecraft.getInstance().level == null) {
            return;
        }

        Slime slime = EntityTypes.SLIME.create(
                Minecraft.getInstance().level, EntitySpawnReason.LOAD);
        if (slime == null) {
            return;
        }
        slime.setId(Integer.MAX_VALUE);
        slime.setSize(morphState.slimeform$getSize(), false);
        slime.tickCount = (int) state.ageInTicks;
        SlimeRenderer renderer = (SlimeRenderer) Minecraft.getInstance()
                .getEntityRenderDispatcher().getRenderer(slime);
        SlimeRenderState slimeState = (SlimeRenderState) renderer.createRenderState();
        renderer.extractRenderState(slime, slimeState, 0.0F);
        renderer.submit(slimeState, poseStack, collector, cameraState);
    }
}
