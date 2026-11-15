package io.asy.fragmented;

import io.asy.fragmented.mixin.MobGoalSelectorAccessor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Assigns idle allied slimes to mobs that are threatening a slime-form player. */
public final class SlimeDefenseTargetGoal extends TargetGoal {
    private static final double DEFENSE_RADIUS = 32.0D;
    private static final long TRACKER_EXPIRY_TICKS = 600L;
    private static final Map<UUID, TrackedThreats> TRACKED_THREATS = new HashMap<>();
    private static boolean DISPATCHING_IMMEDIATE_TARGET;

    private final Slime slime;
    private Player defendedPlayer;

    public SlimeDefenseTargetGoal(Slime slime) {
        super(slime, false);
        this.slime = slime;
    }

    /** Immediately reruns nearby allied slime target selectors after a hostile acquires the player. */
    public static void onThreatTargetChanged(Mob threat, Player player) {
        if (threat.level().isClientSide()
                || !threat.isAlive()
                || !player.isAlive()
                || player.level() != threat.level()
                || !SlimeFormState.isActive(player)
                || player.entityTags().contains(SlimeFormMod.SLIME_DORMANT_TAG)) {
            return;
        }

        long gameTime = threat.level().getGameTime();
        TrackedThreats tracker = TRACKED_THREATS.compute(player.getUUID(), (id, existing) ->
                existing == null || !existing.dimension.equals(threat.level().dimension())
                        ? new TrackedThreats(threat.level().dimension(), gameTime)
                        : existing);
        tracker.lastAccessTick = gameTime;
        tracker.entityIds.add(threat.getUUID());

        if (DISPATCHING_IMMEDIATE_TARGET) {
            return;
        }

        AABB area = player.getBoundingBox().inflate(DEFENSE_RADIUS);
        List<Slime> defendingSlimes = threat.level().getEntitiesOfClass(Slime.class, area,
                slime -> slime != threat
                        && slime.isAlive()
                        && slime.getSize() > SlimeFormState.MIN_SIZE
                        && slime.isAlliedTo(player)
                        && slime.canAttack(threat));
        if (defendingSlimes.isEmpty()) {
            return;
        }

        DISPATCHING_IMMEDIATE_TARGET = true;
        try {
            for (Slime slime : defendingSlimes) {
                ((MobGoalSelectorAccessor) slime).slimeform$getTargetSelector().tick();
            }
        } finally {
            DISPATCHING_IMMEDIATE_TARGET = false;
        }
    }

    @Override
    public boolean canUse() {
        if (slime.level().isClientSide() || !slime.isAlive()
                || slime.getSize() <= SlimeFormState.MIN_SIZE) {
            return false;
        }

        LivingEntity currentTarget = slime.getTarget();
        if (currentTarget != null && currentTarget.isAlive()) {
            return false;
        }

        pruneExpiredTrackers();
        Player player = findNearestEligiblePlayer();
        if (player == null) {
            return false;
        }

        List<Mob> threats = findThreats(player);
        if (threats.isEmpty()) {
            return false;
        }

        targetMob = SlimeDefenseAssignmentManager.reserveTarget(slime, player, threats);
        defendedPlayer = targetMob == null ? null : player;
        return targetMob != null;
    }

    @Override
    public boolean canContinueToUse() {
        boolean shouldContinue = targetMob != null
                && slime.isAlive()
                && slime.getTarget() == targetMob
                && targetMob.isAlive()
                && targetMob.level() == slime.level()
                && defendedPlayer != null
                && defendedPlayer.isAlive()
                && defendedPlayer.level() == slime.level()
                && SlimeFormState.isActive(defendedPlayer)
                && !defendedPlayer.entityTags().contains(SlimeFormMod.SLIME_DORMANT_TAG)
                && slime.isAlliedTo(defendedPlayer)
                && targetMob.distanceToSqr(defendedPlayer) <= DEFENSE_RADIUS * DEFENSE_RADIUS;
        if (shouldContinue) {
            SlimeDefenseAssignmentManager.maintainReservation(slime, defendedPlayer, (Mob) targetMob);
        }
        return shouldContinue;
    }

    @Override
    public void start() {
        slime.setTarget(targetMob);
        super.start();
    }

    @Override
    public void stop() {
        if (defendedPlayer != null) {
            SlimeDefenseAssignmentManager.releaseReservation(slime, defendedPlayer);
        }
        // A direct player command may replace this goal's target while it runs.
        // Do not clear that replacement when this goal shuts down.
        if (slime.getTarget() == targetMob) {
            super.stop();
        }
        targetMob = null;
        defendedPlayer = null;
    }

    private Player findNearestEligiblePlayer() {
        AABB area = slime.getBoundingBox().inflate(DEFENSE_RADIUS);
        return slime.level().getEntitiesOfClass(Player.class, area, player ->
                        player.isAlive()
                                && SlimeFormState.isActive(player)
                                && !player.entityTags().contains(SlimeFormMod.SLIME_DORMANT_TAG)
                                && slime.isAlliedTo(player))
                .stream()
                .min(Comparator.comparingDouble((Player candidate) -> slime.distanceToSqr(candidate))
                        .thenComparing(Player::getUUID))
                .orElse(null);
    }

    private List<Mob> findThreats(Player player) {
        AABB area = player.getBoundingBox().inflate(DEFENSE_RADIUS);
        List<Mob> nearbyMobs = slime.level().getEntitiesOfClass(Mob.class, area,
                mob -> mob != slime
                        && mob.isAlive()
                        && slime.canAttack(mob));

        TrackedThreats tracker = TRACKED_THREATS.compute(player.getUUID(), (id, existing) ->
                existing == null || !existing.dimension.equals(slime.level().dimension())
                        ? new TrackedThreats(slime.level().dimension(), slime.level().getGameTime())
                        : existing);
        tracker.lastAccessTick = slime.level().getGameTime();

        Set<UUID> nearbyIds = new HashSet<>();
        for (Mob mob : nearbyMobs) {
            nearbyIds.add(mob.getUUID());
            if (isThreatTo(mob, player)) {
                tracker.entityIds.add(mob.getUUID());
            }
        }

        // Retain a discovered threat while it remains in the player's defense
        // area, even after the first slime causes it to target the slime.
        tracker.entityIds.retainAll(nearbyIds);
        if (tracker.entityIds.isEmpty()) {
            TRACKED_THREATS.remove(player.getUUID(), tracker);
            return List.of();
        }

        List<Mob> threats = new ArrayList<>();
        for (Mob mob : nearbyMobs) {
            if (tracker.entityIds.contains(mob.getUUID())) {
                threats.add(mob);
            }
        }
        return threats;
    }

    private boolean isThreatTo(Mob mob, Player player) {
        if (mob.getTarget() == player) {
            return true;
        }

        if (mob instanceof NeutralMob neutralMob) {
            EntityReference<LivingEntity> angerTarget = neutralMob.getPersistentAngerTarget();
            return angerTarget != null && angerTarget.getUUID().equals(player.getUUID());
        }
        return false;
    }

    private void pruneExpiredTrackers() {
        ResourceKey<Level> dimension = slime.level().dimension();
        long gameTime = slime.level().getGameTime();
        TRACKED_THREATS.entrySet().removeIf(entry ->
                entry.getValue().dimension.equals(dimension)
                        && gameTime - entry.getValue().lastAccessTick > TRACKER_EXPIRY_TICKS);
    }

    private static final class TrackedThreats {
        private final ResourceKey<Level> dimension;
        private final Set<UUID> entityIds = new HashSet<>();
        private long lastAccessTick;

        private TrackedThreats(ResourceKey<Level> dimension, long lastAccessTick) {
            this.dimension = dimension;
            this.lastAccessTick = lastAccessTick;
        }
    }
}
