package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormMod;
import io.asy.fragmented.SlimeMorphManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Slimes are hostile, so vanilla discards them in peaceful difficulty, which would end the morph
 * on its first tick. Every vanilla despawn path ends in discard(), so refusing that one removal
 * for a live morph body keeps it alive. The morph manager's own removal and orphaned bodies are
 * not blocked (see SlimeMorphManager#shouldBlockPeacefulRemoval).
 */
@Mixin(Entity.class)
public abstract class SlimeMorphPeacefulGuardMixin {
    // TEMP-DEBUG counter: limits log spam, remove with the debug logging.
    private static int slimeform$debugBlocked;

    @Inject(
            method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void slimeform$keepMorphBodyInPeaceful(Entity.RemovalReason reason, CallbackInfo ci) {
        if (reason == Entity.RemovalReason.DISCARDED
                && (Object) this instanceof Slime slime
                && SlimeMorphManager.shouldBlockPeacefulRemoval(slime)) {
            if (slimeform$debugBlocked < 5) {
                slimeform$debugBlocked++;
                SlimeFormMod.LOGGER.info("[slimeform-debug] blocked peaceful discard of the morph body");
            }
            ci.cancel();
        }
    }
}
