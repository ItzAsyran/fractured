package io.asy.fragmented;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Persistent slime-form state and player attribute synchronization. */
public final class SlimeFormState {
    public static final int MIN_SIZE = 1;
    public static final String MUTATION_WITHER = "slimeform.mutation.wither";
    public static final String MUTATION_ELDER_GUARDIAN = "slimeform.mutation.elder_guardian";
    public static final String MUTATION_WARDEN = "slimeform.mutation.warden";

    private static final String SIZE_PREFIX = "slimeform.size.";
    private static final String MORPH_SIZE_PREFIX = "slimeform.morph_size.";
    private static final Identifier HEALTH_MODIFIER_ID =
            Identifier.fromNamespaceAndPath(SlimeFormMod.MOD_ID, "slime_form_health");
    private static volatile boolean clientPhaseEnabled;
    private static final Set<String> clientMutations = ConcurrentHashMap.newKeySet();

    private SlimeFormState() {
    }

    public static boolean hasMutation(Player player, String mutation) {
        if (player == null) {
            return false;
        }
        if (player.level().isClientSide()) {
            return clientMutations.contains(mutation) || player.entityTags().contains(mutation);
        }
        return player.entityTags().contains(mutation);
    }

    public static void setClientMutations(List<String> mutations) {
        clientMutations.clear();
        if (mutations != null) {
            clientMutations.addAll(mutations);
        }
    }

    public static void syncMutations(ServerPlayer player) {
        if (player == null || player.connection == null) {
            return;
        }
        List<String> active = new ArrayList<>();
        if (player.entityTags().contains(MUTATION_WITHER)) active.add(MUTATION_WITHER);
        if (player.entityTags().contains(MUTATION_ELDER_GUARDIAN)) active.add(MUTATION_ELDER_GUARDIAN);
        if (player.entityTags().contains(MUTATION_WARDEN)) active.add(MUTATION_WARDEN);
        ServerPlayNetworking.send(player, new SlimeFormPayloads.MutationSyncPayload(active));
    }

    public static void grantMutation(ServerPlayer player, String mutation) {
        if (player == null || player.entityTags().contains(mutation)) {
            return;
        }
        player.addTag(mutation);
        syncMutations(player);

        String translationKey = switch (mutation) {
            case MUTATION_WITHER -> "slimeform.mutation.wither.unlocked";
            case MUTATION_ELDER_GUARDIAN -> "slimeform.mutation.elder_guardian.unlocked";
            case MUTATION_WARDEN -> "slimeform.mutation.warden.unlocked";
            default -> "slimeform.mutation.generic.unlocked";
        };
        player.sendSystemMessage(Component.translatable(translationKey).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    public static void tickPlayerMutations(ServerPlayer player) {
        if (player == null || !player.isAlive()) {
            return;
        }
        if (hasMutation(player, MUTATION_WITHER)) {
            if (player.hasEffect(MobEffects.WITHER)) {
                player.removeEffect(MobEffects.WITHER);
            }
        }
        if (hasMutation(player, MUTATION_ELDER_GUARDIAN)) {
            if (player.isInWater() || player.isUnderWater()) {
                player.setAirSupply(player.getMaxAirSupply());
                if (player.tickCount % 20 == 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 40, 0, true, false, true));
                }
            }
        }
    }

    public static boolean isPhaseEnabled(Player player) {
        return isPhaseEnabled((Entity) player);
    }

    public static boolean isPhaseEnabled(Entity entity) {
        return entity.level().isClientSide() ? clientPhaseEnabled : SlimeFormConfig.get().doPhaseEnabled;
    }

    public static void setClientPhaseEnabled(boolean enabled) {
        clientPhaseEnabled = enabled;
    }

    public static boolean isActive(Player player) {
        return player.entityTags().contains(SlimeFormMod.SLIME_FORM_TAG);
    }

    public static int getMaxSize() {
        return SlimeFormConfig.get().effectiveMaxSlimeSize();
    }

    public static int getSize(Player player) {
        return taggedSize(player);
    }

    /** Returns the configured morph override, or the player's normal slime size. */
    public static int getMorphSize(Player player) {
        return player.entityTags().stream()
                .filter(tag -> tag.startsWith(MORPH_SIZE_PREFIX))
                .map(tag -> tag.substring(MORPH_SIZE_PREFIX.length()))
                .mapToInt(SlimeFormState::parseSizeTag)
                .filter(parsedSize -> parsedSize >= MIN_SIZE)
                .max()
                .orElse(getSize(player));
    }

    public static void setMorphSize(Player player, int size) {
        if (size < MIN_SIZE) {
            throw new IllegalArgumentException("Morph size must be positive");
        }
        player.entityTags().removeIf(tag -> tag.startsWith(MORPH_SIZE_PREFIX));
        player.addTag(MORPH_SIZE_PREFIX + size);
    }

    public static void resetMorphSize(Player player) {
        player.entityTags().removeIf(tag -> tag.startsWith(MORPH_SIZE_PREFIX));
    }

    /**
     * Resolves the slime size for rider positioning, including the client-side
     * max-health fallback when synchronized size tags are unavailable.
     */
    public static int getRiderSize(Player player) {
        int taggedSize = taggedSize(player);
        if (taggedSize != getMaxSize() || player.entityTags().contains(SIZE_PREFIX + taggedSize)) {
            return taggedSize;
        }

        if (player.level().isClientSide()) {
            float maxHealth = player.getMaxHealth();
            int size = (int) Math.round(Math.sqrt(maxHealth));
            if (size >= MIN_SIZE && maxHealth == maxHealthForSize(size)) {
                return Math.min(size, getMaxSize());
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
        player.entityTags().removeIf(tag -> tag.startsWith(SIZE_PREFIX));
        player.addTag(SIZE_PREFIX + Math.max(MIN_SIZE, Math.min(getMaxSize(), size)));
        if (player instanceof ServerPlayer serverPlayer && SlimeMorphManager.isMorphed(serverPlayer)) {
            refreshMorphDimensions(serverPlayer);
        }
    }

    public static void refreshMorphDimensions(Player player) {
        player.refreshDimensions();
    }

    public static void deactivate(Player player) {
        player.removeTag(SlimeFormMod.SLIME_FORM_TAG);
        player.entityTags().removeIf(tag -> tag.startsWith(SIZE_PREFIX));
        removeHealthModifier(player);
    }

    public static float maxHealthForSize(int size) {
        return (float) ((double) size * size);
    }

    private static int taggedSize(Player player) {
        int size = player.entityTags().stream()
                .filter(tag -> tag.startsWith(SIZE_PREFIX))
                .map(tag -> tag.substring(SIZE_PREFIX.length()))
                .mapToInt(SlimeFormState::parseSizeTag)
                .filter(parsedSize -> parsedSize >= MIN_SIZE)
                .max()
                .orElse(getMaxSize());
        return Math.min(size, getMaxSize());
    }

    private static int parseSizeTag(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
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
