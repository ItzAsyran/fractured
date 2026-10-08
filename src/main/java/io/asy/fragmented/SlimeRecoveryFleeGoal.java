package io.asy.fragmented;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Size 1 recovery fragments run from hostile mobs that can actually reach them.
 *
 * <p>Every fragment picks its own escape route. The goal ends as soon as nothing can reach
 * the fragment any more (it got far enough away, or squeezed somewhere the mob cannot follow),
 * so a safe fragment never keeps wandering off.
 */
public final class SlimeRecoveryFleeGoal extends Goal {
    private static final int NO_PROGRESS_LIMIT_TICKS = 12;
    private static final int REPATH_INTERVAL_TICKS = 10;
    private static final double FLEE_SPEED = 1.2D;
    private final Slime slime;
    private Path fleePath;
    private List<Mob> threats = List.of();
    private Set<UUID> threatIds = Set.of();
    private boolean threatsChanged;
    private long nextRepathTick;
    private Vec3 lastPosition;
    private int noProgressTicks;

    public SlimeRecoveryFleeGoal(Slime slime) {
        this.slime = slime;
        // Vanilla SlimeRandomDirectionGoal uses LOOK to overwrite the slime's
        // move controller. Reserve both flags while the flee path is active.
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!isEligible() || !refreshThreats()) {
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
        // Stop the moment this fragment is safe, even if a sibling is still being chased.
        return isEligible()
                && refreshThreats()
                && fleePath != null
                && !fleePath.isDone();
    }

    @Override
    public void start() {
        SlimeFormMod.setRecoveryRegroupStatus(slime, "fleeing");
        slime.setTarget(null);
        SlimeRecoveryMovement.steerAlongPath(slime, fleePath, FLEE_SPEED);
        visualizePath();
        lastPosition = slime.position();
        noProgressTicks = 0;
        threatsChanged = false;
    }

    @Override
    public void tick() {
        slime.setTarget(null);
        if (!refreshThreats()) {
            // Safe now. canContinueToUse() ends the goal on the next check.
            return;
        }

        boolean pathFailed = fleePath == null || fleePath.isDone();
        boolean pathTurnsTowardThreat = fleePath != null
                && !SlimeFormMod.isRecoveryFleePathSafe(slime, fleePath, threats);
        boolean madeProgress = lastPosition == null
                || slime.position().distanceToSqr(lastPosition) > 0.01D;
        noProgressTicks = madeProgress ? 0 : noProgressTicks + 1;
        lastPosition = slime.position();

        if (noProgressTicks >= NO_PROGRESS_LIMIT_TICKS) {
            // Do not keep feeding the slime the same movement command when
            // navigation has failed to move it through enclosed terrain.
            fleePath = null;
            slime.getNavigation().stop();
            SlimeRecoveryMovement.hold(slime);
            threatsChanged = false;
            return;
        }

        if (threatsChanged || pathFailed || pathTurnsTowardThreat) {
            if (pathFailed || pathTurnsTowardThreat) {
                fleePath = null;
            }
            if (requestPath(threatsChanged || pathTurnsTowardThreat)) {
                noProgressTicks = 0;
            }
            threatsChanged = false;
        }
        SlimeRecoveryMovement.steerAlongPath(slime, fleePath, FLEE_SPEED);
        visualizePath();
    }

    @Override
    public void stop() {
        fleePath = null;
        threats = List.of();
        threatIds = Set.of();
        threatsChanged = false;
        SlimeRecoveryMovement.stop(slime);
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

    /** Re-reads this fragment's reachable threats (cached briefly by the recovery manager). */
    private boolean refreshThreats() {
        List<Mob> current = SlimeFormMod.getRecoveryFragmentThreats(slime);
        if (current != threats) {
            Set<UUID> currentIds = new HashSet<>();
            for (Mob threat : current) {
                currentIds.add(threat.getUUID());
            }
            if (!currentIds.equals(threatIds)) {
                threatsChanged = true;
            }
            threats = current;
            threatIds = currentIds;
        }
        return !threats.isEmpty();
    }

    private boolean requestPath(boolean force) {
        long gameTime = slime.level().getGameTime();
        if (!force && gameTime < nextRepathTick) {
            return fleePath != null;
        }
        fleePath = SlimeFormMod.findRecoveryFleePath(slime, threats);
        nextRepathTick = gameTime + REPATH_INTERVAL_TICKS;
        return fleePath != null;
    }

    private void visualizePath() {
        SlimeFormMod.visualizeRecoveryFleePath(slime, fleePath, threats);
    }
}
