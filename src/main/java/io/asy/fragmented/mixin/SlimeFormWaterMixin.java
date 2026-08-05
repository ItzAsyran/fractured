package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormConfig;
import io.asy.fragmented.SlimeFormState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class SlimeFormWaterMixin {
    private static final double SLIME_WATER_SINK_FACTOR = 0.85D;

    @Inject(method = "getFluidFallingAdjustedMovement", at = @At("RETURN"), cancellable = true)
    private void slimeform$slowWaterSinking(
            double gravity,
            boolean falling,
            Vec3 movement,
            CallbackInfoReturnable<Vec3> cir) {
        if (!((Object) this instanceof Player player)
                || !SlimeFormState.isActive(player)
                || !SlimeFormConfig.get().slimeWaterBehavior
                || !player.isInWater()) {
            return;
        }

        Vec3 adjusted = cir.getReturnValue();
        if (adjusted.y < 0.0D) {
            cir.setReturnValue(new Vec3(adjusted.x, adjusted.y * SLIME_WATER_SINK_FACTOR, adjusted.z));
        }
    }
}
