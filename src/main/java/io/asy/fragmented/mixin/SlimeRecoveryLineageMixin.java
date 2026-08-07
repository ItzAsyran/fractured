package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeRecoveryLineage;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(Slime.class)
public abstract class SlimeRecoveryLineageMixin implements SlimeRecoveryLineage {
    @Unique
    private String slimeform$recoveryLineage;

    @Unique
    private UUID slimeform$recoveryParent;

    @Unique
    private int slimeform$recoveryGeneration;

    @Override
    public String slimeform$getRecoveryLineage() {
        return slimeform$recoveryLineage;
    }

    @Override
    public UUID slimeform$getRecoveryParent() {
        return slimeform$recoveryParent;
    }

    @Override
    public int slimeform$getRecoveryGeneration() {
        return slimeform$recoveryGeneration;
    }

    @Override
    public void slimeform$setRecoveryLineage(String lineageId, UUID parentId, int generation) {
        slimeform$recoveryLineage = lineageId;
        slimeform$recoveryParent = parentId;
        slimeform$recoveryGeneration = generation;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void slimeform$saveRecoveryLineage(ValueOutput output, CallbackInfo ci) {
        if (slimeform$getRecoveryLineage() == null || slimeform$getRecoveryLineage().isEmpty()) {
            return;
        }
        output.putString("SlimeFormRecoveryLineage", slimeform$getRecoveryLineage());
        if (slimeform$recoveryParent != null) {
            output.putString("SlimeFormRecoveryParent", slimeform$recoveryParent.toString());
        }
        output.putInt("SlimeFormRecoveryGeneration", slimeform$recoveryGeneration);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void slimeform$loadRecoveryLineage(ValueInput input, CallbackInfo ci) {
        String lineage = input.getStringOr("SlimeFormRecoveryLineage", "");
        if (lineage.isEmpty()) {
            return;
        }
        String parent = input.getStringOr("SlimeFormRecoveryParent", "");
        UUID parentId = parent.isEmpty() ? null : parseUuid(parent);
        slimeform$setRecoveryLineage(
                lineage,
                parentId,
                input.getIntOr("SlimeFormRecoveryGeneration", 0));
    }

    @Unique
    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
