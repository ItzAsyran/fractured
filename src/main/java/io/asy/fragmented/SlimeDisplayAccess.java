package io.asy.fragmented;

import com.mojang.math.Transformation;

public interface SlimeDisplayAccess {
    void slimeform$setTransformation(Transformation transformation);

    /** Ticks the client blends over when the transformation changes (0 = jump). */
    void slimeform$setTransformationInterpolationDuration(int ticks);

    /** Ticks until the blend starts, counted from when the change is received (0 = now). */
    void slimeform$setTransformationInterpolationDelay(int ticks);
}
