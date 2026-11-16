package io.asy.fragmented;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.cubemob.Slime;

public final class SlimeFormEntities {
    private SlimeFormEntities() {
    }

    /**
     * Vanilla refuses to create hostile mobs in peaceful difficulty (create returns null), so fall
     * back to constructing the slime directly. SlimePeacefulGuardMixin keeps such slimes from being
     * discarded by vanilla peaceful cleanup while they are still needed.
     */
    public static Slime createSlime(ServerLevel level) {
        Slime slime = EntityTypes.SLIME.create(level, EntitySpawnReason.TRIGGERED);
        return slime != null ? slime : new Slime(EntityTypes.SLIME, level);
    }
}
