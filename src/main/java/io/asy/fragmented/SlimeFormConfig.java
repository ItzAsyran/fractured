package io.asy.fragmented;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SlimeFormConfig {
    public static final int MIN_MAX_SLIME_SIZE = 1;
    public static final int MAX_MAX_SLIME_SIZE = 10;
    public static final int MIN_SPLIT_DURATION_SECONDS = 1;
    public static final int MAX_SPLIT_DURATION_SECONDS = 300;
    public static final int MIN_SLIME_BALLS_REQUIRED = 1;
    public static final int MAX_SLIME_BALLS_REQUIRED = 64;
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
    public static final int MIN_RECOVERY_REFORM_SAFETY_RADIUS = 2;
    public static final int MAX_RECOVERY_REFORM_SAFETY_RADIUS = 32;
    public static final double MIN_ABSORB_CAPACITY_FACTOR = 0.01D;
    public static final double MAX_ABSORB_CAPACITY_FACTOR = 2.0D;
    public static final int MIN_ABSORB_COOLDOWN_TICKS = 0;
    public static final int MAX_ABSORB_COOLDOWN_TICKS = 6000;
    public static final double MIN_ABSORB_DIGEST_BASE_DPS = 0.1D;
    public static final double MAX_ABSORB_DIGEST_BASE_DPS = 50.0D;
    public static final double MIN_ABSORB_DIGEST_DPS_PER_SIZE = 0.0D;
    public static final double MAX_ABSORB_DIGEST_DPS_PER_SIZE = 10.0D;
    public static final int MIN_ABSORB_DIGEST_INTERVAL_TICKS = 1;
    public static final int MAX_ABSORB_DIGEST_INTERVAL_TICKS = 200;
    public static final double MIN_ABSORB_DISSOLVE_GAIN_PER_UNIT = 0.0D;
    public static final double MAX_ABSORB_DISSOLVE_GAIN_PER_UNIT = 50.0D;
    public static final double MIN_ABSORB_STRUGGLE_JUMP_GAIN = 0.5D;
    public static final double MAX_ABSORB_STRUGGLE_JUMP_GAIN = 50.0D;
    public static final double MIN_ABSORB_STRUGGLE_HIT_GAIN = 0.5D;
    public static final double MAX_ABSORB_STRUGGLE_HIT_GAIN = 50.0D;
    public static final double MIN_ABSORB_STRUGGLE_DECAY = 0.0D;
    public static final double MAX_ABSORB_STRUGGLE_DECAY = 50.0D;
    public static final double MIN_ABSORB_FULLNESS_SLOWDOWN = 0.0D;
    public static final double MAX_ABSORB_FULLNESS_SLOWDOWN = 0.9D;
    public static final int MIN_ABSORB_INNER_OPACITY_PERCENT = 0;
    public static final int MAX_ABSORB_INNER_OPACITY_PERCENT = 100;
    public static final double MIN_ABSORB_FIT_TARGET_SCALE = 0.05D;
    public static final double MAX_ABSORB_FIT_TARGET_SCALE = 1.0D;
    public static final int MIN_ABSORB_WILD_SLIME_RANGE = 1;
    public static final int MAX_ABSORB_WILD_SLIME_RANGE = 128;

    public int maxSlimeSize = 5;

    public boolean autoActivateSlimeForm = false;

    public int splitDurationSeconds = 30;

    public boolean recoveryFleePathDebug = false;

    public boolean recoveryFleeDangerDebug = false;

    public boolean recoveryLineageDebug = false;

    public int recoveryReformSafetyRadius = 12;

    public boolean recoveryHostileReformBlock = true;

    public boolean recoveryReformCost = true;

    public int slimeBallsRequired = 1;

    public double riderOffsetX = 0.0D;

    public double riderOffsetYPerSize = 0.0D;

    public double riderOffsetZ = 0.0D;

    public boolean slimeWaterBehavior = true;

    public boolean afkDormantEnabled = true;

    public boolean afkDormantDebug = false;

    public boolean afkDormantHudDebug = false;

    public boolean floatingItemDisplays = true;

    public boolean slimeFootstepParticles = true;

    /** Master switch for every slime sound the mod plays or substitutes for a player. */
    public boolean slimeSoundsEnabled = true;
    public boolean slimeSoundFootsteps = true;
    public boolean slimeSoundJump = true;
    public boolean slimeSoundLanding = true;
    public boolean slimeSoundFall = true;
    public boolean slimeSoundHurt = true;
    public boolean slimeSoundDeath = true;
    public boolean slimeSoundAttack = true;
    public boolean slimeSoundGrowth = true;

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
    public boolean itemDebugShowAxes = false;

    public boolean doPhaseEnabled = false;

    /** Temporary bounded diagnostics for morph phase collision/suffocation. */
    public boolean phaseDebugEnabled = false;

    public boolean slimeChunksEnabled = false;

    public int slimeChunkChance = 50;

    public boolean slimeMorphEnabled = false;

    public boolean swampSpawnEnabled = false;

    public int playerTransparencyPercent = 0;

    public boolean playerTransparencySlimeTint = false;

    public boolean playerTransparencySlimeShell = false;

    public int slimeMorphTransformSeconds = 2;

    public int slimeMorphExitSeconds = 5;

    public int afkInactivitySeconds = 300;

    public boolean developerMode = false;

    public boolean absorbEnabled = true;
    public boolean absorbPlayersEnabled = true;
    public boolean absorbBossesEnabled = true;
    public double absorbCapacityFactor = 0.2D;
    public int absorbReabsorbCooldownTicks = 60;
    public float absorbDigestBaseDps = 1.0F;
    public float absorbDigestDpsPerSizeAbove2 = 0.25F;
    public int absorbDigestIntervalTicks = 20;
    public float absorbDissolveHealthPerUnit = 3.0F;
    public float absorbDissolveHungerPerUnit = 3.0F;
    public float absorbStruggleJumpGain = 6.0F;
    public float absorbStruggleHitGain = 3.0F;
    public float absorbStruggleDecayPerSecond = 4.0F;
    public double absorbFullnessMaxSlowdown = 0.25D;
    public int absorbInnerOpacityPercent = 25;
    public double absorbFitTargetScale = 0.72D;
    public List<String> absorbBlockedEntities = new ArrayList<>(List.of(
            "minecraft:ender_dragon",
            "minecraft:slime",
            "minecraft:magma_cube"
    ));
    public List<String> absorbHostileOverrides = new ArrayList<>();
    public List<String> absorbPassiveOverrides = new ArrayList<>();
    public int absorbWildSlimeRange = 24;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve(SlimeFormMod.MOD_ID + ".json");
    private static SlimeFormConfig INSTANCE = new SlimeFormConfig();

    public static void initialize() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                SlimeFormConfig loaded = GSON.fromJson(reader, SlimeFormConfig.class);
                if (loaded != null) {
                    INSTANCE = loaded;
                    return;
                }
            } catch (Exception e) {
                SlimeFormMod.LOGGER.error("[slimeform] Failed to load config from {}, using defaults", CONFIG_PATH, e);
            }
        }
        save();
    }

    public static SlimeFormConfig get() {
        return INSTANCE;
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (Exception e) {
            SlimeFormMod.LOGGER.error("[slimeform] Failed to save config to {}", CONFIG_PATH, e);
        }
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

    public double effectiveAbsorbCapacityFactor() {
        return clamp(absorbCapacityFactor, MIN_ABSORB_CAPACITY_FACTOR, MAX_ABSORB_CAPACITY_FACTOR);
    }

    public int effectiveAbsorbReabsorbCooldownTicks() {
        return clamp(absorbReabsorbCooldownTicks, MIN_ABSORB_COOLDOWN_TICKS, MAX_ABSORB_COOLDOWN_TICKS);
    }

    public float effectiveAbsorbDigestBaseDps() {
        return (float) clamp(absorbDigestBaseDps, MIN_ABSORB_DIGEST_BASE_DPS, MAX_ABSORB_DIGEST_BASE_DPS);
    }

    public float effectiveAbsorbDigestDpsPerSizeAbove2() {
        return (float) clamp(absorbDigestDpsPerSizeAbove2, MIN_ABSORB_DIGEST_DPS_PER_SIZE, MAX_ABSORB_DIGEST_DPS_PER_SIZE);
    }

    public int effectiveAbsorbDigestIntervalTicks() {
        return clamp(absorbDigestIntervalTicks, MIN_ABSORB_DIGEST_INTERVAL_TICKS, MAX_ABSORB_DIGEST_INTERVAL_TICKS);
    }

    public float effectiveAbsorbDissolveHealthPerUnit() {
        return (float) clamp(absorbDissolveHealthPerUnit, MIN_ABSORB_DISSOLVE_GAIN_PER_UNIT, MAX_ABSORB_DISSOLVE_GAIN_PER_UNIT);
    }

    public float effectiveAbsorbDissolveHungerPerUnit() {
        return (float) clamp(absorbDissolveHungerPerUnit, MIN_ABSORB_DISSOLVE_GAIN_PER_UNIT, MAX_ABSORB_DISSOLVE_GAIN_PER_UNIT);
    }

    public float effectiveAbsorbStruggleJumpGain() {
        return (float) clamp(absorbStruggleJumpGain, MIN_ABSORB_STRUGGLE_JUMP_GAIN, MAX_ABSORB_STRUGGLE_JUMP_GAIN);
    }

    public float effectiveAbsorbStruggleHitGain() {
        return (float) clamp(absorbStruggleHitGain, MIN_ABSORB_STRUGGLE_HIT_GAIN, MAX_ABSORB_STRUGGLE_HIT_GAIN);
    }

    public float effectiveAbsorbStruggleDecayPerSecond() {
        return (float) clamp(absorbStruggleDecayPerSecond, MIN_ABSORB_STRUGGLE_DECAY, MAX_ABSORB_STRUGGLE_DECAY);
    }

    public double effectiveAbsorbFullnessMaxSlowdown() {
        return clamp(absorbFullnessMaxSlowdown, MIN_ABSORB_FULLNESS_SLOWDOWN, MAX_ABSORB_FULLNESS_SLOWDOWN);
    }

    public int effectiveAbsorbInnerOpacityPercent() {
        return clamp(absorbInnerOpacityPercent, MIN_ABSORB_INNER_OPACITY_PERCENT, MAX_ABSORB_INNER_OPACITY_PERCENT);
    }

    public double effectiveAbsorbFitTargetScale() {
        return clamp(absorbFitTargetScale, MIN_ABSORB_FIT_TARGET_SCALE, MAX_ABSORB_FIT_TARGET_SCALE);
    }

    public int effectiveAbsorbWildSlimeRange() {
        return clamp(absorbWildSlimeRange, MIN_ABSORB_WILD_SLIME_RANGE, MAX_ABSORB_WILD_SLIME_RANGE);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
