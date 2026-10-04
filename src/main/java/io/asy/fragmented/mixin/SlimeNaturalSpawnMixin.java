package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeSwampSpawn;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Vanilla only calls adjustSpawnLocation with the level's world spawn, when
 * the player has no bed/anchor (never set, obstructed or destroyed). Bed and
 * anchor respawns take a different path and are left untouched.
 */
@Mixin(ServerPlayer.class)
public abstract class SlimeNaturalSpawnMixin {
    @ModifyVariable(method = "adjustSpawnLocation", at = @At("HEAD"), argsOnly = true)
    private BlockPos slimeform$useSwampAsWorldSpawn(BlockPos pos) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        ServerLevel level = player.level().getServer().findRespawnDimension();
        if (!pos.equals(level.getRespawnData().pos()) || !SlimeSwampSpawn.appliesTo(player)) {
            return pos;
        }
        return SlimeSwampSpawn.find(level, pos).orElse(pos);
    }
}
