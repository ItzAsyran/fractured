package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormMod;
import io.asy.fragmented.SlimeMorphManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TEMP-DEBUG: logs who removes a morph body and why. Delete this file and its json entry afterwards. */
@Mixin(Entity.class)
public abstract class SlimeMorphDebugRemoveMixin {
    @Inject(method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V", at = @At("HEAD"), require = 0)
    private void slimeform$debugMorphBodyRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
        if ((Object) this instanceof Slime slime
                && SlimeMorphManager.isMorphBody(slime)
                && !slime.level().isClientSide()) {
            SlimeFormMod.LOGGER.info("[slimeform-debug] morph body remove(): reason={} difficulty={}",
                    reason, slime.level().getDifficulty(), new Throwable("remove() called from"));
        }
    }
}
