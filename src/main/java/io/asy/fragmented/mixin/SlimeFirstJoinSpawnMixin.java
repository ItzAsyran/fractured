package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeSwampSpawn;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.config.PrepareSpawnTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Places brand-new players (no saved position) in the swamp spawn. Only
 * auto-activated slime form applies here, since the player entity does not
 * exist yet.
 */
@Mixin(PrepareSpawnTask.class)
public abstract class SlimeFirstJoinSpawnMixin {
    @ModifyArgs(
            method = "lambda$start$3",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/PlayerSpawnFinder;findSpawn("
                            + "Lnet/minecraft/server/level/ServerLevel;"
                            + "Lnet/minecraft/core/BlockPos;)"
                            + "Ljava/util/concurrent/CompletableFuture;"))
    private static void slimeform$useSwampForNewPlayers(Args args) {
        if (!SlimeSwampSpawn.appliesTo(null)) {
            return;
        }
        ServerLevel level = args.get(0);
        BlockPos pos = args.get(1);
        SlimeSwampSpawn.find(level, pos).ifPresent(swamp -> args.set(1, swamp));
    }
}
