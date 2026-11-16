package io.asy.fragmented;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Size 1 recovery fragments run from hostile mobs that can actually reach them.
 *
 * <p>Every fragment picks its own escape route and sticks to it. It remembers which way it has been
 * running and where it has been, so a new route normally continues in the same direction instead of
 * swinging back toward the danger, and it only re-plans when the route fails, would run into a threat,
 * or a new threat shows up close by. The goal ends as soon as nothing can reach the fragment any more.
 */
public final class SlimeRecoveryFleeGoal extends Goal {
    private static final int NO_PROGRESS_LIMIT_TICKS = 12;
    private static final int REPATH_COOLDOWN_TICKS = 10;
    private static final int TRAIL_SAMPLE_TICKS = 5;
    private static final int TRAIL_SIZE = 8;
    private static final double TRAIL_STEP_SQR = 0.64D;
    /** A threat that newly appears this close forces a re-plan (squared blocks). */
    private static final double CLOSE_THREAT_DISTANCE_SQR = 36.0D;
    private static final double FLEE_SPEED = 1.2D;
    private static final double PANIC_SPEED = 1.5D;
    private static final double PANIC_DISTANCE_SQR = 16.0D;
    /** Forget the old heading and trail after the goal has been idle this long. */
    private static final long MEMORY_TICKS = 60L;
    private final Slime slime;
    private Path fleePath;
    private List<Mob> threats = List.of();
    private Set<UUID> threatIds = Set.of();
    private boolean newCloseThreat;
    private long nextRepathTick;
    private Vec3 lastPosition;
    private int noProgressTicks;
    private Vec3 heading;
    private final List<Vec3> trail = new ArrayList<>();
    private long lastTrailTick;
    private BlockPos preferredTarget;

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
        if (fleePath == null && slime.level().getGameTime() >= nextRepathTick) {
            requestPath();
        }
        // If no usable route exists, release MOVE so vanilla slime AI can
        // take over instead of keeping this goal active and jumping in place.
        return fleePath != null;
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
        if (slime.level().getGameTime() - lastTrailTick > MEMORY_TICKS) {
            heading = null;
            trail.clear();
        }
        SlimeRecoveryMovement.steerAlongPath(slime, fleePath, speed());
        visualizePath();
        lastPosition = slime.position();
        noProgressTicks = 0;
        newCloseThreat = false;
    }

    @Override
    public void tick() {
        slime.setTarget(null);
        if (!refreshThreats()) {
            // Safe now. canContinueToUse() ends the goal on the next check.
            return;
        }
        updateHeadingAndTrail();

        boolean pathFailed = fleePath == null || fleePath.isDone();
        boolean routeUnsafe = !pathFailed
                && !SlimeFormMod.isRecoveryFleePathSafe(slime, fleePath, threats);
        boolean madeProgress = lastPosition == null
                || slime.position().distanceToSqr(lastPosition) > 0.01D;
        noProgressTicks = madeProgress ? 0 : noProgressTicks + 1;
        lastPosition = slime.position();

        if (noProgressTicks >= NO_PROGRESS_LIMIT_TICKS) {
            // Do not keep feeding the slime the same movement command when
            // navigation has failed to move it through enclosed terrain. Remember where that
            // route was heading so the next plan avoids the same dead end.
            rememberDeadEnd();
            fleePath = null;
            slime.getNavigation().stop();
            SlimeRecoveryMovement.hold(slime);
            newCloseThreat = false;
            return;
        }

        boolean cooled = slime.level().getGameTime() >= nextRepathTick;
        if (pathFailed || ((routeUnsafe || newCloseThreat) && cooled)) {
            Path replacement = requestPath();
            if (replacement != null) {
                noProgressTicks = 0;
            } else if (pathFailed) {
                fleePath = null;
            }
            // Otherwise keep the route we have: a committed route beats no route.
            newCloseThreat = false;
        }
        SlimeRecoveryMovement.steerAlongPath(slime, fleePath, speed());
        visualizePath();
    }

    @Override
    public void stop() {
        fleePath = null;
        threats = List.of();
        threatIds = Set.of();
        newCloseThreat = false;
        preferredTarget = null;
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
                if (!threatIds.contains(threat.getUUID())
                        && slime.distanceToSqr(threat) <= CLOSE_THREAT_DISTANCE_SQR) {
                    newCloseThreat = true;
                }
            }
            threats = current;
            threatIds = currentIds;
        }
        return !threats.isEmpty();
    }

    /** Plans a new route and adopts it if one was found. Returns it, or null if there was none. */
    private Path requestPath() {
        long gameTime = slime.level().getGameTime();
        nextRepathTick = gameTime + REPATH_COOLDOWN_TICKS;
        Path found = SlimeFormMod.findRecoveryFleePath(slime, threats, heading, trail, preferredTarget);
        if (found != null) {
            fleePath = found;
            preferredTarget = endOf(found);
        } else if (fleePath == null) {
            SlimeFormMod.setRecoveryRegroupStatus(slime, "no escape route");
        }
        return found;
    }

    private static BlockPos endOf(Path path) {
        return path.getEndNode() == null ? null : path.getEndNode().asBlockPos();
    }

    private void rememberDeadEnd() {
        BlockPos end = fleePath == null ? null : endOf(fleePath);
        if (end != null) {
            trail.add(Vec3.atCenterOf(end));
            if (trail.size() > TRAIL_SIZE) {
                trail.remove(0);
            }
        }
        preferredTarget = null;
    }

    private double speed() {
        for (Mob threat : threats) {
            if (slime.distanceToSqr(threat) <= PANIC_DISTANCE_SQR) {
                return PANIC_SPEED;
            }
        }
        return FLEE_SPEED;
    }

    /** Remembers the way we are running and a short trail of where we have been. */
    private void updateHeadingAndTrail() {
        long now = slime.level().getGameTime();
        Vec3 position = slime.position();
        if (trail.isEmpty()) {
            trail.add(position);
            lastTrailTick = now;
            return;
        }
        if (now - lastTrailTick < TRAIL_SAMPLE_TICKS) {
            return;
        }
        lastTrailTick = now;
        Vec3 last = trail.get(trail.size() - 1);
        double dx = position.x - last.x;
        double dz = position.z - last.z;
        double distanceSqr = dx * dx + dz * dz;
        if (distanceSqr < TRAIL_STEP_SQR) {
            return;
        }
        double length = Math.sqrt(distanceSqr);
        double nx = dx / length;
        double nz = dz / length;
        if (heading == null) {
            heading = new Vec3(nx, 0.0D, nz);
        } else {
            double hx = heading.x * 0.5D + nx * 0.5D;
            double hz = heading.z * 0.5D + nz * 0.5D;
            double hl = Math.sqrt(hx * hx + hz * hz);
            heading = hl < 0.0001D ? new Vec3(nx, 0.0D, nz) : new Vec3(hx / hl, 0.0D, hz / hl);
        }
        trail.add(position);
        if (trail.size() > TRAIL_SIZE) {
            trail.remove(0);
        }
    }

    private void visualizePath() {
        SlimeFormMod.visualizeRecoveryFleePath(slime, fleePath, threats);
    }
}
