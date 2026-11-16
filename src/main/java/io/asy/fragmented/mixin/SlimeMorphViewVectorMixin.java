package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While morphed, the camera is the slime body and vanilla aims block and entity targeting along the
 * camera entity's own view vector. The body never pitches and turns toward its movement instead of
 * following the mouse, so only blocks straight in front of it could be hit. Use the player's view
 * vector instead, so you can break, build and attack anywhere you are looking, like a normal player.
 */
@Mixin(Entity.class)
public abstract class SlimeMorphViewVectorMixin {
    @Inject(method = "getViewVector", at = @At("RETURN"), cancellable = true)
    private void slimeform$aimMorphBodyWithThePlayersView(
            float partialTick, CallbackInfoReturnable<Vec3> cir) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Slime)
                || !self.level().isClientSide()
                || !SlimeFormClient.isLocalMorphBodyActive()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.getCameraEntity() != self) {
            return;
        }
        cir.setReturnValue(client.player.getViewVector(partialTick));
    }
}
