package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeMoveControlAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.world.entity.monster.cubemob.AbstractCubeMob$CubeMobMoveControl")
public interface SlimeMoveControlMixin extends SlimeMoveControlAccess {
    @Override
    @Invoker("setWantedMovement")
    void slimeform$setWantedMovement(double speed);

    @Override
    @Invoker("setDirection")
    void slimeform$setDirection(float yaw, boolean aggressive);
}
