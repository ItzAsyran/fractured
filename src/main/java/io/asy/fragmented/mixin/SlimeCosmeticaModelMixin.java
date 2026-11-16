package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeCosmeticaCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Cosmetica compat (2/2): applies the slime-form transparency and tint to the per-layer tint array
 * that CosmeticaModel.submitOnPart builds right before it submits the accessory model. Cosmetica's
 * quads are baked on the translucent layer with tint index 0, so alpha in that array takes effect.
 * Pseudo + require = 0: harmless when Cosmetica is not installed or its internals change.
 */
@Pseudo
@Mixin(targets = "cc.cosmetica.core.api.CosmeticaModel")
public abstract class SlimeCosmeticaModelMixin {
    @ModifyVariable(
            method = "submitOnPart",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Arrays;fill([II)V",
                    shift = At.Shift.AFTER),
            ordinal = 0,
            require = 0)
    private int[] slimeform$applyAccessoryOpacity(int[] tints) {
        return SlimeCosmeticaCompat.applyToTints(tints);
    }
}
