package io.asy.fragmented;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/** Persistent slime-form state and player attribute synchronization. */
public final class SlimeFormState {
    public static final int MIN_SIZE = 1;
    private static final String SIZE_PREFIX = "slimeform.size.";
    private static final Identifier HEALTH_MODIFIER_ID =
            Identifier.fromNamespaceAndPath(SlimeFormMod.MOD_ID, "slime_form_health");

    private SlimeFormState() {
    }

    public static boolean isActive(Player player) {
        return player.getTags().contains(SlimeFormMod.SLIME_FORM_TAG);
    }

    public static int getMaxSize() {
        return SlimeFormConfig.get().effectiveMaxSlimeSize();
    }

    public static int getSize(Player player) {
        for (int size = SlimeFormConfig.MAX_MAX_SLIME_SIZE; size >= MIN_SIZE; size--) {
            if (player.getTags().contains(SIZE_PREFIX + size)) {
                return Math.min(size, getMaxSize());
            }
        }
        return getMaxSize();
    }

    /**
     * Resolves the slime size for rider positioning, including the client-side
     * max-health fallback when synchronized size tags are unavailable.
     */
    public static int getRiderSize(Player player) {
        for (int size = SlimeFormConfig.MAX_MAX_SLIME_SIZE; size >= MIN_SIZE; size--) {
            if (player.getTags().contains(SIZE_PREFIX + size)) {
                return Math.min(size, getMaxSize());
            }
        }

        if (player.level().isClientSide()) {
            float maxHealth = player.getMaxHealth();
            for (int size = getMaxSize(); size >= MIN_SIZE; size--) {
                if (maxHealth == maxHealthForSize(size)) {
                    return size;
                }
            }
        }

        return getMaxSize();
    }

    public static void activate(ServerPlayer player) {
        player.addTag(SlimeFormMod.SLIME_FORM_TAG);
        setSize(player, getMaxSize());
        applyHealth(player, true);
    }

    public static void setSize(Player player, int size) {
        for (int current = MIN_SIZE; current <= SlimeFormConfig.MAX_MAX_SLIME_SIZE; current++) {
            player.removeTag(SIZE_PREFIX + current);
        }
        player.addTag(SIZE_PREFIX + Math.max(MIN_SIZE, Math.min(getMaxSize(), size)));
    }

    public static void deactivate(Player player) {
        player.removeTag(SlimeFormMod.SLIME_FORM_TAG);
        for (int size = MIN_SIZE; size <= SlimeFormConfig.MAX_MAX_SLIME_SIZE; size++) {
            player.removeTag(SIZE_PREFIX + size);
        }
        removeHealthModifier(player);
    }

    public static float maxHealthForSize(int size) {
        return size * size;
    }

    /**
     * Determines whether the passenger offset should be applied on either side.
     * Persistent entity tags are authoritative on the server but are not
     * guaranteed to be present on the client, so the client also uses the
     * synchronized size-based max-health attribute as its fallback.
     */
    public static boolean shouldApplyRiderOffset(Player player) {
        if (isActive(player)) {
            return getSize(player) >= MIN_SIZE;
        }

        if (!player.level().isClientSide()) {
            return false;
        }

        float maxHealth = player.getMaxHealth();
        return maxHealth >= maxHealthForSize(2)
                && maxHealth <= maxHealthForSize(getMaxSize())
                && maxHealth != 20.0F;
    }

    /**
     * Resolves whether the client should apply SlimeForm-only visual behavior.
     * Entity tags are authoritative, with max health as the client sync fallback.
     */
    public static boolean isClientVisualSlimeForm(Player player) {
        if (isActive(player)) {
            return true;
        }
        if (!player.level().isClientSide()) {
            return false;
        }

        float maxHealth = player.getMaxHealth();
        return maxHealth >= maxHealthForSize(MIN_SIZE)
                && maxHealth <= maxHealthForSize(getMaxSize())
                && maxHealth != 20.0F;
    }

    public static void applyHealth(Player player, boolean refill) {
        if (!isActive(player)) {
            removeHealthModifier(player);
            return;
        }

        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.addOrUpdateTransientModifier(new AttributeModifier(
                    HEALTH_MODIFIER_ID,
                    maxHealthForSize(getSize(player)) - 20.0D,
                    AttributeModifier.Operation.ADD_VALUE));
        }

        float maximum = player.getMaxHealth();
        if (refill || player.getHealth() <= 0.0F) {
            player.setHealth(maximum);
        } else if (player.getHealth() > maximum) {
            player.setHealth(maximum);
        }
    }

    private static void removeHealthModifier(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.removeModifier(HEALTH_MODIFIER_ID);
        }
    }
}
