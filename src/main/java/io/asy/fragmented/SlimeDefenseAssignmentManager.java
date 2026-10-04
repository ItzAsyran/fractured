package io.asy.fragmented;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Coordinates defensive target reservations across nearby slimes. */
final class SlimeDefenseAssignmentManager {
    private static final double DEFENSE_RADIUS = 32.0D;
    private static final int TARGET_SLIMES_PER_THREAT = 3;
    private static final long PENDING_RESERVATION_TICKS = 2L;
    private static final long GROUP_EXPIRY_TICKS = 600L;
    private static final Map<AssignmentKey, AssignmentGroup> GROUPS = new HashMap<>();

    private SlimeDefenseAssignmentManager() {
    }

    static synchronized Mob reserveTarget(Slime slime, Player player, List<Mob> threats) {
        ResourceKey<Level> dimension = slime.level().dimension();
        long gameTime = slime.level().getGameTime();
        pruneExpiredGroups(dimension, gameTime);

        AssignmentKey key = new AssignmentKey(player.getUUID(), dimension);
        AssignmentGroup group = GROUPS.computeIfAbsent(key, ignored -> new AssignmentGroup(gameTime));
        group.lastAccessTick = gameTime;

        AABB area = player.getBoundingBox().inflate(DEFENSE_RADIUS);
        List<Slime> nearbySlimes = slime.level().getEntitiesOfClass(Slime.class, area,
                candidate -> candidate.isAlive()
                        && candidate.getSize() > SlimeFormState.MIN_SIZE
                        && candidate.isAlliedTo(player));
        if (nearbySlimes.stream().noneMatch(candidate -> candidate == slime)) {
            nearbySlimes.add(slime);
        }

        Map<UUID, Slime> slimesById = new HashMap<>();
        for (Slime nearbySlime : nearbySlimes) {
            slimesById.put(nearbySlime.getUUID(), nearbySlime);
        }

        Map<UUID, Mob> threatsById = new HashMap<>();
        for (Mob threat : threats) {
            threatsById.put(threat.getUUID(), threat);
        }

        pruneReservations(group, slimesById, threatsById, gameTime);

        Map<UUID, Integer> assignedCounts = new HashMap<>();
        for (Mob threat : threats) {
            assignedCounts.put(threat.getUUID(), 0);
        }

        for (Slime nearbySlime : nearbySlimes) {
            Reservation reservation = group.reservations.get(nearbySlime.getUUID());
            LivingEntity currentTarget = nearbySlime.getTarget();
            UUID assignedThreatId = reservation != null
                    ? reservation.threatId
                    : currentTarget == null ? null : currentTarget.getUUID();
            if (assignedThreatId != null && assignedCounts.containsKey(assignedThreatId)) {
                assignedCounts.compute(assignedThreatId, (ignored, count) -> count + 1);
            }
        }

        List<Mob> uncoveredThreats = threats.stream()
                .filter(threat -> assignedCounts.get(threat.getUUID()) == 0)
                .toList();
        Mob selected;
        if (!uncoveredThreats.isEmpty()) {
            selected = uncoveredThreats.stream()
                    .min(Comparator.comparingDouble((Mob threat) -> threat.distanceToSqr(player))
                            .thenComparing(Mob::getUUID))
                    .orElse(null);
        } else {
            List<Mob> underBudgetThreats = threats.stream()
                    .filter(threat -> assignedCounts.get(threat.getUUID()) < TARGET_SLIMES_PER_THREAT)
                    .toList();
            List<Mob> allocationPool = underBudgetThreats.isEmpty() ? threats : underBudgetThreats;
            selected = allocationPool.stream()
                    .min(Comparator.comparingInt((Mob threat) -> assignedCounts.get(threat.getUUID()))
                            .thenComparingDouble(slime::distanceToSqr)
                            .thenComparing(Mob::getUUID))
                    .orElse(null);
        }

        if (selected != null) {
            group.reservations.put(slime.getUUID(), new Reservation(selected.getUUID(), gameTime));
        }
        return selected;
    }

    static synchronized void maintainReservation(Slime slime, Player player, Mob threat) {
        AssignmentKey key = new AssignmentKey(player.getUUID(), slime.level().dimension());
        AssignmentGroup group = GROUPS.get(key);
        if (group == null) {
            return;
        }

        Reservation reservation = group.reservations.get(slime.getUUID());
        if (reservation != null && reservation.threatId.equals(threat.getUUID())) {
            group.lastAccessTick = slime.level().getGameTime();
        }
    }

    static synchronized void releaseReservation(Slime slime, Player player) {
        AssignmentKey key = new AssignmentKey(player.getUUID(), slime.level().dimension());
        AssignmentGroup group = GROUPS.get(key);
        if (group == null) {
            return;
        }

        group.reservations.remove(slime.getUUID());
        group.lastAccessTick = slime.level().getGameTime();
        if (group.reservations.isEmpty()) {
            GROUPS.remove(key);
        }
    }

    private static void pruneExpiredGroups(ResourceKey<Level> dimension, long gameTime) {
        Iterator<Map.Entry<AssignmentKey, AssignmentGroup>> iterator = GROUPS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<AssignmentKey, AssignmentGroup> entry = iterator.next();
            AssignmentGroup group = entry.getValue();
            if (entry.getKey().dimension.equals(dimension)
                    && gameTime - group.lastAccessTick > GROUP_EXPIRY_TICKS) {
                iterator.remove();
            }
        }
    }

    private static void pruneReservations(
            AssignmentGroup group,
            Map<UUID, Slime> slimesById,
            Map<UUID, Mob> threatsById,
            long gameTime) {
        Iterator<Map.Entry<UUID, Reservation>> iterator = group.reservations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Reservation> entry = iterator.next();
            Slime assignedSlime = slimesById.get(entry.getKey());
            Reservation reservation = entry.getValue();
            Mob assignedThreat = threatsById.get(reservation.threatId);
            if (assignedSlime == null || assignedThreat == null) {
                iterator.remove();
                continue;
            }

            LivingEntity currentTarget = assignedSlime.getTarget();
            if (currentTarget != null && currentTarget != assignedThreat) {
                iterator.remove();
            } else if (currentTarget == null
                    && gameTime - reservation.createdAt > PENDING_RESERVATION_TICKS) {
                iterator.remove();
            }
        }
    }

    private record AssignmentKey(UUID playerId, ResourceKey<Level> dimension) {
    }

    private record Reservation(UUID threatId, long createdAt) {
    }

    private static final class AssignmentGroup {
        private final Map<UUID, Reservation> reservations = new HashMap<>();
        private long lastAccessTick;

        private AssignmentGroup(long lastAccessTick) {
            this.lastAccessTick = lastAccessTick;
        }
    }
}
