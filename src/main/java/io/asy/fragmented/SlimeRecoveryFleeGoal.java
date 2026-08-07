package io.asy.fragmented;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.pathfinder.Path;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Persistent avoidance behavior for size 1 slimes participating in recovery. */
public final class SlimeRecoveryFleeGoal extends Goal {
    private static final int NO_PROGRESS_LIMIT_TICKS = 12;
    private final Slime slime;
    private Path fleePath;
    private List<Mob> threats = List.of();
    private Set<java.util.UUID> threatIds = Set.of();
    private boolean threatsChanged;
    private long nextThreatCheckTick;
    private long nextRepathTick;
    private Vec3 lastPosition;
    private int noProgressTicks;
    private int sharedRouteVersion = -1;
    private int sharedWaypointIndex;
    private boolean sharedRouteActive;

    public SlimeRecoveryFleeGoal(Slime slime) {
        this.slime = slime;
        // Vanilla SlimeRandomDirectionGoal uses LOOK to overwrite the slime's
        // move controller. Reserve both flags while the flee path is active.
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!isEligible() || !refreshThreats(false)) {
            return false;
        }
        boolean pathReady = requestPath(threatsChanged);
        threatsChanged = false;
        // If no usable route exists, release MOVE so vanilla slime AI can
        // take over instead of keeping this goal active and jumping in place.
        return pathReady;
    }

    @Override
    public boolean canContinueToUse() {
        if (!isEligible()) {
            return false;
        }

        // Release this individual fragment as soon as its own local area is
        // clear, even if another lineage fragment is still under threat.
        if (!SlimeFormMod.hasRecoveryFleeThreatNearby(slime)) {
            return false;
        }
        return fleePath != null && !fleePath.isDone();
    }

    @Override
    public void start() {
        slime.setTarget(null);
        steerAlongPath();
        visualizePath();
        lastPosition = slime.position();
        noProgressTicks = 0;
        threatsChanged = false;
    }

    @Override
    public void tick() {
        slime.setTarget(null);
        boolean danger = refreshThreats(false);
        boolean pathFailed = fleePath == null || fleePath.isDone();
        boolean pathTurnsTowardThreat = fleePath != null
                && !SlimeFormMod.isRecoveryFleePathSafe(slime, fleePath, threats);
        boolean madeProgress = lastPosition == null
                || slime.position().distanceToSqr(lastPosition) > 0.01D;
        noProgressTicks = madeProgress ? 0 : noProgressTicks + 1;
        lastPosition = slime.position();

        // A safe slime may complete its current path, but must not repath.
        boolean stalled = noProgressTicks >= NO_PROGRESS_LIMIT_TICKS;
        if (danger && stalled) {
            // Do not keep feeding the slime the same movement command when
            // navigation has failed to move it through enclosed terrain.
            fleePath = null;
            slime.getNavigation().stop();
            holdPosition();
            threatsChanged = false;
            return;
        }

        boolean shouldRepath = danger
                && (threatsChanged || pathFailed || pathTurnsTowardThreat
                || (fleePath == null && sharedRouteActive));
        if (shouldRepath) {
            if (pathFailed || pathTurnsTowardThreat) {
                fleePath = null;
            }
            boolean pathRequested = requestPath(threatsChanged || pathTurnsTowardThreat);
            if (pathRequested) {
                steerAlongPath();
                noProgressTicks = 0;
            }
            threatsChanged = false;
        }
        steerAlongPath();
        visualizePath();
    }

    @Override
    public void stop() {
        fleePath = null;
        threats = List.of();
        threatIds = Set.of();
        threatsChanged = false;
        sharedRouteActive = false;
        slime.getNavigation().stop();
        ((SlimeMoveControlAccess) slime.getMoveControl()).slimeform$setWantedMovement(0.0D);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean isEligible() {
        return !slime.level().isClientSide()
                && slime.isAlive()
                && slime.getSize() == SlimeFormState.MIN_SIZE
                && SlimeFormMod.hasRecoveryLineage(slime);
    }

    private boolean refreshThreats(boolean force) {
        long gameTime = slime.level().getGameTime();
        if (!force && gameTime < nextThreatCheckTick) {
            return !threats.isEmpty();
        }

        List<Mob> currentThreats = getThreats();
        Set<java.util.UUID> currentThreatIds = new HashSet<>();
        for (Mob threat : currentThreats) {
            currentThreatIds.add(threat.getUUID());
        }
        threatsChanged = !currentThreatIds.equals(threatIds);
        threats = currentThreats;
        threatIds = currentThreatIds;
        nextThreatCheckTick = gameTime + 10L;
        if (currentThreats.isEmpty()) {
            SlimeFormMod.clearRecoveryFleeRoute(slime);
        }
        return !threats.isEmpty();
    }

    private boolean requestPath(boolean force) {
        long gameTime = slime.level().getGameTime();
        if (!force && gameTime < nextRepathTick) {
            return fleePath != null;
        }

        SlimeFormMod.RecoveryFleePathResult result = SlimeFormMod.getRecoveryFleePath(
                slime,
                threats,
                sharedRouteVersion,
                sharedWaypointIndex);
        fleePath = result.path();
        sharedRouteVersion = result.routeVersion();
        sharedWaypointIndex = result.waypointIndex();
        sharedRouteActive = result.routeActive();
        nextRepathTick = gameTime + 10L;
        return fleePath != null;
    }

    private void steerAlongPath() {
        SlimeMoveControlAccess moveControl = (SlimeMoveControlAccess) slime.getMoveControl();
        if (fleePath == null || fleePath.isDone()) {
            moveControl.slimeform$setWantedMovement(0.0D);
            return;
        }

        while (!fleePath.isDone()
                && slime.distanceToSqr(
                        fleePath.getNextNodePos().getX() + 0.5D,
                        fleePath.getNextNodePos().getY(),
                        fleePath.getNextNodePos().getZ() + 0.5D) <= 1.5D) {
            fleePath.advance();
        }
        if (fleePath.isDone()) {
            moveControl.slimeform$setWantedMovement(0.0D);
            return;
        }

        // SlimeMoveControl ignores generic wanted-position coordinates. Use
        // its native direction and movement commands so the normal slime jump
        // cycle travels through the calculated path nodes.
        Vec3 next = fleePath.getNextNodePos().getCenter();
        float yaw = (float) Math.toDegrees(Math.atan2(
                next.z - slime.getZ(),
                next.x - slime.getX())) - 90.0F;
        moveControl.slimeform$setDirection(yaw, true);
        moveControl.slimeform$setWantedMovement(1.2D);
    }

    private void holdPosition() {
        SlimeMoveControlAccess moveControl = (SlimeMoveControlAccess) slime.getMoveControl();
        moveControl.slimeform$setDirection(slime.getYRot(), false);
        moveControl.slimeform$setWantedMovement(0.0D);
    }

    private void visualizePath() {
        SlimeFormMod.visualizeRecoveryFleePath(slime, fleePath, threats);
    }

    private List<Mob> getThreats() {
        return SlimeFormMod.getRecoveryFleeThreats(slime);
    }
}
