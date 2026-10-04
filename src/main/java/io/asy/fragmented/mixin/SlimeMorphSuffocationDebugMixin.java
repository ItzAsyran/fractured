package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormPhaseDebug;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Temporary reachability probe for vanilla's in-wall check. */
@Mixin(Entity.class)
public abstract class SlimeMorphSuffocationDebugMixin {
    @Inject(method = "isInWall", at = @At("RETURN"))
    private void slimeform$recordInWall(CallbackInfoReturnable<Boolean> callback) {
        SlimeFormPhaseDebug.recordInWall((Entity) (Object) this, callback.getReturnValue());
    }
}
