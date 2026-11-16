package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeMorphManager;
import io.asy.fragmented.SlimeRecoveryManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Slimes are hostile, so vanilla discards them in peaceful difficulty. That would end a morph on
 * its first tick and remove the split fragments a player needs to reform. Every vanilla despawn
 * path ends in discard(), so refusing that one removal for slimes that are still needed keeps them
 * alive. The mod cleanup and orphaned slimes are not blocked (see
 * SlimeMorphManager#shouldBlockPeacefulRemoval and
 * SlimeRecoveryManager#shouldBlockPeacefulFragmentRemoval).
 */
@Mixin(Entity.class)
public abstract class SlimePeacefulGuardMixin {
    @Inject(
            method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void slimeform$keepNeededSlimesInPeaceful(Entity.RemovalReason reason, CallbackInfo ci) {
        if (reason == Entity.RemovalReason.DISCARDED
                && (Object) this instanceof Slime slime
                && (SlimeMorphManager.shouldBlockPeacefulRemoval(slime)
                        || SlimeRecoveryManager.shouldBlockPeacefulFragmentRemoval(slime))) {
            ci.cancel();
        }
    }
}
