package io.asy.fragmented.mixin;

import io.asy.fragmented.EntityPositionRiderAccess;
import io.asy.fragmented.SlimeFormMod;
import io.asy.fragmented.SlimeFormState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class SlimePassengerPositionMixin implements EntityPositionRiderAccess {
    @Invoker("positionRider")
    @Override
    public abstract void slimeform$positionRider(Entity passenger, Entity.MoveFunction moveFunction);

    @Inject(
            method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V",
            at = @At("TAIL"))
    private void slimeform$addRiderOffset(
            Entity passenger, Entity.MoveFunction moveFunction, CallbackInfo ci) {
        if ((Object) this instanceof Slime
                && passenger instanceof Player player
                && SlimeFormState.shouldApplyRiderOffset(player)) {
            passenger.setPos(
                    passenger.getX() + SlimeFormMod.getRiderOffsetX(player),
                    passenger.getY() + SlimeFormMod.getRiderOffsetY(player),
                    passenger.getZ() + SlimeFormMod.getRiderOffsetZ(player));
        }
    }
}
