package io.asy.fragmented;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;

/**
 * Shared mathematical formulas and classification logic for the absorb mechanic.
 * Used across the server, renderer, and UI to ensure consistent volume, capacity,
 * and gain computations.
 */
public final class SlimeAbsorbHelper {
    public static final double SLIME_DIMENSION_FACTOR = 0.52D;
    public static final double CALIBRATION_ZOMBIE_VOLUME = 0.702D;

    public static final ResourceKey<DamageType> ACID_DIGESTION_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(SlimeFormMod.MOD_ID, "acid_digestion"));

    private SlimeAbsorbHelper() {
    }

    /**
     * Calculates entity volume based on real hitbox width and height (already scaled).
     * Volume = width * width * height.
     */
    public static double getEntityVolume(Entity entity) {
        if (entity == null) {
            return 0.0D;
        }
        double width = entity.getBbWidth();
        double height = entity.getBbHeight();
        return width * width * height;
    }

    /**
     * Calculates slime volume from size.
     * Slime volume = (0.52 * size)^3.
     */
    public static double getSlimeVolume(int size) {
        double edge = SLIME_DIMENSION_FACTOR * Math.max(1, size);
        return edge * edge * edge;
    }

    /**
     * Calculates absorb capacity from slime size and capacity factor.
     * Capacity = slime volume * absorbCapacityFactor.
     */
    public static double getSlimeCapacity(int size, double capacityFactor) {
        return getSlimeVolume(size) * capacityFactor;
    }

    /**
     * Unified digestion DPS formula:
     * DPS = absorbDigestBaseDps + absorbDigestDpsPerSizeAbove2 * max(0, slimeSize - 2)
     */
    public static float getDigestDps(int slimeSize, float baseDps, float dpsPerSizeAbove2) {
        return baseDps + dpsPerSizeAbove2 * Math.max(0, slimeSize - 2);
    }

    /**
     * Unified digestion pulse damage formula:
     * Pulse damage = DPS * (intervalTicks / 20.0F)
     */
    public static float getDigestPulseDamage(int slimeSize, float baseDps, float dpsPerSizeAbove2, int intervalTicks) {
        float dps = getDigestDps(slimeSize, baseDps, dpsPerSizeAbove2);
        return dps * ((float) intervalTicks / 20.0F);
    }

    /**
     * Creates an acid damage source that ignores armor via the #minecraft:bypasses_armor tag.
     * Direct entity is the slime holding the victim; causing entity is the owner (if present)
     * so kills count as player kills granting full loot tables, XP, and advancement credit.
     */
    public static DamageSource createAcidDamageSource(ServerLevel level, Slime slime, ServerPlayer owner) {
        var registry = level.registryAccess().lookup(Registries.DAMAGE_TYPE);
        if (registry.isPresent()) {
            var holder = registry.get().get(ACID_DIGESTION_TYPE);
            if (holder.isPresent()) {
                return new DamageSource(holder.get(), slime, owner != null ? owner : slime);
            }
        }
        return owner != null
                ? level.damageSources().indirectMagic(slime, owner)
                : level.damageSources().indirectMagic(slime, slime);
    }

    /**
     * Calculates dissolve health gain scaled to the entity volume relative to a zombie (0.702).
     */
    public static float getDissolveHealthGain(double entityVolume, float healthPerUnit) {
        return (float) ((entityVolume / CALIBRATION_ZOMBIE_VOLUME) * healthPerUnit);
    }

    /**
     * Calculates dissolve hunger gain scaled to the entity volume relative to a zombie (0.702).
     */
    public static float getDissolveHungerGain(double entityVolume, float hungerPerUnit) {
        return (float) ((entityVolume / CALIBRATION_ZOMBIE_VOLUME) * hungerPerUnit);
    }

    /**
     * Calculates proportional health gain for damage actually dealt:
     * gain = fullKillGain * (damageDealt / maxHealth)
     */
    public static float getProportionalHealthGain(
            double entityVolume, float maxHealth, float damageDealt, float healthPerUnit) {
        if (maxHealth <= 0.0F || damageDealt <= 0.0F) {
            return 0.0F;
        }
        float fullGain = getDissolveHealthGain(entityVolume, healthPerUnit);
        return fullGain * Math.min(1.0F, damageDealt / maxHealth);
    }

    /**
     * Calculates proportional hunger gain for damage actually dealt:
     * gain = fullKillGain * (damageDealt / maxHealth)
     */
    public static float getProportionalHungerGain(
            double entityVolume, float maxHealth, float damageDealt, float hungerPerUnit) {
        if (maxHealth <= 0.0F || damageDealt <= 0.0F) {
            return 0.0F;
        }
        float fullGain = getDissolveHungerGain(entityVolume, hungerPerUnit);
        return fullGain * Math.min(1.0F, damageDealt / maxHealth);
    }

    /**
     * Calculates linear fullness slowdown penalty up to maxSlowdown as heldVolume approaches capacity.
     * Returns negative multiplier, e.g. -0.25 (-25%).
     */
    public static double getFullnessSlowdown(double heldVolume, double capacity, double maxSlowdown) {
        if (capacity <= 0.0D || heldVolume <= 0.0D || maxSlowdown <= 0.0D) {
            return 0.0D;
        }
        double ratio = Math.min(1.0D, heldVolume / capacity);
        return -Math.abs(maxSlowdown) * ratio;
    }

    /**
     * Checks if the entity is an invalid absorb target due to type constraints:
     * non-LivingEntity, ArmorStand, missing SCALE attribute, disabled bosses, or listed in absorbBlockedEntities.
     */
    public static boolean isBlockedEntityType(Entity entity, SlimeFormConfig config) {
        if (!(entity instanceof LivingEntity living)) {
            return true;
        }
        if (living instanceof ArmorStand) {
            return true;
        }
        if (living.getAttributes() == null || !living.getAttributes().hasAttribute(Attributes.SCALE)) {
            return true;
        }
        if (isPlayerOriginOrSpecialSlime(entity)) {
            return true;
        }
        if (isBoss(entity) && !config.absorbBossesEnabled) {
            return true;
        }

        String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        return config.absorbBlockedEntities != null && config.absorbBlockedEntities.contains(id);
    }

    public static boolean isBoss(Entity entity) {
        return isWither(entity) || isElderGuardian(entity) || isWarden(entity);
    }

    public static boolean isWither(Entity entity) {
        return entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss;
    }

    public static boolean isElderGuardian(Entity entity) {
        return entity instanceof net.minecraft.world.entity.monster.ElderGuardian;
    }

    public static boolean isWarden(Entity entity) {
        return entity instanceof net.minecraft.world.entity.monster.warden.Warden;
    }

    /**
     * Confirms whether an entity is a player-associated slime: morph body, recovery fragment, or dormant slime.
     * These must never be absorbed under any circumstance.
     */
    public static boolean isPlayerOriginOrSpecialSlime(Entity entity) {
        if (entity instanceof Slime slime) {
            if (SlimeRecoveryManager.isPlayerOriginSlime(slime)
                    || SlimeRecoveryManager.hasRecoveryLineage(slime)
                    || SlimeMorphManager.isMorphBody(slime)) {
                return true;
            }
        }
        return entity.entityTags().contains(SlimeMorphManager.BODY_TAG)
                || entity.entityTags().contains(SlimeFormMod.PLAYER_RECOVERY_SLIME_TAG)
                || entity.entityTags().contains(SlimeFormMod.PLAYER_DORMANT_SLIME_TAG);
    }

    /**
     * Hostile vs Passive classification rule:
     * - HOSTILE = implements vanilla Enemy interface, plus absorbHostileOverrides.
     * - PASSIVE = everything else, plus absorbPassiveOverrides.
     * - PLAYERS follow PASSIVE rules.
     */
    public static boolean isHostile(LivingEntity entity, SlimeFormConfig config) {
        if (entity instanceof Player) {
            return false;
        }

        String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        if (config.absorbPassiveOverrides != null && config.absorbPassiveOverrides.contains(id)) {
            return false;
        }
        if (config.absorbHostileOverrides != null && config.absorbHostileOverrides.contains(id)) {
            return true;
        }

        return entity instanceof Enemy;
    }
}
