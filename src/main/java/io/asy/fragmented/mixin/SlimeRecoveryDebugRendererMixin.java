package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormConfig;
import io.asy.fragmented.SlimeFormMod;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class SlimeRecoveryDebugRendererMixin {
    @Inject(method = "getNameTag", at = @At("HEAD"), cancellable = true)
    private void slimeform$recoveryNameTag(
            Entity entity, CallbackInfoReturnable<Component> cir) {
        if (SlimeFormConfig.get().recoveryLineageDebug
                && entity instanceof Slime slime
                && SlimeFormMod.hasRecoveryLineage(slime)) {
            cir.setReturnValue(Component.literal(SlimeFormMod.getRecoveryDebugLabel(slime)));
        }
    }
}

@Mixin(LivingEntityRenderer.class)
abstract class SlimeRecoveryDebugLivingRendererMixin {
    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void slimeform$showRecoveryName(
            LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (SlimeFormConfig.get().recoveryLineageDebug
                && entity instanceof Slime slime
                && SlimeFormMod.hasRecoveryLineage(slime)) {
            cir.setReturnValue(distance < 4096.0D);
        }
    }
}
