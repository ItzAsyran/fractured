package io.asy.fragmented;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.GameType;
import net.minecraft.resources.ResourceKey;
import io.asy.fragmented.mixin.SlimeServerConnectionAccessor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.UUID;

/**
 * Split / recovery / reform lifecycle for slime-form players.
 *
 * <p>Extracted verbatim from {@link SlimeFormMod}. Covers: fragment lineage helpers, the per-tick
 * recovery loop, hostile-safety handling, fragment flee-path logic, inventory snapshot/restore,
 * and reform/failure effects. {@code SlimeFormMod} keeps thin forwarding methods so existing
 * mixins and goals did not need to change.
 */
public final class SlimeRecoveryManager {
    private SlimeRecoveryManager() {
    }

    /** How often (ticks) shared recovery scans refresh. 5 ticks = 0.25 s, imperceptible for AI. */
    private static final int ASSIST_INTERVAL_TICKS = 5;
    private static final int HOSTILE_CHECK_INTERVAL_TICKS = 5;
    /** Hostile mobs farther than this from a fragment are ignored when judging its safety. */
    private static final double THREAT_EVAL_RADIUS = 24.0D;
    /** Only the nearest few mobs get a (comparatively expensive) reachability check. */
    private static final int THREAT_EVAL_MAX_MOBS = 3;
    private static final int THREAT_EVAL_INTERVAL_TICKS = 15;
    /** Flying mobs have no usable ground path, so treat them as a threat inside this range. */
    private static final double AIRBORNE_THREAT_RADIUS = 10.0D;
    /** All fragments must be within this many blocks of the anchor before the player may reform. */
    private static final double GROUP_RADIUS = 3.0D;
    private static final int GROUP_HOLD_TICKS = 20;
    /** If fragments cannot gather within this long after the countdown, reform anyway. */
    private static final int GROUP_WAIT_MAX_TICKS = 20 * 45;
    private static final int ANCHOR_REEVALUATE_TICKS = 10;
    /**
     * If calm fragments stay split from the anchor this long, hand the anchor role to a
     * stranded fragment so the others walk to it instead (this fixes one-way routes such as
     * a fragment that dropped down a ledge). Tried at most a few times per recovery.
     */
    private static final int REGROUP_RESCUE_AFTER_TICKS = 20 * 8;
    private static final int REGROUP_RESCUE_MAX = 3;
    /**
     * A fragment only counts as safe once it is at least this far from every hostile mob, even
     * ones that cannot walk to it. A mob behind a one-block gap can still hit something just
     * outside it, so "cannot reach me" alone would leave fragments hugging the wall.
     */
    private static final double SAFE_STANDOFF_DISTANCE = 6.0D;
    /** A regroup route is blocked if any hostile mob stands this close to part of it. */
    private static final double REGROUP_PATH_SAFETY_RADIUS = SAFE_STANDOFF_DISTANCE;
    private static final double SIBLING_SPREAD_RANGE = 6.0D;
    /** Latest regroup status per fragment (by entity UUID); read by the gathering message and debug label. */
    private static final Map<UUID, String> REGROUP_STATUS = new ConcurrentHashMap<>();
    private static final double SIBLING_SPREAD_WEIGHT = 2.0D;
    private static final long NEVER = Long.MIN_VALUE / 2;
    private static final int SIZE_ONE_RECOVERY_MAX_TICKS = 3 * 60 * SlimeFormMod.RECOVERY_COUNTDOWN_INTERVAL_TICKS;
    public static final int RECOVERY_PROGRESS_BAR_WIDTH = 20;
    public static final int REFORM_PARTICLE_COUNT = 40;
    static final double RECOVERY_FLEE_THREAT_RADIUS = 32.0D;
    private static final int RECOVERY_FLEE_DIRECTION_COUNT = 8;
    private static final double[] RECOVERY_FLEE_WAYPOINT_DISTANCES = {8.0D, 12.0D, 16.0D};
    private static final Map<UUID, Recovery> RECOVERIES = new HashMap<>();
    private static final String ABANDONED_RECOVERY_TAG_PREFIX = "slimeform.recovery_abandoned=";

    public static String createRecoveryLineageId(ServerPlayer player) {
        return player.getUUID() + "_recovery_" + UUID.randomUUID();
    }

    public static boolean hasRecoveryLineage(Slime slime) {
        return getRecoveryLineage(slime) != null;
    }

    public static String getRecoveryLineage(Slime slime) {
        return ((SlimeRecoveryLineage) slime).slimeform$getRecoveryLineage();
    }

    public static UUID getRecoveryParent(Slime slime) {
        return ((SlimeRecoveryLineage) slime).slimeform$getRecoveryParent();
    }

    public static int getRecoveryGeneration(Slime slime) {
        return ((SlimeRecoveryLineage) slime).slimeform$getRecoveryGeneration();
    }

    public static String getRecoveryDebugLabel(Slime slime) {
        String lineageId = getRecoveryLineage(slime);
        if (lineageId == null) {
            return null;
        }
        String compactId = lineageId.replace("-", "");
        int recoverySeparator = compactId.indexOf("_recovery_");
        if (recoverySeparator >= 0) {
            compactId = compactId.substring(recoverySeparator + "_recovery_".length());
        }
        compactId = compactId.substring(0, Math.min(4, compactId.length())).toUpperCase(java.util.Locale.ROOT);
        String status = REGROUP_STATUS.get(slime.getUUID());
        return "Recovery " + compactId + " · G" + getRecoveryGeneration(slime)
                + (status == null ? "" : " · " + status);
    }

    public static void assignRecoveryLineage(
            Slime slime, String lineageId, UUID parentId, int generation) {
        slime.addTag(SlimeFormMod.PLAYER_RECOVERY_SLIME_TAG);
        ((SlimeRecoveryLineage) slime).slimeform$setRecoveryLineage(lineageId, parentId, generation);
        trackRecoveryLineageEntity(slime);
        SlimeFormMod.LOGGER.info(
                "[slimeform] Recovery slime created uuid={} size={} lineage={} parent={} generation={}",
                slime.getUUID(),
                slime.getSize(),
                lineageId,
                parentId,
                generation);
    }

    public static boolean assignRecoveryLineage(Slime parent, Slime child) {
        String lineageId = getRecoveryLineage(parent);
        if (lineageId == null) {
            return false;
        }
        assignRecoveryLineage(
                child,
                lineageId,
                parent.getUUID(),
                getRecoveryGeneration(parent) + 1);
        return true;
    }

    public static boolean isPlayerOriginSlime(Slime slime) {
        return slime.entityTags().contains(SlimeFormMod.PLAYER_RECOVERY_SLIME_TAG)
                || slime.entityTags().contains(SlimeFormMod.PLAYER_DORMANT_SLIME_TAG)
                || hasRecoveryLineage(slime);
    }

    public static void trackRecoveryLineageEntity(Slime slime) {
        String lineageId = getRecoveryLineage(slime);
        if (lineageId == null) {
            return;
        }
        for (Recovery recovery : RECOVERIES.values()) {
            if (lineageId.equals(recovery.lineageId())) {
                recovery.lineageEntityIds().add(slime.getUUID());
            }
        }
    }

    public static void beginRecovery(
            ServerPlayer player,
            List<Slime> splitSlimes,
            GameType previousGameMode,
            LivingEntity originalKiller) {
        if (splitSlimes.isEmpty()) {
            return;
        }

        InventorySnapshot inventory = captureAndClearInventory(player);
        RECOVERIES.put(player.getUUID(), new Recovery(
                getRecoveryLineage(splitSlimes.get(0)),
                originalKiller == null ? null : originalKiller.getUUID(),
                player.level().dimension(),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot(),
                previousGameMode,
                SlimeFormMod.getSplitDurationTicks(),
                inventory));
        Recovery recovery = RECOVERIES.get(player.getUUID());
        if (recovery != null) {
            splitSlimes.forEach(slime -> recovery.lineageEntityIds().add(slime.getUUID()));
            // Every fragment is one size below the size the player died at, so the
            // original size is the strongest fragment + 1 (never above the maximum).
            int strongestAtStart = splitSlimes.stream()
                    .mapToInt(Slime::getSize)
                    .max()
                    .orElse(SlimeFormState.MIN_SIZE);
            recovery.originalSize = Math.min(strongestAtStart + 1, SlimeFormState.getMaxSize());
        }
        SlimeFormMod.LOGGER.info("[slimeform] Recovery started for {} ({} ticks) at {} ({}, {}, {})",
                player.getName().getString(),
                SlimeFormMod.getSplitDurationTicks(),
                player.level().dimension().identifier(),
                player.getX(),
                player.getY(),
                player.getZ());
    }

    /**
     * Re-sends the camera packet because the client can reset its camera to
     * the player while processing the death/immediate-respawn sequence.
     */
    public static void syncRecoveryCamera(ServerPlayer player, Entity target) {
        player.setCamera(target);
        player.connection.send(new ClientboundSetCameraPacket(target));
    }

    static void tickRecoveries(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Recovery>> iterator = RECOVERIES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Recovery> entry = iterator.next();
            Recovery recovery = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                // Safety net: normally handlePlayerDisconnect already ended the recovery.
                cleanupSplitSlimes(server, recovery);
                dropInventory(server, recovery);
                iterator.remove();
                continue;
            }

            List<Slime> lineageSlimes = resolveLineageSlimes(server, recovery);
            if (!lineageSlimes.isEmpty()) {
                recovery.lastKnownPosition = lineageSlimes.get(lineageSlimes.size() - 1).position();
            }
            List<Slime> survivingSlimes = new ArrayList<>();
            for (Slime lineageSlime : lineageSlimes) {
                if (lineageSlime.isAlive() && !lineageSlime.isRemoved()) {
                    survivingSlimes.add(lineageSlime);
                }
            }
            if (survivingSlimes.isEmpty()) {
                if (recovery.emptyLineageChecks() == 0) {
                    recovery.incrementEmptyLineageChecks();
                    continue;
                }
                completeFailedRecovery(server, player, recovery, iterator);
                continue;
            }
            recovery.resetEmptyLineageChecks();
            recovery.survivors = survivingSlimes;
            updateRegroupAnchor(recovery, survivingSlimes);
            Slime anchor = findSurvivor(survivingSlimes, recovery.anchorId);
            int groupedCount = anchor == null ? 0 : countGroupedFragments(survivingSlimes, anchor);
            if (anchor != null
                    && groupedCount < survivingSlimes.size()
                    && recovery.rescueCount < REGROUP_RESCUE_MAX
                    && !anyFragmentThreatened(recovery, survivingSlimes)) {
                // Everyone is calm but someone is not joining the anchor. After a while let the
                // stranded fragment be the anchor, so the others walk to it instead.
                recovery.strandedTicks++;
                if (recovery.strandedTicks >= REGROUP_RESCUE_AFTER_TICKS) {
                    Slime straggler = null;
                    double farthest = 0.0D;
                    for (Slime fragment : survivingSlimes) {
                        double distance = fragment.distanceToSqr(anchor);
                        if (fragment != anchor
                                && distance > GROUP_RADIUS * GROUP_RADIUS
                                && distance > farthest) {
                            straggler = fragment;
                            farthest = distance;
                        }
                    }
                    if (straggler != null) {
                        recovery.anchorId = straggler.getUUID();
                        recovery.anchorStamp = recovery.recoveryElapsedTicks;
                        recovery.rescueCount++;
                        anchor = straggler;
                        groupedCount = countGroupedFragments(survivingSlimes, anchor);
                    }
                    recovery.strandedTicks = 0;
                }
            } else {
                recovery.strandedTicks = 0;
            }
            recovery.groupedTicks = anchor != null && groupedCount == survivingSlimes.size()
                    ? recovery.groupedTicks + 1
                    : 0;
            if (recovery.recoveryElapsedTicks % ASSIST_INTERVAL_TICKS == 0) {
                continueRecoveryDefense(server, recovery, survivingSlimes);
                coordinateRecoveryAssistance(recovery, survivingSlimes);
            }
            updateRecoveryFleeDangerBar(recovery, player, survivingSlimes);
            // Keep watching the fragment the player picked. If it died (or none was picked
            // yet), fall back to the anchor, then to any survivor.
            Slime cameraTarget = findSurvivor(survivingSlimes, recovery.cameraSlimeId);
            if (cameraTarget == null) {
                cameraTarget = anchor != null ? anchor : survivingSlimes.get(0);
            }
            recovery.cameraSlimeId = cameraTarget.getUUID();
            if (recovery.cameraResyncTicksRemaining > 0) {
                syncRecoveryCamera(player, cameraTarget);
                recovery.cameraResyncTicksRemaining--;
            } else {
                player.setCamera(cameraTarget);
            }

            recovery.recoveryElapsedTicks++;
            boolean sizeOneRecovery = survivingSlimes.stream()
                    .allMatch(slime -> slime.getSize() == SlimeFormState.MIN_SIZE);
            boolean reformCountdownComplete = false;
            if (sizeOneRecovery) {
                if (hasNearbyRecoveryHostileCached(recovery, survivingSlimes)) {
                    // Preserve the exact remaining countdown while danger is
                    // present. It resumes when the safety radius is clear.
                    if (recovery.recoveryElapsedTicks % SlimeFormMod.RECOVERY_COUNTDOWN_INTERVAL_TICKS == 0) {
                        player.sendOverlayMessage(createHostileReformStatus(recovery));
                    }
                } else {
                    if (!recovery.sizeOneReformCountdownStarted) {
                        recovery.sizeOneReformCountdownStarted = true;
                        recovery.ticksRemaining = recovery.totalTicks;
                    }
                    recovery.ticksRemaining--;
                    if (recovery.ticksRemaining % SlimeFormMod.RECOVERY_COUNTDOWN_INTERVAL_TICKS == 0
                            && recovery.ticksRemaining > 0) {
                        player.sendOverlayMessage(
                                createRecoveryProgressBar(recovery.ticksRemaining, recovery.totalTicks));
                    }
                    reformCountdownComplete = recovery.ticksRemaining <= 0;
                }
            } else {
                // Keep size 2+ recovery behavior timer-based and unchanged.
                recovery.ticksRemaining--;
                if (recovery.ticksRemaining % SlimeFormMod.RECOVERY_COUNTDOWN_INTERVAL_TICKS == 0
                        && recovery.ticksRemaining > 0) {
                    player.sendOverlayMessage(
                            createRecoveryProgressBar(recovery.ticksRemaining, recovery.totalTicks));
                }
                reformCountdownComplete = recovery.ticksRemaining <= 0;
            }

            if (recovery.ticksRemaining < 0) {
                recovery.ticksRemaining = 0;
            }
            boolean maxTimeoutReached = recovery.recoveryElapsedTicks >= SIZE_ONE_RECOVERY_MAX_TICKS;
            // The countdown only says "long enough"; the player may not reform until every
            // fragment has gathered around the anchor (and, for size 1 recoveries, no
            // hostile is close by).
            boolean readyToReform = reformCountdownComplete
                    && recovery.groupedTicks >= GROUP_HOLD_TICKS
                    && !(sizeOneRecovery && hasNearbyRecoveryHostileCached(recovery, survivingSlimes));
            if (reformCountdownComplete && !readyToReform) {
                recovery.gatherWaitTicks++;
                if (recovery.recoveryElapsedTicks % SlimeFormMod.RECOVERY_COUNTDOWN_INTERVAL_TICKS == 0) {
                    String detail = " · no safe anchor";
                    if (anchor != null) {
                        detail = "";
                        for (int index = 0; index < survivingSlimes.size(); index++) {
                            Slime fragment = survivingSlimes.get(index);
                            if (fragment != anchor
                                    && fragment.distanceToSqr(anchor) > GROUP_RADIUS * GROUP_RADIUS) {
                                String status = REGROUP_STATUS.get(fragment.getUUID());
                                detail = " · fragment " + (index + 1) + ": "
                                        + (status == null ? "unknown" : status);
                                break;
                            }
                        }
                    }
                    player.sendOverlayMessage(Component.literal(
                            "Gathering fragments... " + groupedCount + "/" + survivingSlimes.size() + detail)
                            .withStyle(ChatFormatting.YELLOW));
                }
            }
            boolean forceReform = (sizeOneRecovery && maxTimeoutReached)
                    || recovery.gatherWaitTicks >= GROUP_WAIT_MAX_TICKS;
            if (!readyToReform && !forceReform) {
                continue;
            }

            ServerPlayer replacement = server.getPlayerList().respawn(
                    player, false, Entity.RemovalReason.KILLED);
            if (replacement == null) {
                SlimeFormMod.LOGGER.warn("[slimeform] Respawn returned no player for {}", entry.getKey());
                cleanupSplitSlimes(server, recovery);
                iterator.remove();
                continue;
            }

            SlimeServerConnectionAccessor connection =
                    (SlimeServerConnectionAccessor) replacement.connection;
            connection.slimeform$setPlayer(replacement);

            GameType restoredGameMode = recovery.previousGameMode();
            replacement.gameMode.changeGameModeForPlayer(restoredGameMode);
            replacement.setGameMode(restoredGameMode);
            replacement.onUpdateAbilities();
            replacement.connection.send(new ClientboundGameEventPacket(
                    ClientboundGameEventPacket.CHANGE_GAME_MODE,
                    restoredGameMode.getId()));

            Slime destination = anchor != null ? anchor : survivingSlimes.get(0);
            ServerLevel recoveryLevel = (ServerLevel) destination.level();
            Vec3 destinationPosition = destination.position();
            int reformedSize = reformSizeFor(recovery, survivingSlimes, destination);
            cleanupSplitSlimes(server, recovery);
            replacement.teleportTo(
                    recoveryLevel,
                    destinationPosition.x,
                    destinationPosition.y,
                    destinationPosition.z,
                    Set.of(),
                    recovery.yRot(),
                    recovery.xRot(),
                    false);
            replacement.setCamera(replacement);
            SlimeFormState.setSize(replacement, reformedSize);
            SlimeFormState.applyHealth(replacement, true);
            ensureReformRoom(replacement, recoveryLevel);
            restoreInventory(replacement, recovery.inventory());
            playReformEffects(recoveryLevel, replacement);
            replacement.sendOverlayMessage(Component.empty());
            if (reformedSize < recovery.originalSize) {
                replacement.sendSystemMessage(Component.literal(
                        "You have reformed, but smaller: size " + reformedSize + " (was "
                                + recovery.originalSize + "). Slime was lost along the way.")
                        .withStyle(ChatFormatting.YELLOW));
            } else {
                replacement.sendSystemMessage(Component.literal("You have reformed.")
                        .withStyle(ChatFormatting.GREEN));
            }
            SlimeFormMod.LOGGER.info("[slimeform] Recovery completed for {} in {} at ({}, {}, {}), "
                            + "gameMode()={}, internalMode={}, spectator={}, connectionPlayer={}",
                    replacement.getName().getString(),
                    recoveryLevel.dimension().identifier(),
                    replacement.getX(),
                    replacement.getY(),
                    replacement.getZ(),
                    replacement.gameMode(),
                    replacement.gameMode.getGameModeForPlayer(),
                    replacement.isSpectator(),
                    connection.slimeform$getPlayer().getUUID());
            iterator.remove();
        }
    }

    /**
     * Reform cost: you come back as big as the slime you managed to gather. Every fragment of
     * size s counts s * s (the same scaling as max health, so a size 2 player split into four
     * size 1 fragments is exactly four units), and the new size is the square root of the total,
     * rounded. It never exceeds the size you died at and never drops below 1. Only fragments that
     * reached the group count; stragglers are lost. With the cost option off, the old rule applies
     * (largest fragment + 1).
     */
    private static int reformSizeFor(Recovery recovery, List<Slime> survivors, Slime destination) {
        if (!SlimeFormConfig.get().recoveryReformCost) {
            int strongest = survivors.stream()
                    .mapToInt(Slime::getSize)
                    .max()
                    .orElse(SlimeFormState.MIN_SIZE);
            return strongest + 1;
        }
        double mass = 0.0D;
        for (Slime fragment : survivors) {
            if (fragment == destination
                    || fragment.distanceToSqr(destination) <= GROUP_RADIUS * GROUP_RADIUS) {
                mass += (double) fragment.getSize() * fragment.getSize();
            }
        }
        int size = (int) Math.round(Math.sqrt(mass));
        int cap = Math.max(SlimeFormState.MIN_SIZE, recovery.originalSize);
        return Math.max(SlimeFormState.MIN_SIZE, Math.min(cap, size));
    }

    /**
     * The reformed player is taller than a small fragment, so reforming inside a one-block gap
     * could leave them inside blocks. If the spot is blocked, move to the nearest free spot
     * (up first, then a short ring around it).
     */
    private static void ensureReformRoom(ServerPlayer player, ServerLevel level) {
        if (level.noCollision(player, player.getBoundingBox())) {
            return;
        }
        double[][] offsets = {
                {0.0D, 0.25D, 0.0D}, {0.0D, 0.5D, 0.0D}, {0.0D, 1.0D, 0.0D},
                {1.0D, 0.0D, 0.0D}, {-1.0D, 0.0D, 0.0D}, {0.0D, 0.0D, 1.0D}, {0.0D, 0.0D, -1.0D},
                {1.0D, 1.0D, 0.0D}, {-1.0D, 1.0D, 0.0D}, {0.0D, 1.0D, 1.0D}, {0.0D, 1.0D, -1.0D},
                {1.0D, 0.0D, 1.0D}, {1.0D, 0.0D, -1.0D}, {-1.0D, 0.0D, 1.0D}, {-1.0D, 0.0D, -1.0D},
                {2.0D, 0.0D, 0.0D}, {-2.0D, 0.0D, 0.0D}, {0.0D, 0.0D, 2.0D}, {0.0D, 0.0D, -2.0D},
                {2.0D, 1.0D, 0.0D}, {-2.0D, 1.0D, 0.0D}, {0.0D, 1.0D, 2.0D}, {0.0D, 1.0D, -2.0D}
        };
        for (double[] offset : offsets) {
            if (level.noCollision(player, player.getBoundingBox().move(offset[0], offset[1], offset[2]))) {
                player.teleportTo(
                        level,
                        player.getX() + offset[0],
                        player.getY() + offset[1],
                        player.getZ() + offset[2],
                        Set.of(),
                        player.getYRot(),
                        player.getXRot(),
                        false);
                return;
            }
        }
    }

    private static boolean hasNearbyRecoveryHostileCached(Recovery recovery, List<Slime> survivingSlimes) {
        long elapsed = recovery.recoveryElapsedTicks;
        if (!isFresh(elapsed, recovery.hostileNearbyStamp, HOSTILE_CHECK_INTERVAL_TICKS)) {
            recovery.hostileNearby = hasNearbyRecoveryHostile(recovery, survivingSlimes);
            recovery.hostileNearbyStamp = elapsed;
        }
        return recovery.hostileNearby;
    }

    /**
     * "Block Reform Near Hostiles": true while the block is enabled and any fragment is in
     * danger by the same rules fragments use to flee (a hostile mob can reach it, or any
     * hostile is within the standoff distance). A hostile that is merely nearby but cannot
     * reach a hidden fragment no longer blocks the reform.
     */
    private static boolean hasNearbyRecoveryHostile(Recovery recovery, List<Slime> survivingSlimes) {
        if (!SlimeFormConfig.get().recoveryHostileReformBlock) {
            return false;
        }
        return anyFragmentThreatened(recovery, survivingSlimes);
    }
    private static void updateRecoveryFleeDangerBar(
            Recovery recovery,
            ServerPlayer player,
            List<Slime> survivingSlimes) {
        ServerBossEvent bar = recovery.fleeDangerBar;
        if (!SlimeFormConfig.get().recoveryFleeDangerDebug) {
            bar.setVisible(false);
            return;
        }

        double nearestDistance = RECOVERY_FLEE_THREAT_RADIUS;
        for (Slime slime : survivingSlimes) {
            if (slime.getSize() != SlimeFormState.MIN_SIZE) {
                continue;
            }
            for (Mob threat : getRecoveryFragmentThreats(slime)) {
                nearestDistance = Math.min(nearestDistance, slime.distanceTo(threat));
            }
        }

        if (nearestDistance >= RECOVERY_FLEE_THREAT_RADIUS) {
            bar.setVisible(false);
            return;
        }

        double danger = 1.0D - nearestDistance / RECOVERY_FLEE_THREAT_RADIUS;
        bar.setProgress((float) Math.max(0.0D, Math.min(1.0D, danger)));
        bar.setColor(danger >= 0.66D
                ? BossEvent.BossBarColor.RED
                : danger >= 0.33D ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.GREEN);
        bar.setName(Component.literal(String.format(
                java.util.Locale.ROOT,
                "Recovery danger: %.1f blocks",
                nearestDistance)));
        bar.addPlayer(player);
        bar.setVisible(true);
    }

    private static void completeFailedRecovery(
            MinecraftServer server,
            ServerPlayer player,
            Recovery recovery,
            Iterator<Map.Entry<UUID, Recovery>> iterator) {
        cleanupSplitSlimes(server, recovery);
        ServerPlayer replacement = server.getPlayerList().respawn(
                player, false, Entity.RemovalReason.KILLED);
        if (replacement == null) {
            SlimeFormMod.LOGGER.warn("[slimeform] Respawn returned no player after failed recovery for {}", player.getUUID());
            iterator.remove();
            return;
        }

        SlimeServerConnectionAccessor connection =
                (SlimeServerConnectionAccessor) replacement.connection;
        connection.slimeform$setPlayer(replacement);

        GameType restoredGameMode = recovery.previousGameMode();
        replacement.gameMode.changeGameModeForPlayer(restoredGameMode);
        replacement.setGameMode(restoredGameMode);
        replacement.onUpdateAbilities();
        replacement.connection.send(new ClientboundGameEventPacket(
                ClientboundGameEventPacket.CHANGE_GAME_MODE,
                restoredGameMode.getId()));
        replacement.setCamera(replacement);
        SlimeFormState.setSize(replacement, SlimeFormState.getMaxSize());
        SlimeFormState.applyHealth(replacement, true);
        playRecoveryFailureEffects((ServerLevel) replacement.level(), replacement);
        dropInventory(server, recovery);
        replacement.sendSystemMessage(
                Component.literal("Recovery failed. ").withStyle(ChatFormatting.RED)
                        .append(Component.literal(
                                "All split slimes were lost; you respawned at your spawnpoint.")
                                .withStyle(ChatFormatting.GRAY)));
        SlimeFormMod.LOGGER.info("[slimeform] Failed recovery for {}; inventory was lost and player respawned at spawnpoint",
                replacement.getName().getString());
        iterator.remove();
    }

    /**
     * Resolves this recovery's fragments by UUID instead of scanning every entity in
     * every dimension each tick. Order is stable (registration order). Like the old
     * full scan, only entities in loaded chunks are found, and dead/removed ones are
     * still returned so callers can filter them as before.
     */
    private static List<Slime> resolveLineageSlimes(MinecraftServer server, Recovery recovery) {
        List<Slime> found = new ArrayList<>();
        String lineageId = recovery.lineageId();
        for (UUID id : recovery.lineageEntityIds()) {
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(id) instanceof Slime slime
                        && lineageId.equals(getRecoveryLineage(slime))) {
                    found.add(slime);
                    break;
                }
            }
        }
        return found;
    }

    private static boolean isFresh(long now, long stamp, int ttlTicks) {
        return now >= stamp && now - stamp < ttlTicks;
    }

    private static List<Slime> getLineageSlimes(MinecraftServer server, String lineageId) {
        List<Slime> lineageSlimes = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Slime slime && lineageId.equals(getRecoveryLineage(slime))) {
                    lineageSlimes.add(slime);
                }
            }
        }
        return lineageSlimes;
    }

    private static void continueRecoveryDefense(
            MinecraftServer server,
            Recovery recovery,
            List<Slime> survivingSlimes) {
        UUID originalKillerId = recovery.originalKillerId();
        if (originalKillerId == null || recovery.postAvenging()) {
            if (recovery.postAvenging()) {
                commandRecoverySlimesToAttackHostiles(survivingSlimes);
            }
            return;
        }

        ServerLevel level = server.getLevel(recovery.dimension());
        if (level == null) {
            return;
        }

        Entity originalKiller = level.getEntity(originalKillerId);
        if (originalKiller instanceof LivingEntity living && living.isAlive()) {
            return;
        }

        recovery.setPostAvenging();
        commandRecoverySlimesToAttackHostiles(survivingSlimes);
    }

    private static void commandRecoverySlimesToAttackHostiles(List<Slime> survivingSlimes) {
        List<Slime> attackingSlimes = survivingSlimes.stream()
                .filter(slime -> slime.getSize() > SlimeFormState.MIN_SIZE)
                .toList();
        if (attackingSlimes.isEmpty()) {
            return;
        }

        LivingEntity sharedTarget = attackingSlimes.stream()
                .map(Slime::getTarget)
                .filter(SlimeRecoveryManager::isRecoveryHostile)
                .findFirst()
                .orElse(null);

        if (sharedTarget == null) {
            double nearestDistance = Double.MAX_VALUE;
            for (Slime slime : attackingSlimes) {
                for (Mob mob : slime.level().getEntitiesOfClass(
                        Mob.class,
                        slime.getBoundingBox().inflate(32.0D),
                        SlimeRecoveryManager::isRecoveryHostile)) {
                    double distance = slime.distanceToSqr(mob);
                    if (distance < nearestDistance) {
                        sharedTarget = mob;
                        nearestDistance = distance;
                    }
                }
            }
        }

        if (sharedTarget == null) {
            return;
        }

        for (Slime slime : attackingSlimes) {
            slime.setTarget(sharedTarget);
        }
    }

    private static void coordinateRecoveryAssistance(
            Recovery recovery,
            List<Slime> survivingSlimes) {
        LivingEntity sharedTarget = findRecoveryHostileTarget(survivingSlimes);
        if (sharedTarget != null) {
            for (Slime slime : survivingSlimes) {
                if (slime.getSize() > SlimeFormState.MIN_SIZE) {
                    slime.setTarget(sharedTarget);
                } else {
                    slime.setTarget(null);
                }
            }
            assistNearbyNormalSlimes(recovery, survivingSlimes, sharedTarget);
        }

    }

    private static LivingEntity findRecoveryHostileTarget(List<Slime> survivingSlimes) {
        LivingEntity currentTarget = survivingSlimes.stream()
                .filter(slime -> slime.getSize() > SlimeFormState.MIN_SIZE)
                .map(Slime::getTarget)
                .filter(SlimeRecoveryManager::isRecoveryHostile)
                .findFirst()
                .orElse(null);
        if (currentTarget != null) {
            return currentTarget;
        }

        double nearestDistance = Double.MAX_VALUE;
        LivingEntity nearest = null;
        for (Slime splitSlime : survivingSlimes) {
            AABB area = splitSlime.getBoundingBox().inflate(15.0D);
            for (Mob mob : splitSlime.level().getEntitiesOfClass(
                    Mob.class,
                    area,
                    SlimeRecoveryManager::isRecoveryHostile)) {
                double distance = splitSlime.distanceToSqr(mob);
                if (distance < nearestDistance) {
                    nearest = mob;
                    nearestDistance = distance;
                }
            }

            for (Slime nearbySlime : splitSlime.level().getEntitiesOfClass(
                    Slime.class,
                    area,
                    slime -> isRecoveryHostile(slime.getTarget()))) {
                LivingEntity target = nearbySlime.getTarget();
                double distance = splitSlime.distanceToSqr(nearbySlime);
                if (target != null && distance < nearestDistance) {
                    nearest = target;
                    nearestDistance = distance;
                }
            }
        }
        return nearest;
    }

    private static void assistNearbyNormalSlimes(
            Recovery recovery,
            List<Slime> survivingSlimes,
            LivingEntity target) {
        for (Slime splitSlime : survivingSlimes) {
            for (Slime nearbySlime : splitSlime.level().getEntitiesOfClass(
                    Slime.class,
                    splitSlime.getBoundingBox().inflate(15.0D),
                    slime -> slime.isAlive()
                            && slime.getSize() > SlimeFormState.MIN_SIZE
                            && (!hasRecoveryLineage(slime)
                            || survivingSlimes.contains(slime)))) {
                nearbySlime.setTarget(target);
                if (!hasRecoveryLineage(nearbySlime)) {
                    recovery.assistedSlimeIds().add(nearbySlime.getUUID());
                }
            }
        }
    }

    static Path findRecoveryFleePath(Slime slime, List<Mob> threats) {
        Vec3 away = Vec3.ZERO;
        for (Mob threat : threats) {
            away = away.add(slime.position().subtract(threat.position()));
        }
        // Spread out from sibling fragments so they do not all pick the same escape.
        Recovery fleeingRecovery = findRecovery(slime);
        if (fleeingRecovery != null) {
            for (Slime sibling : fleeingRecovery.survivors) {
                if (sibling == slime || !sibling.isAlive()) {
                    continue;
                }
                Vec3 apart = slime.position().subtract(sibling.position());
                double distance = apart.length();
                if (distance > 0.01D && distance < SIBLING_SPREAD_RANGE) {
                    away = away.add(apart.scale(SIBLING_SPREAD_WEIGHT / distance));
                }
            }
        }
        // Each fragment samples a slightly different fan of directions (stable per fragment).
        double angleJitter = ((slime.getUUID().hashCode() & 0xFF) / 255.0D - 0.5D) * (Math.PI / 4.0D);
        double baseAngle = (away.horizontalDistanceSqr() < 0.0001D
                ? slime.getRandom().nextDouble() * Math.PI * 2.0D
                : Math.atan2(away.z, away.x)) + angleJitter;

        double currentSafety = minimumThreatDistanceSqr(slime.blockPosition(), threats);
        Path bestSafePath = null;
        double bestSafeRouteDistance = -1.0D;
        double bestSafeEndpointDistance = -1.0D;
        double bestSafeEndpointDisplacement = -1.0D;
        Path bestFallbackPath = null;
        double bestFallbackRouteDistance = -1.0D;
        double bestFallbackEndpointDistance = -1.0D;
        double bestFallbackEndpointDisplacement = -1.0D;
        for (int direction = 0; direction < RECOVERY_FLEE_DIRECTION_COUNT; direction++) {
            double angle = baseAngle
                    + (Math.PI * 2.0D * direction / RECOVERY_FLEE_DIRECTION_COUNT);
            for (double distance : RECOVERY_FLEE_WAYPOINT_DISTANCES) {
                BlockPos destination = BlockPos.containing(
                        slime.getX() + Math.cos(angle) * distance,
                        slime.getY(),
                        slime.getZ() + Math.sin(angle) * distance);
                Path candidate = slime.getNavigation().createPath(destination, 0);
                if (candidate == null || !candidate.canReach()) {
                    continue;
                }
                if (candidate.getNodeCount() < 2) {
                    continue;
                }

                BlockPos endpoint = candidate.getEndNode() == null
                        ? candidate.getTarget()
                        : candidate.getEndNode().asBlockPos();
                if (endpoint == null || endpoint.distSqr(destination) > 4.0D) {
                    // Navigation can return a partial path when the requested
                    // waypoint is blocked. Do not mistake a wall-adjacent
                    // partial route for a successful escape.
                    continue;
                }
                if (!isRecoveryFleePathCollisionFree(slime, candidate)) {
                    continue;
                }

                double routeSafety = minimumPathThreatDistanceSqr(candidate, threats);
                double endpointSafety = minimumThreatDistanceSqr(endpoint, threats);
                double endpointDisplacement = endpoint.distSqr(slime.blockPosition());

                if (isBetterFleePath(
                        endpointSafety,
                        routeSafety,
                        endpointDisplacement,
                        bestFallbackRouteDistance,
                        bestFallbackEndpointDistance,
                        bestFallbackEndpointDisplacement)) {
                    bestFallbackPath = candidate;
                    bestFallbackRouteDistance = routeSafety;
                    bestFallbackEndpointDistance = endpointSafety;
                    bestFallbackEndpointDisplacement = endpointDisplacement;
                }

                // Prefer paths that never bring the slime meaningfully closer
                // to a threat after it leaves its current position.
                if (routeSafety >= currentSafety - 1.0D
                        && isBetterFleePath(
                                endpointSafety,
                                routeSafety,
                                endpointDisplacement,
                                bestSafeRouteDistance,
                                bestSafeEndpointDistance,
                                bestSafeEndpointDisplacement)) {
                    bestSafePath = candidate;
                    bestSafeRouteDistance = routeSafety;
                    bestSafeEndpointDistance = endpointSafety;
                    bestSafeEndpointDisplacement = endpointDisplacement;
                }
            }
        }
        return bestSafePath != null ? bestSafePath : bestFallbackPath;
    }
    /**
     * Hostile mobs that can actually get to this fragment right now, plus any hostile mob
     * within the standoff distance. An empty list means the fragment is safe, which is how
     * fleeing knows to stop. Reachability uses each mob's own pathfinding, which respects the
     * mob's size, so a one-block gap that a zombie or spider cannot enter counts as safe once
     * the fragment has put a little distance between itself and the mob.
     */
    static List<Mob> getRecoveryFragmentThreats(Slime slime) {
        Recovery recovery = findRecovery(slime);
        return recovery == null ? List.of() : evaluateFragmentThreats(recovery, slime);
    }

    private static List<Mob> evaluateFragmentThreats(Recovery recovery, Slime slime) {
        long now = slime.level().getGameTime();
        ThreatEval cached = recovery.threatEvalCache.get(slime.getUUID());
        if (cached != null && isFresh(now, cached.tick(), THREAT_EVAL_INTERVAL_TICKS)) {
            return cached.threats();
        }

        List<Mob> nearby = new ArrayList<>(slime.level().getEntitiesOfClass(
                Mob.class,
                slime.getBoundingBox().inflate(THREAT_EVAL_RADIUS),
                SlimeRecoveryManager::isRecoveryHostile));
        nearby.sort(Comparator.comparingDouble((Mob mob) -> slime.distanceToSqr(mob)));
        List<Mob> dangerous = new ArrayList<>();
        double standoffSqr = SAFE_STANDOFF_DISTANCE * SAFE_STANDOFF_DISTANCE;
        int pathChecks = 0;
        for (Mob mob : nearby) {
            if (slime.distanceToSqr(mob) <= standoffSqr) {
                // Too close to be comfortable, whether or not it can walk to us.
                dangerous.add(mob);
                continue;
            }
            // The list is sorted by distance, so everything left is outside the standoff
            // range. Only the nearest few get the (more expensive) reachability check.
            if (pathChecks >= THREAT_EVAL_MAX_MOBS) {
                break;
            }
            pathChecks++;
            if (canMobReach(mob, slime.blockPosition())) {
                dangerous.add(mob);
            }
        }
        List<Mob> result = List.copyOf(dangerous);
        recovery.threatEvalCache.put(slime.getUUID(), new ThreatEval(now, result));
        return result;
    }

    private static boolean canMobReach(Mob mob, BlockPos target) {
        Path path = mob.getNavigation().createPath(target, 1);
        if (path != null) {
            return path.canReach();
        }
        // No ground route at all. An airborne mob may still fly there, so stay
        // cautious about those when they are close.
        return !mob.onGround()
                && !mob.isInWater()
                && mob.distanceToSqr(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D)
                        <= AIRBORNE_THREAT_RADIUS * AIRBORNE_THREAT_RADIUS;
    }

    /**
     * Picks (and keeps) the fragment the others gather around: a fragment that is not
     * under threat, preferring the biggest, then the one closest to its siblings.
     * The current anchor is kept for as long as it stays safe so the group does not flip-flop.
     */
    private static void updateRegroupAnchor(Recovery recovery, List<Slime> survivors) {
        long elapsed = recovery.recoveryElapsedTicks;
        Slime current = findSurvivor(survivors, recovery.anchorId);
        boolean currentSafe = current != null && evaluateFragmentThreats(recovery, current).isEmpty();
        if (currentSafe) {
            return;
        }
        if (isFresh(elapsed, recovery.anchorStamp, ANCHOR_REEVALUATE_TICKS) && recovery.anchorId == null) {
            return;
        }

        Slime best = null;
        double bestSpread = Double.MAX_VALUE;
        for (Slime candidate : survivors) {
            if (!evaluateFragmentThreats(recovery, candidate).isEmpty()) {
                continue;
            }
            double spread = 0.0D;
            for (Slime other : survivors) {
                spread += candidate.distanceToSqr(other);
            }
            if (best == null
                    || candidate.getSize() > best.getSize()
                    || (candidate.getSize() == best.getSize() && spread < bestSpread)) {
                best = candidate;
                bestSpread = spread;
            }
        }
        recovery.anchorId = best == null ? null : best.getUUID();
        recovery.anchorStamp = elapsed;
    }

    /** Left click (next) / right click (previous) while spectating: switch which fragment you watch. */
    static void cycleCamera(ServerPlayer player, boolean next) {
        Recovery recovery = RECOVERIES.get(player.getUUID());
        if (recovery == null || recovery.survivors.isEmpty()) {
            return;
        }
        List<Slime> fragments = recovery.survivors;
        int current = 0;
        for (int index = 0; index < fragments.size(); index++) {
            if (fragments.get(index).getUUID().equals(recovery.cameraSlimeId)) {
                current = index;
                break;
            }
        }
        int target = Math.floorMod(current + (next ? 1 : -1), fragments.size());
        recovery.cameraSlimeId = fragments.get(target).getUUID();
        player.sendOverlayMessage(Component.literal(
                "Fragment " + (target + 1) + "/" + fragments.size()).withStyle(ChatFormatting.AQUA));
    }

    private static Slime findSurvivor(List<Slime> survivors, UUID id) {
        if (id == null) {
            return null;
        }
        for (Slime fragment : survivors) {
            if (fragment.getUUID().equals(id)) {
                return fragment;
            }
        }
        return null;
    }

    private static int countGroupedFragments(List<Slime> survivors, Slime anchor) {
        int grouped = 0;
        for (Slime fragment : survivors) {
            if (fragment == anchor || fragment.distanceToSqr(anchor) <= GROUP_RADIUS * GROUP_RADIUS) {
                grouped++;
            }
        }
        return grouped;
    }

    static void setRecoveryRegroupStatus(Slime slime, String status) {
        REGROUP_STATUS.put(slime.getUUID(), status);
    }

    private static boolean anyFragmentThreatened(Recovery recovery, List<Slime> survivors) {
        for (Slime fragment : survivors) {
            if (!evaluateFragmentThreats(recovery, fragment).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** The fragment this slime should gather around, or null if there is no safe anchor right now. */
    static Slime getRecoveryRegroupAnchor(Slime slime) {
        Recovery recovery = findRecovery(slime);
        if (recovery == null || recovery.anchorId == null) {
            return null;
        }
        for (Slime fragment : recovery.survivors) {
            if (fragment.getUUID().equals(recovery.anchorId) && fragment.isAlive() && !fragment.isRemoved()) {
                return fragment;
            }
        }
        return null;
    }

    /** A regroup route is unsafe if a mob that can reach part of it is standing close to it. */
    static boolean isRecoveryRegroupPathSafe(Slime slime, Path path) {
        if (path == null || path.getNodeCount() < 2) {
            return false;
        }
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (int index = 1; index < path.getNodeCount(); index++) {
            BlockPos node = path.getNodePos(index);
            minX = Math.min(minX, node.getX());
            minY = Math.min(minY, node.getY());
            minZ = Math.min(minZ, node.getZ());
            maxX = Math.max(maxX, node.getX() + 1.0D);
            maxY = Math.max(maxY, node.getY() + 1.0D);
            maxZ = Math.max(maxZ, node.getZ() + 1.0D);
        }
        AABB area = new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(REGROUP_PATH_SAFETY_RADIUS);
        double limit = REGROUP_PATH_SAFETY_RADIUS * REGROUP_PATH_SAFETY_RADIUS;
        for (Mob mob : slime.level().getEntitiesOfClass(
                Mob.class, area, SlimeRecoveryManager::isRecoveryHostile)) {
            BlockPos nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            for (int index = 1; index < path.getNodeCount(); index++) {
                BlockPos node = path.getNodePos(index);
                double distance = node.distSqr(mob.blockPosition());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = node;
                }
            }
            if (nearest != null && nearestDistance <= limit) {
                return false;
            }
        }
        return true;
    }

    private static Recovery findRecovery(Slime slime) {
        String lineageId = getRecoveryLineage(slime);
        if (lineageId == null) {
            return null;
        }
        for (Recovery recovery : RECOVERIES.values()) {
            if (lineageId.equals(recovery.lineageId())) {
                return recovery;
            }
        }
        return null;
    }
    private static ServerPlayer findRecoveryPlayer(MinecraftServer server, Recovery recovery) {
        for (Map.Entry<UUID, Recovery> entry : RECOVERIES.entrySet()) {
            if (entry.getValue() == recovery) {
                return server.getPlayerList().getPlayer(entry.getKey());
            }
        }
        return null;
    }

    private static boolean isRecoveryFleePathCollisionFree(Slime slime, Path path) {
        for (int index = 1; index < path.getNodeCount(); index++) {
            BlockPos node = path.getNodePos(index);
            AABB nodeBox = slime.getBoundingBox().move(
                    node.getX() + 0.5D - slime.getX(),
                    node.getY() - slime.getY(),
                    node.getZ() + 0.5D - slime.getZ());
            if (!slime.level().noCollision(slime, nodeBox)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBetterFleePath(
            double endpointDistance,
            double routeDistance,
            double endpointDisplacement,
            double bestRouteDistance,
            double bestEndpointDistance,
            double bestEndpointDisplacement) {
        return endpointDistance > bestEndpointDistance
                || (endpointDistance == bestEndpointDistance && routeDistance > bestRouteDistance)
                || (endpointDistance == bestEndpointDistance
                && routeDistance == bestRouteDistance
                && endpointDisplacement > bestEndpointDisplacement);
    }

    static boolean isRecoveryFleePathSafe(Slime slime, Path path, List<Mob> threats) {
        if (path == null || threats.isEmpty() || path.getNodeCount() < 2) {
            return false;
        }
        double currentSafety = minimumThreatDistanceSqr(slime.blockPosition(), threats);
        return minimumPathThreatDistanceSqr(path, threats) >= currentSafety - 1.0D;
    }

    static void visualizeRecoveryFleePath(Slime slime, Path path, List<Mob> threats) {
        if (!SlimeFormConfig.get().recoveryFleePathDebug
                || slime.level().isClientSide()
                || path == null
                || slime.level().getGameTime() % 4L != 0L) {
            return;
        }

        ServerLevel level = (ServerLevel) slime.level();
        Recovery recovery = findRecovery(slime);
        ServerPlayer viewer = recovery == null ? null : findRecoveryPlayer(level.getServer(), recovery);
        if (viewer == null) {
            return;
        }

        List<BlockPos> route = pathNodes(path);
        for (BlockPos node : route) {
            level.sendParticles(
                    viewer,
                    ParticleTypes.END_ROD,
                    false,
                    false,
                    node.getX() + 0.5D,
                    node.getY() + 0.15D,
                    node.getZ() + 0.5D,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D);
        }

        BlockPos endpoint = route.isEmpty() ? null : route.get(route.size() - 1);
        if (endpoint != null) {
            level.sendParticles(
                    viewer,
                    ParticleTypes.SOUL_FIRE_FLAME,
                    false,
                    false,
                    endpoint.getX() + 0.5D,
                    endpoint.getY() + 0.25D,
                    endpoint.getZ() + 0.5D,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D);
        }

        for (Mob threat : threats) {
            level.sendParticles(
                    viewer,
                    ParticleTypes.FLAME,
                    false,
                    false,
                    threat.getX(),
                    threat.getY() + threat.getBbHeight() * 0.5D,
                    threat.getZ(),
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D);
        }
    }

    private static List<BlockPos> pathNodes(Path path) {
        List<BlockPos> nodes = new ArrayList<>();
        for (int index = 1; index < path.getNodeCount(); index++) {
            nodes.add(path.getNodePos(index));
        }
        return nodes;
    }

    private static double minimumPathThreatDistanceSqr(Path path, List<Mob> threats) {
        double minimumDistance = Double.MAX_VALUE;
        for (int index = 1; index < path.getNodeCount(); index++) {
            minimumDistance = Math.min(
                    minimumDistance,
                    minimumThreatDistanceSqr(path.getNodePos(index), threats));
        }
        return minimumDistance;
    }

    private static double minimumThreatDistanceSqr(BlockPos position, List<Mob> threats) {
        double minimumDistance = Double.MAX_VALUE;
        for (Mob threat : threats) {
            minimumDistance = Math.min(minimumDistance, position.distSqr(threat.blockPosition()));
        }
        return minimumDistance;
    }

    static boolean isRecoveryHostile(LivingEntity entity) {
        // Slimes are not threats to recovery fragments, even though they are
        // living Enemy entities in vanilla's classification.
        return entity instanceof Enemy
                && entity instanceof Mob
                && !(entity instanceof Slime)
                && entity.isAlive();
    }

    /**
     * Ends a running recovery when its player disconnects (or the server stops).
     * Items are dropped where the fragments last were, the fragments are removed,
     * and the player is saved as a normal survival player at full size instead of
     * a dead spectator. A tag lets the next join explain what happened.
     */
    static void handlePlayerDisconnect(MinecraftServer server, ServerPlayer player) {
        Recovery recovery = RECOVERIES.remove(player.getUUID());
        if (recovery == null) {
            return;
        }
        cleanupSplitSlimes(server, recovery);
        dropInventory(server, recovery);

        GameType restoredGameMode = recovery.previousGameMode();
        player.gameMode.changeGameModeForPlayer(restoredGameMode);
        player.setGameMode(restoredGameMode);
        SlimeFormState.setSize(player, SlimeFormState.getMaxSize());
        SlimeFormState.applyHealth(player, true);

        Vec3 drop = recovery.lastKnownPosition;
        player.entityTags().removeIf(tag -> tag.startsWith(ABANDONED_RECOVERY_TAG_PREFIX));
        player.addTag(ABANDONED_RECOVERY_TAG_PREFIX
                + (int) Math.floor(drop.x) + ","
                + (int) Math.floor(drop.y) + ","
                + (int) Math.floor(drop.z));
        SlimeFormMod.LOGGER.info(
                "[slimeform] {} disconnected during a recovery; inventory dropped at {}, {}, {}",
                player.getName().getString(),
                (int) Math.floor(drop.x), (int) Math.floor(drop.y), (int) Math.floor(drop.z));
    }

    /** Called on join: tells the player where their items went after a disconnect mid-recovery. */
    static void notifyAbandonedRecovery(ServerPlayer player) {
        String found = null;
        for (String tag : player.entityTags()) {
            if (tag.startsWith(ABANDONED_RECOVERY_TAG_PREFIX)) {
                found = tag;
                break;
            }
        }
        if (found == null) {
            return;
        }
        player.removeTag(found);
        String position = found.substring(ABANDONED_RECOVERY_TAG_PREFIX.length()).replace(",", " ");
        player.sendSystemMessage(
                Component.literal("Your slime recovery ended when you disconnected. ")
                        .withStyle(ChatFormatting.RED)
                        .append(Component.literal("Your items were dropped near " + position + ".")
                                .withStyle(ChatFormatting.GRAY)));
    }

    private static void dropInventory(MinecraftServer server, Recovery recovery) {
        ServerLevel level = server.getLevel(recovery.dimension());
        if (level == null) {
            return;
        }

        Vec3 position = recovery.lastKnownPosition;
        int dropped = 0;
        for (ItemStack stack : recovery.inventory().allStacks()) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemEntity item = new ItemEntity(
                    level,
                    position.x,
                    position.y,
                    position.z,
                    stack.copy());
            item.setPickUpDelay(40);
            level.addFreshEntity(item);
            dropped++;
        }
        if (dropped > 0) {
            SlimeFormMod.LOGGER.info("[slimeform] Dropped {} inventory stacks after failed recovery", dropped);
        }
    }

    private static InventorySnapshot captureAndClearInventory(ServerPlayer player) {
        List<ItemStack> inventoryItems = new ArrayList<>();
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            inventoryItems.add(stack.copy());
        }

        Map<EquipmentSlot, ItemStack> equipmentItems = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : carriedEquipmentSlots()) {
            equipmentItems.put(slot, player.getItemBySlot(slot).copy());
        }

        player.getInventory().getNonEquipmentItems().replaceAll(stack -> ItemStack.EMPTY);
        for (EquipmentSlot slot : carriedEquipmentSlots()) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        player.getInventory().setChanged();
        return new InventorySnapshot(inventoryItems, equipmentItems);
    }

    private static void restoreInventory(ServerPlayer player, InventorySnapshot inventory) {
        List<ItemStack> inventoryItems = player.getInventory().getNonEquipmentItems();
        for (int index = 0; index < inventoryItems.size(); index++) {
            inventoryItems.set(index, inventory.items().get(index).copy());
        }
        for (EquipmentSlot slot : carriedEquipmentSlots()) {
            player.setItemSlot(slot, inventory.equipment().getOrDefault(slot, ItemStack.EMPTY).copy());
        }
        player.getInventory().setChanged();
    }

    private static Set<EquipmentSlot> carriedEquipmentSlots() {
        return EnumSet.of(
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET,
                EquipmentSlot.OFFHAND);
    }

    private static Component createRecoveryProgressBar(int ticksRemaining, int totalTicks) {
        int totalSeconds = totalTicks / SlimeFormMod.RECOVERY_COUNTDOWN_INTERVAL_TICKS;
        int seconds = (int) Math.ceil(ticksRemaining / (double) SlimeFormMod.RECOVERY_COUNTDOWN_INTERVAL_TICKS);
        double progress = ticksRemaining / (double) totalTicks;
        int filled = Math.max(0, Math.min(RECOVERY_PROGRESS_BAR_WIDTH,
                (int) Math.ceil(progress * RECOVERY_PROGRESS_BAR_WIDTH)));
        ChatFormatting color = progress > 0.5D
                ? ChatFormatting.GREEN
                : progress > 0.25D ? ChatFormatting.YELLOW : ChatFormatting.RED;

        MutableComponent bar = Component.literal("Reforming [").withStyle(ChatFormatting.GRAY);
        bar.append(Component.literal("█".repeat(filled)).withStyle(color));
        bar.append(Component.literal("░".repeat(RECOVERY_PROGRESS_BAR_WIDTH - filled))
                .withStyle(ChatFormatting.DARK_GRAY));
        bar.append(Component.literal("] " + seconds + "s / " + totalSeconds + "s")
                .withStyle(color));
        return bar;
    }

    private static Component createHostileReformStatus(Recovery recovery) {
        int remainingTicks = Math.max(0, SIZE_ONE_RECOVERY_MAX_TICKS - recovery.recoveryElapsedTicks);
        int remainingSeconds = (int) Math.ceil(
                remainingTicks / (double) SlimeFormMod.RECOVERY_COUNTDOWN_INTERVAL_TICKS);
        int minutes = remainingSeconds / 60;
        int seconds = remainingSeconds % 60;
        return Component.literal("Reforming: [Fragments In Danger]\n")
                .withStyle(ChatFormatting.RED)
                .append(Component.literal(String.format(
                        java.util.Locale.ROOT,
                        "Time to force reform: %dm %02ds",
                        minutes,
                        seconds)).withStyle(ChatFormatting.YELLOW));
    }

    private static void playReformEffects(ServerLevel level, ServerPlayer player) {
        level.sendParticles(
                ParticleTypes.ITEM_SLIME,
                player.getX(),
                player.getY() + 1.0D,
                player.getZ(),
                REFORM_PARTICLE_COUNT,
                0.5D,
                0.8D,
                0.5D,
                0.1D);
        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.SLIME_SQUISH,
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
    }

    private static void playRecoveryFailureEffects(ServerLevel level, ServerPlayer player) {
        level.sendParticles(
                ParticleTypes.ITEM_SLIME,
                player.getX(),
                player.getY() + 0.7D,
                player.getZ(),
                24,
                0.45D,
                0.55D,
                0.45D,
                0.06D);
        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.SLIME_SQUISH,
                SoundSource.PLAYERS,
                1.0F,
                0.65F);
    }

    private static void cleanupSplitSlimes(MinecraftServer server, Recovery recovery) {
        for (UUID fragmentId : recovery.lineageEntityIds()) {
            REGROUP_STATUS.remove(fragmentId);
        }
        recovery.clearFleeDangerBar();
        int removed = 0;
        String lineageId = recovery.lineageId();

        // UUIDs are only a fallback for entities that may no longer be returned
        // by the normal entity scan. Entity-owned lineage remains authoritative.
        for (ServerLevel level : server.getAllLevels()) {
            for (UUID lineageEntityId : recovery.lineageEntityIds()) {
                Entity entity = level.getEntity(lineageEntityId);
                if (entity instanceof Slime slime && !slime.isRemoved()) {
                    slime.discard();
                    removed++;
                }
            }
            for (UUID assistedId : recovery.assistedSlimeIds()) {
                Entity entity = level.getEntity(assistedId);
                if (entity instanceof Slime slime && !slime.isRemoved()) {
                    slime.setTarget(null);
                }
            }
        }

        // Snapshot before discard because removing entities can mutate the
        // level's entity collections during iteration.
        List<Slime> lineageSlimes = getLineageSlimes(server, lineageId);
        for (Slime slime : lineageSlimes) {
            if (!slime.isRemoved()) {
                slime.discard();
                removed++;
            }
        }
        recovery.assistedSlimeIds().clear();
        recovery.lineageEntityIds().clear();
        if (removed > 0) {
            SlimeFormMod.LOGGER.info("[slimeform] Removed {} temporary split slimes", removed);
        }
    }

    private record ThreatEval(long tick, List<Mob> threats) {
    }

    private static final class Recovery {
        private final String lineageId;
        private final UUID originalKillerId;
        private final ResourceKey<Level> dimension;
        private final double x;
        private final double y;
        private final double z;
        private final float yRot;
        private final float xRot;
        private final GameType previousGameMode;
        private int ticksRemaining;
        private final int totalTicks;
        private int recoveryElapsedTicks;
        private int cameraResyncTicksRemaining = 10;
        private boolean sizeOneReformCountdownStarted;
        private final InventorySnapshot inventory;
        private Vec3 lastKnownPosition;
        private boolean postAvenging;
        private final Set<UUID> assistedSlimeIds = new HashSet<>();
        private final Set<UUID> lineageEntityIds = new LinkedHashSet<>();
        private int emptyLineageChecks;
        // Short-lived caches so sibling fragments share one scan instead of each doing their own.
        private long hostileNearbyStamp = NEVER;
        private boolean hostileNearby;
        private final Map<UUID, ThreatEval> threatEvalCache = new HashMap<>();
        // Regroup state: the fragment the others gather around, and how long the whole
        // group has been together.
        private List<Slime> survivors = List.of();
        private UUID anchorId;
        private UUID cameraSlimeId;
        private int originalSize = SlimeFormState.MIN_SIZE;
        private int strandedTicks;
        private int rescueCount;
        private long anchorStamp = NEVER;
        private int groupedTicks;
        private int gatherWaitTicks;
        private final ServerBossEvent fleeDangerBar;

        private Recovery(String lineageId,
                         UUID originalKillerId,
                         ResourceKey<Level> dimension,
                         double x,
                         double y,
                         double z,
                         float yRot,
                         float xRot,
                         GameType previousGameMode,
                         int ticksRemaining,
                         InventorySnapshot inventory) {
            this.lineageId = lineageId;
            this.originalKillerId = originalKillerId;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yRot = yRot;
            this.xRot = xRot;
            this.previousGameMode = previousGameMode;
            this.ticksRemaining = ticksRemaining;
            this.totalTicks = ticksRemaining;
            this.inventory = inventory;
            this.lastKnownPosition = new Vec3(x, y, z);
            this.fleeDangerBar = new ServerBossEvent(
                    UUID.randomUUID(),
                    Component.literal("Recovery danger"),
                    BossEvent.BossBarColor.GREEN,
                    BossEvent.BossBarOverlay.PROGRESS);
            this.fleeDangerBar.setVisible(false);
        }

        private String lineageId() {
            return lineageId;
        }

        private UUID originalKillerId() {
            return originalKillerId;
        }

        private boolean postAvenging() {
            return postAvenging;
        }

        private void setPostAvenging() {
            postAvenging = true;
        }

        private Set<UUID> assistedSlimeIds() {
            return assistedSlimeIds;
        }

        private Set<UUID> lineageEntityIds() {
            return lineageEntityIds;
        }

        private int emptyLineageChecks() {
            return emptyLineageChecks;
        }

        private void incrementEmptyLineageChecks() {
            emptyLineageChecks++;
        }

        private void resetEmptyLineageChecks() {
            emptyLineageChecks = 0;
        }

        private void clearFleeDangerBar() {
            fleeDangerBar.removeAllPlayers();
            fleeDangerBar.setVisible(false);
        }

        private GameType previousGameMode() {
            return previousGameMode;
        }

        private ResourceKey<Level> dimension() {
            return dimension;
        }

        private double x() {
            return x;
        }

        private double y() {
            return y;
        }

        private double z() {
            return z;
        }

        private float yRot() {
            return yRot;
        }

        private float xRot() {
            return xRot;
        }

        private InventorySnapshot inventory() {
            return inventory;
        }
    }

    private record InventorySnapshot(
            List<ItemStack> items,
            Map<EquipmentSlot, ItemStack> equipment) {
        private List<ItemStack> allStacks() {
            List<ItemStack> stacks = new ArrayList<>(items);
            stacks.addAll(equipment.values());
            return stacks;
        }
    }

    /** True while the player has an active split/recovery in progress. */
    static boolean isRecovering(UUID playerId) {
        return RECOVERIES.containsKey(playerId);
    }

    /** Seconds left on the player's reform countdown (only meaningful if {@link #isRecovering}). */
    static int recoverySecondsRemaining(UUID playerId) {
        Recovery recovery = RECOVERIES.get(playerId);
        return recovery == null
                ? 0
                : (int) Math.ceil(recovery.ticksRemaining
                        / (double) SlimeFormMod.RECOVERY_COUNTDOWN_INTERVAL_TICKS);
    }
}
