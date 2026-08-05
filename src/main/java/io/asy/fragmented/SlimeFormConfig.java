package io.asy.fragmented;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;

@Config(name = SlimeFormMod.MOD_ID)
public class SlimeFormConfig implements me.shedaniel.autoconfig.ConfigData {
    public static final int MIN_MAX_SLIME_SIZE = 1;
    public static final int MAX_MAX_SLIME_SIZE = 10;
    public static final int MIN_SPLIT_DURATION_SECONDS = 1;
    public static final int MAX_SPLIT_DURATION_SECONDS = 300;
    public static final int MIN_SLIME_BALLS_REQUIRED = 1;
    public static final int MAX_SLIME_BALLS_REQUIRED = 64;
    public static final int MIN_PASSIVE_SPAWN_CHANCE = 0;
    public static final int MAX_PASSIVE_SPAWN_CHANCE = 100;
    public static final int MIN_PASSIVE_SPAWN_COOLDOWN_SECONDS = 5;
    public static final int MAX_PASSIVE_SPAWN_COOLDOWN_SECONDS = 600;
    public static final int MIN_MAX_NEARBY_SPAWNED_SLIMES = 0;
    public static final int MAX_MAX_NEARBY_SPAWNED_SLIMES = 16;
    public static final int MIN_AFK_INACTIVITY_SECONDS = 30;
    public static final int MAX_AFK_INACTIVITY_SECONDS = 3600;
    public static final double MIN_RIDER_OFFSET = -1.0D;
    public static final double MAX_RIDER_OFFSET = 1.0D;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = MIN_MAX_SLIME_SIZE, max = MAX_MAX_SLIME_SIZE)
    public int maxSlimeSize = 5;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = MIN_SPLIT_DURATION_SECONDS, max = MAX_SPLIT_DURATION_SECONDS)
    public int splitDurationSeconds = 30;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = MIN_SLIME_BALLS_REQUIRED, max = MAX_SLIME_BALLS_REQUIRED)
    public int slimeBallsRequired = 1;

    public boolean passiveSlimeSpawning = true;

    @ConfigEntry.BoundedDiscrete(min = MIN_PASSIVE_SPAWN_CHANCE, max = MAX_PASSIVE_SPAWN_CHANCE)
    public int passiveSlimeSpawnChance = 2;

    @ConfigEntry.BoundedDiscrete(
            min = MIN_PASSIVE_SPAWN_COOLDOWN_SECONDS,
            max = MAX_PASSIVE_SPAWN_COOLDOWN_SECONDS)
    public int passiveSlimeSpawnCooldownSeconds = 30;

    @ConfigEntry.BoundedDiscrete(
            min = MIN_MAX_NEARBY_SPAWNED_SLIMES,
            max = MAX_MAX_NEARBY_SPAWNED_SLIMES)
    public int maxNearbySpawnedSlimes = 4;

    public double riderOffsetX = 0.0D;

    public double riderOffsetYPerSize = 0.0D;

    public double riderOffsetZ = 0.0D;

    public boolean slimeWaterBehavior = true;

    public boolean afkDormantEnabled = true;

    public boolean showAfkTimerDebug = false;

    @ConfigEntry.BoundedDiscrete(
            min = MIN_AFK_INACTIVITY_SECONDS,
            max = MAX_AFK_INACTIVITY_SECONDS)
    public int afkInactivitySeconds = 300;

    public static void initialize() {
        AutoConfig.register(SlimeFormConfig.class, GsonConfigSerializer::new);
    }

    public static SlimeFormConfig get() {
        return AutoConfig.getConfigHolder(SlimeFormConfig.class).getConfig();
    }

    public int effectiveMaxSlimeSize() {
        return clamp(maxSlimeSize, MIN_MAX_SLIME_SIZE, MAX_MAX_SLIME_SIZE);
    }

    public int effectiveSplitDurationSeconds() {
        return clamp(splitDurationSeconds, MIN_SPLIT_DURATION_SECONDS, MAX_SPLIT_DURATION_SECONDS);
    }

    public int effectiveSlimeBallsRequired() {
        return clamp(slimeBallsRequired, MIN_SLIME_BALLS_REQUIRED, MAX_SLIME_BALLS_REQUIRED);
    }

    public int effectivePassiveSlimeSpawnChance() {
        return clamp(passiveSlimeSpawnChance, MIN_PASSIVE_SPAWN_CHANCE, MAX_PASSIVE_SPAWN_CHANCE);
    }

    public int effectivePassiveSlimeSpawnCooldownSeconds() {
        return clamp(
                passiveSlimeSpawnCooldownSeconds,
                MIN_PASSIVE_SPAWN_COOLDOWN_SECONDS,
                MAX_PASSIVE_SPAWN_COOLDOWN_SECONDS);
    }

    public int effectiveMaxNearbySpawnedSlimes() {
        return clamp(
                maxNearbySpawnedSlimes,
                MIN_MAX_NEARBY_SPAWNED_SLIMES,
                MAX_MAX_NEARBY_SPAWNED_SLIMES);
    }

    public int effectiveAfkInactivitySeconds() {
        return clamp(afkInactivitySeconds, MIN_AFK_INACTIVITY_SECONDS, MAX_AFK_INACTIVITY_SECONDS);
    }

    public double effectiveRiderOffsetX() {
        return clamp(riderOffsetX, MIN_RIDER_OFFSET, MAX_RIDER_OFFSET);
    }

    public double effectiveRiderOffsetYPerSize() {
        return clamp(riderOffsetYPerSize, MIN_RIDER_OFFSET, MAX_RIDER_OFFSET);
    }

    public double effectiveRiderOffsetZ() {
        return clamp(riderOffsetZ, MIN_RIDER_OFFSET, MAX_RIDER_OFFSET);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
