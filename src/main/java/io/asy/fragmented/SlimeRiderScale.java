package io.asy.fragmented;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.cubemob.Slime;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Applies the mounted slime's scale to the player's synchronized size attribute. */
public final class SlimeRiderScale {
    private static final float MINIMUM_PLAYER_SCALE = 0.20F;
    private static final float PLAYER_SCALE_PER_SLIME_SIZE = 0.20F;
    private static final float MAXIMUM_PLAYER_SCALE = 100.0F;
    private static final Identifier RIDER_SCALE_MODIFIER_ID =
            Identifier.fromNamespaceAndPath(SlimeFormMod.MOD_ID, "slime_rider_scale");
    private static final Map<UUID, Integer> APPLIED_SLIME_SIZES = new HashMap<>();

    private SlimeRiderScale() {
    }

    public static void tick(ServerPlayer player) {
        AttributeInstance scaleAttribute = player.getAttribute(Attributes.SCALE);
        if (scaleAttribute == null) {
            return;
        }

        UUID playerId = player.getUUID();
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof Slime slime) {
            int slimeSize = Math.max(1, slime.getSize());
            Integer appliedSize = APPLIED_SLIME_SIZES.get(playerId);
            if (appliedSize == null || appliedSize != slimeSize) {
                double playerScale = Math.min(
                        MAXIMUM_PLAYER_SCALE,
                        MINIMUM_PLAYER_SCALE + (slimeSize - 1) * PLAYER_SCALE_PER_SLIME_SIZE);
                scaleAttribute.addOrUpdateTransientModifier(new AttributeModifier(
                        RIDER_SCALE_MODIFIER_ID,
                        playerScale - 1.0D,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                player.refreshDimensions();
                APPLIED_SLIME_SIZES.put(playerId, slimeSize);
            }
        } else if (APPLIED_SLIME_SIZES.remove(playerId) != null) {
            scaleAttribute.removeModifier(RIDER_SCALE_MODIFIER_ID);
            player.refreshDimensions();
        }
    }

    public static void clear(ServerPlayer player) {
        APPLIED_SLIME_SIZES.remove(player.getUUID());
        AttributeInstance scaleAttribute = player.getAttribute(Attributes.SCALE);
        if (scaleAttribute != null) {
            scaleAttribute.removeModifier(RIDER_SCALE_MODIFIER_ID);
        }
    }
}
