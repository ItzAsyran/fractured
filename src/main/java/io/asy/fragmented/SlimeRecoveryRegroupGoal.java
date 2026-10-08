package io.asy.fragmented;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Calm recovery fragments gather around the group's anchor fragment, like small slimes
 * rejoining a bigger one. The anchor itself stands still and waits.
 *
 * <p>Runs below the flee goal (a threatened size 1 fragment flees first) and gives way to
 * attacking: it never runs while a bigger slime has a target. It also reports a short
 * status for each fragment (shown while gathering) so a stuck fragment can be diagnosed.
 */
public final class SlimeRecoveryRegroupGoal extends Goal {
    private static final int REPATH_INTERVAL_TICKS = 10;
    private static final int NO_PROGRESS_LIMIT_TICKS = 20;
    private static final double ARRIVE_DISTANCE_SQR = 2.25D;
    /** A route that cannot quite finish still counts if it ends this close to the anchor. */
    private static final double PARTIAL_PATH_END_DISTANCE_SQR = 6.25D;
    private static final double REGROUP_SPEED = 1.0D;
    private final Slime slime;
    private Path path;
    private long nextRepathTick;
    private Vec3 lastPosition;
    private int noProgressTicks;
    private String blockReason = "no path to anchor";

    public SlimeRecoveryRegroupGoal(Slime slime) {
        this.slime = slime;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return shouldRegroup();
    }

    @Override
    public boolean canContinueToUse() {
        return shouldRegroup();
    }

    @Override
    public void start() {
        path = null;
        nextRepathTick = 0L;
        lastPosition = slime.position();
        noProgressTicks = 0;
    }

    @Override
    public void tick() {
        Slime anchor = SlimeFormMod.getRecoveryRegroupAnchor(slime);
        if (anchor == null) {
            return;
        }
        if (anchor == slime) {
            path = null;
            report("anchor (waiting)");
            SlimeRecoveryMovement.hold(slime);
            return;
        }
        if (slime.distanceToSqr(anchor) <= ARRIVE_DISTANCE_SQR) {
            path = null;
            report("with anchor");
            SlimeRecoveryMovement.hold(slime);
            return;
        }

        long gameTime = slime.level().getGameTime();
        boolean madeProgress = lastPosition == null
                || slime.position().distanceToSqr(lastPosition) > 0.01D;
        noProgressTicks = madeProgress ? 0 : noProgressTicks + 1;
        lastPosition = slime.position();
        if (noProgressTicks >= NO_PROGRESS_LIMIT_TICKS) {
            // Stuck on terrain: stop pushing, and try a fresh route a little later.
            path = null;
            noProgressTicks = 0;
            nextRepathTick = gameTime + REPATH_INTERVAL_TICKS * 2L;
            report("stuck on terrain");
            SlimeRecoveryMovement.hold(slime);
            return;
        }

        if (path == null || path.isDone() || gameTime >= nextRepathTick) {
            path = findPathTo(anchor);
            nextRepathTick = gameTime + REPATH_INTERVAL_TICKS;
        }
        if (path == null) {
            // No safe route right now (or none at all): wait where we are.
            report(blockReason);
            SlimeRecoveryMovement.hold(slime);
            return;
        }
        report("walking to anchor");
        SlimeRecoveryMovement.steerAlongPath(slime, path, REGROUP_SPEED);
    }

    @Override
    public void stop() {
        path = null;
        SlimeRecoveryMovement.stop(slime);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean shouldRegroup() {
        if (slime.level().isClientSide()
                || !slime.isAlive()
                || !SlimeFormMod.hasRecoveryLineage(slime)) {
            return false;
        }
        if (slime.getTarget() != null) {
            if (slime.getSize() != SlimeFormState.MIN_SIZE) {
                report("fighting");
                return false;
            }
            // Size 1 fragments never fight during recovery, so a leftover target would only
            // keep them from joining the group.
            slime.setTarget(null);
        }
        // Anything that can reach this fragment is handled by fleeing or fighting first.
        if (!SlimeFormMod.getRecoveryFragmentThreats(slime).isEmpty()) {
            report("threatened");
            return false;
        }
        if (SlimeFormMod.getRecoveryRegroupAnchor(slime) == null) {
            report("no safe anchor");
            return false;
        }
        return true;
    }

    private Path findPathTo(Slime anchor) {
        Path candidate = slime.getNavigation().createPath(anchor.blockPosition(), 1);
        if (candidate == null || candidate.getNodeCount() < 2) {
            blockReason = "no path to anchor";
            return null;
        }
        if (!candidate.canReach()) {
            BlockPos end = candidate.getEndNode() == null ? null : candidate.getEndNode().asBlockPos();
            if (end == null || end.distSqr(anchor.blockPosition()) > PARTIAL_PATH_END_DISTANCE_SQR) {
                blockReason = "anchor unreachable";
                return null;
            }
        }
        if (!SlimeFormMod.isRecoveryRegroupPathSafe(slime, candidate)) {
            blockReason = "route unsafe (mob near)";
            return null;
        }
        return candidate;
    }

    private void report(String status) {
        SlimeFormMod.setRecoveryRegroupStatus(slime, status);
    }
}
