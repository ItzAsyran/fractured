package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeDefenseTargetGoal;
import io.asy.fragmented.SlimeRecoveryFleeGoal;
import io.asy.fragmented.SlimeRecoveryRegroupGoal;
import net.minecraft.world.entity.monster.cubemob.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Slime.class)
public abstract class SlimeRecoveryFleeGoalMixin {
    @Inject(method = "addBehaviourGoals", at = @At("TAIL"))
    private void slimeform$addSlimeGoals(CallbackInfo ci) {
        MobGoalSelectorAccessor access = (MobGoalSelectorAccessor) (Object) this;
        access.slimeform$getGoalSelector().addGoal(
                0,
                new SlimeRecoveryFleeGoal((Slime) (Object) this));
        // Calm fragments gather around the group's anchor. Priority 2 sits above vanilla
        // wandering (3) and keep-jumping (5); it steps aside whenever the slime has a target.
        access.slimeform$getGoalSelector().addGoal(
                2,
                new SlimeRecoveryRegroupGoal((Slime) (Object) this));
        access.slimeform$getTargetSelector().addGoal(
                1,
                new SlimeDefenseTargetGoal((Slime) (Object) this));
    }
}
