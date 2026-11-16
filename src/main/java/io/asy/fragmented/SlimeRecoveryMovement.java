package io.asy.fragmented;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Movement helpers shared by the recovery goals (flee and regroup). */
final class SlimeRecoveryMovement {
    /** A node counts as reached within this squared distance (1.5 blocks). */
    private static final double REACH_DISTANCE_SQR = 2.25D;

    private SlimeRecoveryMovement() {
    }

    /**
     * SlimeMoveControl ignores generic wanted-position coordinates, so drive its native
     * direction and movement commands and let the normal jump cycle follow the path nodes.
     *
     * <p>Slimes travel in long hops, so one hop can carry them past a node. Nodes that are
     * already behind the slime are skipped instead of turning back for them, and the hop
     * direction is only chosen at take-off (it cannot change in mid-air anyway).
     */
    static void steerAlongPath(Slime slime, Path path, double speed) {
        SlimeMoveControlAccess moveControl = (SlimeMoveControlAccess) slime.getMoveControl();
        if (path == null || path.isDone()) {
            return;
        }
        skipReachedNodes(slime, path);
        if (path.isDone()) {
            return;
        }

        if (slime.onGround()) {
            Vec3 next = Vec3.atCenterOf(path.getNextNodePos());
            float yaw = (float) Math.toDegrees(Math.atan2(
                    next.z - slime.getZ(),
                    next.x - slime.getX())) - 90.0F;
            moveControl.slimeform$setDirection(yaw, true);
        }
        moveControl.slimeform$setWantedMovement(speed);
    }

    private static void skipReachedNodes(Slime slime, Path path) {
        while (!path.isDone()) {
            BlockPos node = path.getNextNodePos();
            boolean passed = slime.distanceToSqr(
                    node.getX() + 0.5D, node.getY(), node.getZ() + 0.5D) <= REACH_DISTANCE_SQR;
            if (!passed) {
                // Already past this node? Then the slime is closer to the node after it than
                // this node is.
                int index = nextNodeIndex(path);
                if (index + 1 < path.getNodeCount()) {
                    BlockPos following = path.getNodePos(index + 1);
                    double slimeToFollowing = slime.distanceToSqr(
                            following.getX() + 0.5D, following.getY(), following.getZ() + 0.5D);
                    passed = slimeToFollowing < node.distSqr(following);
                }
            }
            if (!passed) {
                return;
            }
            path.advance();
        }
    }

    /** Index of the node the path is currently heading for (node count once finished). */
    static int nextNodeIndex(Path path) {
        if (path == null || path.isDone()) {
            return path == null ? 0 : path.getNodeCount();
        }
        BlockPos next = path.getNextNodePos();
        for (int index = 0; index < path.getNodeCount(); index++) {
            if (path.getNodePos(index).equals(next)) {
                return index;
            }
        }
        return 0;
    }

    /**
     * Stand still. Do not call setWantedMovement(0): that puts the move control in its
     * MOVE_TO mode with speed 0, and a slime in MOVE_TO keeps running its jump cycle, so it
     * hops on the spot. Leaving the controller idle makes it stop hopping. The goals that call
     * this hold the MOVE/LOOK flags, which keeps vanilla wandering from taking over.
     */
    static void hold(Slime slime) {
        slime.getNavigation().stop();
    }

    /** Turn to face a direction without moving. */
    static void look(Slime slime, float yaw) {
        ((SlimeMoveControlAccess) slime.getMoveControl()).slimeform$setDirection(yaw, false);
    }

    static void stop(Slime slime) {
        slime.getNavigation().stop();
    }
}
