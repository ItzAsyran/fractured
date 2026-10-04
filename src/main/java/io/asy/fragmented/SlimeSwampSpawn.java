package io.asy.fragmented;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Resolves the swamp that replaces the world spawn for slime-form players
 * without a bed or respawn anchor. The biome search is expensive, so the
 * result is cached per level and only recomputed if the world spawn moves.
 */
public final class SlimeSwampSpawn {
    private static final int SEARCH_RADIUS = 6400;
    private static final int HORIZONTAL_STEP = 32;
    private static final int VERTICAL_STEP = 64;
    private static final Map<ServerLevel, CachedSwamp> CACHE = new WeakHashMap<>();

    private SlimeSwampSpawn() {
    }

    private record CachedSwamp(BlockPos worldSpawn, Optional<BlockPos> swamp) {
    }

    /** Whether swamp spawning applies to a player, or to a brand-new player when null. */
    public static boolean appliesTo(Player player) {
        SlimeFormConfig config = SlimeFormConfig.get();
        if (!config.swampSpawnEnabled) {
            return false;
        }
        return config.autoActivateSlimeForm || (player != null && SlimeFormState.isActive(player));
    }

    /** Returns the swamp position to use instead of the given world spawn, if one exists. */
    public static Optional<BlockPos> find(ServerLevel level, BlockPos worldSpawn) {
        synchronized (CACHE) {
            CachedSwamp cached = CACHE.get(level);
            if (cached != null && cached.worldSpawn().equals(worldSpawn)) {
                return cached.swamp();
            }
        }

        Pair<BlockPos, Holder<Biome>> result = level.findClosestBiome3d(
                biome -> biome.is(Biomes.SWAMP),
                worldSpawn,
                SEARCH_RADIUS,
                HORIZONTAL_STEP,
                VERTICAL_STEP);
        Optional<BlockPos> swamp = Optional.ofNullable(result).map(Pair::getFirst);
        if (swamp.isEmpty()) {
            SlimeFormMod.LOGGER.warn("[slimeform] No swamp found within {} blocks of world spawn {}",
                    SEARCH_RADIUS, worldSpawn);
        }
        synchronized (CACHE) {
            CACHE.put(level, new CachedSwamp(worldSpawn.immutable(), swamp));
        }
        return swamp;
    }
}
