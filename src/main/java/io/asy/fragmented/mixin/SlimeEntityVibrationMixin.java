package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormState;
import io.asy.fragmented.SlimeMorphManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class SlimeEntityVibrationMixin {
    @Inject(method = "dampensVibrations", at = @At("HEAD"), cancellable = true)
    private void slimeform$dampenVibrationsWhenSculkMutated(CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player player && SlimeFormState.hasMutation(player, SlimeFormState.MUTATION_WARDEN)) {
            cir.setReturnValue(true);
        } else if (entity instanceof Slime slime && SlimeMorphManager.isMorphBody(slime)) {
            if (!slime.level().isClientSide()) {
                ServerPlayer owner = SlimeMorphManager.ownerOf(slime);
                if (owner != null && SlimeFormState.hasMutation(owner, SlimeFormState.MUTATION_WARDEN)) {
                    cir.setReturnValue(true);
                }
            }
        }
    }
}
