package io.asy.fragmented;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;

/**
 * Applies and removes transient scale modifications for absorbed entities inside a slime.
 * Uses an independent modifier ID to avoid conflicting with SlimeRiderScale or morph scaling.
 */
public final class SlimeAbsorbScale {
    public static final Identifier ABSORB_SCALE_MODIFIER_ID =
            Identifier.fromNamespaceAndPath(SlimeFormMod.MOD_ID, "slime_absorb_scale");

    private SlimeAbsorbScale() {
    }

    /**
     * Dismounts the entity if riding and removes rider scaling before capacity checks and absorption.
     */
    public static void prepareForAbsorb(LivingEntity entity) {
        if (entity.isPassenger()) {
            entity.stopRiding();
            if (entity instanceof Player player) {
                SlimeRiderScale.removeRiderScale(player);
            }
        }
    }

    /**
     * Calculates the scale factor for the absorbed entity so it fits cleanly inside the slime.
     * Takes into account the configured fit target scale and the slime's interior dimensions.
     */
    public static double computeFitScale(LivingEntity entity, Slime slime, SlimeFormConfig config) {
        double maxDimension = Math.max(entity.getBbWidth(), entity.getBbHeight());
        double slimeInterior = slime.getBbHeight() * 0.82D;
        double dimensionalFit = maxDimension > 0.0D ? Math.min(1.0D, slimeInterior / maxDimension) : 1.0D;
        double targetScale = Math.min(config.effectiveAbsorbFitTargetScale(), dimensionalFit);
        return Math.max(0.1D, Math.min(1.0D, targetScale));
    }

    /**
     * Applies the absorb shrink modifier to the entity and refreshes its dimensions.
     */
    public static void applyAbsorbScale(LivingEntity entity, Slime slime, SlimeFormConfig config) {
        AttributeInstance scaleAttribute = entity.getAttribute(Attributes.SCALE);
        if (scaleAttribute == null) {
            return;
        }

        double scale = computeFitScale(entity, slime, config);
        scaleAttribute.addOrUpdateTransientModifier(new AttributeModifier(
                ABSORB_SCALE_MODIFIER_ID,
                scale - 1.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        entity.refreshDimensions();
    }

    /**
     * Removes only the absorb scale modifier upon ejection, restoring original entity size.
     */
    public static void removeAbsorbScale(LivingEntity entity) {
        AttributeInstance scaleAttribute = entity.getAttribute(Attributes.SCALE);
        if (scaleAttribute != null) {
            scaleAttribute.removeModifier(ABSORB_SCALE_MODIFIER_ID);
            entity.refreshDimensions();
        }
    }
}
