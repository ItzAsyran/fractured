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
    public static final int MIN_SLIME_CHUNK_CHANCE = 0;
    public static final int MAX_SLIME_CHUNK_CHANCE = 100;
    public static final int MIN_AFK_INACTIVITY_SECONDS = 30;
    public static final int MAX_AFK_INACTIVITY_SECONDS = 3600;
    public static final double MIN_RIDER_OFFSET = -4.0D;
    public static final double MAX_RIDER_OFFSET = 5.0D;
    public static final double MIN_ITEM_DISPLAY_OFFSET = -4.0D;
    public static final double MAX_ITEM_DISPLAY_OFFSET = 5.0D;
    public static final double MIN_ITEM_DISPLAY_SCALE = 0.05D;
    public static final double MAX_ITEM_DISPLAY_SCALE = 2.0D;
    public static final double MIN_ITEM_DISPLAY_ROTATION = -360.0D;
    public static final double MAX_ITEM_DISPLAY_ROTATION = 360.0D;
    public static final double MIN_ITEM_DISPLAY_BOB = 0.0D;
    public static final double MAX_ITEM_DISPLAY_BOB = 1.0D;
    public static final int MIN_PLAYER_TRANSPARENCY = 0;
    public static final int MAX_PLAYER_TRANSPARENCY = 100;

    @ConfigEntry.Gui.Tooltip
    public int maxSlimeSize = 5;

    @ConfigEntry.Gui.Tooltip
    public boolean autoActivateSlimeForm = false;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = MIN_SPLIT_DURATION_SECONDS, max = MAX_SPLIT_DURATION_SECONDS)
    public int splitDurationSeconds = 30;

    @ConfigEntry.Gui.Tooltip
    public boolean recoveryFleePathDebug = false;

    @ConfigEntry.Gui.Tooltip
    public boolean recoveryFleeDangerDebug = false;

    @ConfigEntry.Gui.Tooltip
    public boolean recoveryLineageDebug = false;

    @ConfigEntry.Gui.Tooltip
    public boolean recoveryHostileReformBlock = true;

    @ConfigEntry.Gui.Tooltip
    public boolean recoveryReformCost = true;

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

    public boolean afkDormantDebug = false;

    @ConfigEntry.Gui.Tooltip
    public boolean afkDormantHudDebug = false;

    public boolean floatingItemDisplays = true;

    public boolean slimeFootstepParticles = true;

    public double itemMainHandOffsetX = 0.0D;
    public double itemMainHandOffsetY = 0.0D;
    public double itemMainHandOffsetZ = 0.0D;
    public double itemOffHandOffsetX = 0.0D;
    public double itemOffHandOffsetY = 0.0D;
    public double itemOffHandOffsetZ = 0.0D;
    public double itemDisplayScale = 0.3D;
    public double itemDisplayRotationX = 90.0D;
    public double itemDisplayRotationY = 0.0D;
    public double itemDisplayRotationZ = 0.0D;
    public double itemDisplayBobAmplitude = 0.01D;

    public boolean doPhaseEnabled = false;

    /** Temporary bounded diagnostics for morph phase collision/suffocation. */
    public boolean phaseDebugEnabled = false;

    @ConfigEntry.Gui.Tooltip
    public boolean slimeChunksEnabled = false;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = MIN_SLIME_CHUNK_CHANCE, max = MAX_SLIME_CHUNK_CHANCE)
    public int slimeChunkChance = 50;

    @ConfigEntry.Gui.Tooltip
    public boolean slimeMorphEnabled = false;

    @ConfigEntry.Gui.Tooltip
    public boolean swampSpawnEnabled = false;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = MIN_PLAYER_TRANSPARENCY, max = MAX_PLAYER_TRANSPARENCY)
    public int playerTransparencyPercent = 0;

    @ConfigEntry.Gui.Tooltip
    public boolean playerTransparencySlimeTint = false;

    @ConfigEntry.Gui.Tooltip
    public boolean playerTransparencySlimeShell = false;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 10)
    public int slimeMorphTransformSeconds = 2;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 15)
    public int slimeMorphExitSeconds = 5;

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

    public static void save() {
        AutoConfig.getConfigHolder(SlimeFormConfig.class).save();
    }

    public int effectiveMaxSlimeSize() {
        return Math.max(MIN_MAX_SLIME_SIZE, maxSlimeSize);
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

    public int effectiveSlimeChunkChance() {
        return clamp(slimeChunkChance, MIN_SLIME_CHUNK_CHANCE, MAX_SLIME_CHUNK_CHANCE);
    }

    public int effectiveAfkInactivitySeconds() {
        return clamp(afkInactivitySeconds, MIN_AFK_INACTIVITY_SECONDS, MAX_AFK_INACTIVITY_SECONDS);
    }

    public int effectiveSlimeMorphTransformTicks() {
        return Math.max(1, slimeMorphTransformSeconds) * 20;
    }

    public int effectiveSlimeMorphExitTicks() {
        return Math.max(1, slimeMorphExitSeconds) * 20;
    }

    public int effectivePlayerTransparencyPercent() {
        return clamp(playerTransparencyPercent, MIN_PLAYER_TRANSPARENCY, MAX_PLAYER_TRANSPARENCY);
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

    public double effectiveItemMainHandOffsetX() {
        return clamp(itemMainHandOffsetX, MIN_ITEM_DISPLAY_OFFSET, MAX_ITEM_DISPLAY_OFFSET);
    }

    public double effectiveItemMainHandOffsetY() {
        return clamp(itemMainHandOffsetY, MIN_ITEM_DISPLAY_OFFSET, MAX_ITEM_DISPLAY_OFFSET);
    }

    public double effectiveItemMainHandOffsetZ() {
        return clamp(itemMainHandOffsetZ, MIN_ITEM_DISPLAY_OFFSET, MAX_ITEM_DISPLAY_OFFSET);
    }

    public double effectiveItemOffHandOffsetX() {
        return clamp(itemOffHandOffsetX, MIN_ITEM_DISPLAY_OFFSET, MAX_ITEM_DISPLAY_OFFSET);
    }

    public double effectiveItemOffHandOffsetY() {
        return clamp(itemOffHandOffsetY, MIN_ITEM_DISPLAY_OFFSET, MAX_ITEM_DISPLAY_OFFSET);
    }

    public double effectiveItemOffHandOffsetZ() {
        return clamp(itemOffHandOffsetZ, MIN_ITEM_DISPLAY_OFFSET, MAX_ITEM_DISPLAY_OFFSET);
    }

    public double effectiveItemDisplayScale() {
        return clamp(itemDisplayScale, MIN_ITEM_DISPLAY_SCALE, MAX_ITEM_DISPLAY_SCALE);
    }

    public double effectiveItemDisplayRotationX() {
        return clamp(itemDisplayRotationX, MIN_ITEM_DISPLAY_ROTATION, MAX_ITEM_DISPLAY_ROTATION);
    }

    public double effectiveItemDisplayRotationY() {
        return clamp(itemDisplayRotationY, MIN_ITEM_DISPLAY_ROTATION, MAX_ITEM_DISPLAY_ROTATION);
    }

    public double effectiveItemDisplayRotationZ() {
        return clamp(itemDisplayRotationZ, MIN_ITEM_DISPLAY_ROTATION, MAX_ITEM_DISPLAY_ROTATION);
    }

    public double effectiveItemDisplayBobAmplitude() {
        return clamp(itemDisplayBobAmplitude, MIN_ITEM_DISPLAY_BOB, MAX_ITEM_DISPLAY_BOB);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
