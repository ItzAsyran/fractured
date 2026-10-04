package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeMorphManager;
import io.asy.fragmented.SlimeFormState;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class SlimeMorphDimensionsMixin {
    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
    private void slimeform$useSlimeDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (!((Object) this instanceof Player player)) {
            return;
        }
        if (SlimeMorphManager.isMorphedClient(player)) {
            float size = SlimeMorphManager.getCurrentMorphSize(player);
            float edge = Math.max(0.35F, size * 0.51F);
            cir.setReturnValue(EntityDimensions.scalable(edge, edge));
        }
    }
}
