package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeAbsorbClientState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class SlimeAbsorbEntityDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void slimeform$suppressTrappedEntities(
            Entity entity,
            Frustum frustum,
            double camX,
            double camY,
            double camZ,
            float partialTick,
            CallbackInfoReturnable<Boolean> cir) {
        if (entity != null && SlimeAbsorbClientState.isTrapped(entity.getId())) {
            cir.setReturnValue(false);
        }
    }
}
