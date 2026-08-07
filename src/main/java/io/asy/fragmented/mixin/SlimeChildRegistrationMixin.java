package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormMod;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class SlimeChildRegistrationMixin {
    @Inject(
            method = "convertTo(Lnet/minecraft/world/entity/EntityType;"
                    + "Lnet/minecraft/world/entity/ConversionParams;"
                    + "Lnet/minecraft/world/entity/EntitySpawnReason;"
                    + "Lnet/minecraft/world/entity/ConversionParams$AfterConversion;)"
                    + "Lnet/minecraft/world/entity/Mob;",
            at = @At("RETURN"))
    private void slimeform$registerEveryConvertedChild(
            EntityType<?> entityType,
            ConversionParams conversionParams,
            EntitySpawnReason spawnReason,
            ConversionParams.AfterConversion<?> afterConversion,
            CallbackInfoReturnable<Mob> cir) {
        Mob parent = (Mob) (Object) this;
        if (parent instanceof Slime parentSlime
                && cir.getReturnValue() instanceof Slime childSlime
                && SlimeFormMod.assignRecoveryLineage(parentSlime, childSlime)) {
            SlimeFormMod.LOGGER.info(
                    "[slimeform] Registered vanilla split child parent={} child={} lineage={} parentUuid={} generation={}",
                    parent.getUUID(),
                    childSlime.getUUID(),
                    SlimeFormMod.getRecoveryLineage(childSlime),
                    SlimeFormMod.getRecoveryParent(childSlime),
                    SlimeFormMod.getRecoveryGeneration(childSlime));
        }
    }
}
