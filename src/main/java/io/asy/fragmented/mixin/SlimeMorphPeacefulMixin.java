package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormMod;
import io.asy.fragmented.SlimeMorphManager;
import net.minecraft.world.entity.monster.cubemob.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Slimes are hostile, so vanilla peaceful cleanup discards them. Vanilla asks this method
 * whether a mob may despawn in peaceful, so answering false for a live morph body keeps it
 * alive. Orphaned bodies (no active morph) still return the vanilla answer and clean up.
 * require = 0: if the method name differs in a version, morph in peaceful just stops working.
 */
@Mixin(Slime.class)
public abstract class SlimeMorphPeacefulMixin {
    // TEMP-DEBUG counter: limits log spam, remove with the debug logging.
    private static int slimeform$debugCalls;

    @Inject(method = "shouldDespawnInPeaceful", at = @At("HEAD"), cancellable = true, require = 0)
    private void slimeform$morphBodyStaysInPeaceful(CallbackInfoReturnable<Boolean> cir) {
        Slime self = (Slime) (Object) this;
        boolean protectedBody = SlimeMorphManager.shouldBlockPeacefulRemoval(self);
        if (SlimeMorphManager.isMorphBody(self) && slimeform$debugCalls < 10) {
            slimeform$debugCalls++;
            SlimeFormMod.LOGGER.info("[slimeform-debug] shouldDespawnInPeaceful hook called for morph body: protected={}", protectedBody);
        }
        if (protectedBody) {
            cir.setReturnValue(false);
        }
    }
}
