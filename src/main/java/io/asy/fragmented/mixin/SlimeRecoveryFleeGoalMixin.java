package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeRecoveryFleeGoal;
import net.minecraft.world.entity.monster.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Slime.class)
public abstract class SlimeRecoveryFleeGoalMixin {
    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void slimeform$addRecoveryFleeGoal(CallbackInfo ci) {
        MobGoalSelectorAccessor access = (MobGoalSelectorAccessor) (Object) this;
        access.slimeform$getGoalSelector().addGoal(
                0,
                new SlimeRecoveryFleeGoal((Slime) (Object) this));
    }
}
