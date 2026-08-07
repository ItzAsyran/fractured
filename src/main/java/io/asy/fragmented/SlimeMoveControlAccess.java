package io.asy.fragmented;

/** Access to the movement commands implemented specifically by SlimeMoveControl. */
public interface SlimeMoveControlAccess {
    void slimeform$setWantedMovement(double speed);

    void slimeform$setDirection(float yaw, boolean aggressive);
}
