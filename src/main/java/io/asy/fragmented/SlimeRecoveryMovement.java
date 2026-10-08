package io.asy.fragmented;

import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Movement helpers shared by the recovery goals (flee and regroup). */
final class SlimeRecoveryMovement {
    private SlimeRecoveryMovement() {
    }

    /**
     * SlimeMoveControl ignores generic wanted-position coordinates, so drive its native
     * direction and movement commands and let the normal jump cycle follow the path nodes.
     */
    static void steerAlongPath(Slime slime, Path path, double speed) {
        SlimeMoveControlAccess moveControl = (SlimeMoveControlAccess) slime.getMoveControl();
        if (path == null || path.isDone()) {
            return;
        }

        while (!path.isDone()
                && slime.distanceToSqr(
                        path.getNextNodePos().getX() + 0.5D,
                        path.getNextNodePos().getY(),
                        path.getNextNodePos().getZ() + 0.5D) <= 1.5D) {
            path.advance();
        }
        if (path.isDone()) {
            return;
        }

        Vec3 next = Vec3.atCenterOf(path.getNextNodePos());
        float yaw = (float) Math.toDegrees(Math.atan2(
                next.z - slime.getZ(),
                next.x - slime.getX())) - 90.0F;
        moveControl.slimeform$setDirection(yaw, true);
        moveControl.slimeform$setWantedMovement(speed);
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

    static void stop(Slime slime) {
        slime.getNavigation().stop();
    }
}
