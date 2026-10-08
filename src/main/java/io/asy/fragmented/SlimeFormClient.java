package io.asy.fragmented;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.lwjgl.glfw.GLFW;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class SlimeFormClient implements ClientModInitializer {
    private static final int SLIME_HIGHLIGHT_COLOR = 0x55FF55;
    private static final int HOSTILE_HIGHLIGHT_COLOR = 0xFF5555;
    private static final int DEFAULT_HIGHLIGHT_COLOR = 0xFFFFFF;
    private static final KeyMapping.Category CONTROLS_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(SlimeFormMod.MOD_ID, "controls"));
    private static final KeyMapping HIGHLIGHT_SLIMES_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.slimeform.highlight_nearby",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_G,
                    CONTROLS_CATEGORY));
    public static final KeyMapping MORPH_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.slimeform.morph",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_H,
                    CONTROLS_CATEGORY));
    private static SlimeFormClient instance;
    private static volatile boolean slimeChunksEnabled;
    private static volatile List<SlimeFormPayloads.AuraSource> slimeChunkSources = List.of();
    private static final Map<Integer, SlimeAppearanceSettings> playerAppearanceSettings = new HashMap<>();
    private final Map<Integer, Boolean> highlightedEntities = new HashMap<>();
    private boolean wakeSent;
    private boolean recoveryCycleAttackWasDown;
    private boolean recoveryCycleUseWasDown;
    private int morphPhase;
    private int morphRemaining;
    private int morphTotal;
    private int morphSize;
    private boolean dormantDebugVisible;
    private int dormantDebugRemainingTicks;
    private long dormantDebugLastUpdateTick;

    @Override
    public void onInitializeClient() {
        instance = this;
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientPlayNetworking.registerGlobalReceiver(
                SlimeFormPayloads.PHASE_STATE_TYPE,
                (payload, context) -> context.client().execute(
                        () -> SlimeFormState.setClientPhaseEnabled(payload.enabled())));
        ClientPlayNetworking.registerGlobalReceiver(
                SlimeFormPayloads.SLIME_CHUNKS_STATE_TYPE,
                (payload, context) -> context.client().execute(() -> {
                    slimeChunksEnabled = payload.enabled();
                    slimeChunkSources = List.copyOf(payload.sources());
                }));
        ClientPlayNetworking.registerGlobalReceiver(
                SlimeFormPayloads.DORMANT_DEBUG_TYPE,
                (payload, context) -> context.client().execute(() -> {
                    dormantDebugVisible = payload.visible();
                    dormantDebugRemainingTicks = payload.remainingTicks();
                    dormantDebugLastUpdateTick = context.client().level == null
                            ? 0L : context.client().level.getGameTime();
                }));
        ClientPlayNetworking.registerGlobalReceiver(
                SlimeFormPayloads.MORPH_STATE_TYPE,
                (payload, context) -> context.client().execute(() -> {
                    morphPhase = payload.phase();
                    morphRemaining = payload.remaining();
                    morphTotal = payload.total();
                    morphSize = Math.max(SlimeFormState.MIN_SIZE, payload.size());
                }));
        ClientPlayNetworking.registerGlobalReceiver(
                SlimeFormPayloads.PLAYER_APPEARANCE_STATE_TYPE,
                (payload, context) -> context.client().execute(() -> {
                    if (payload.present()) {
                        playerAppearanceSettings.put(payload.entityId(), new SlimeAppearanceSettings(
                                payload.transparencyPercent(), payload.slimeTint(), payload.slimeShell()));
                    } else {
                        playerAppearanceSettings.remove(payload.entityId());
                    }
                }));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(SlimeFormClient::syncPlayerAppearance));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            playerAppearanceSettings.clear();
            slimeChunksEnabled = false;
            slimeChunkSources = List.of();
        });
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.BOSS_BAR,
                Identifier.fromNamespaceAndPath(SlimeFormMod.MOD_ID, "morph_hud"),
                this::renderMorphHud);
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.BOSS_BAR,
                Identifier.fromNamespaceAndPath(SlimeFormMod.MOD_ID, "dormant_hud"),
                this::renderDormantHud);
    }

    /**
     * While spectating, left click asks the server for the next recovery fragment and right
     * click for the previous one. The server ignores this unless the player is in a recovery.
     * Only the moment a button goes down counts, so holding a button cycles once.
     */
    private void sendRecoveryCycleInput(Minecraft client) {
        // No screen check needed: while any screen is open Minecraft releases all key
        // mappings, so isDown() below is already false.
        boolean spectating = client.player.isSpectator();
        boolean attackDown = spectating && client.options.keyAttack.isDown();
        boolean useDown = spectating && client.options.keyUse.isDown();
        if (ClientPlayNetworking.canSend(SlimeFormPayloads.RECOVERY_CYCLE_TYPE)) {
            if (attackDown && !recoveryCycleAttackWasDown) {
                ClientPlayNetworking.send(new SlimeFormPayloads.RecoveryCyclePayload(true));
            }
            if (useDown && !recoveryCycleUseWasDown) {
                ClientPlayNetworking.send(new SlimeFormPayloads.RecoveryCyclePayload(false));
            }
        }
        recoveryCycleAttackWasDown = attackDown;
        recoveryCycleUseWasDown = useDown;
    }

    private void tick(Minecraft client) {
        if (client.player == null) {
            clearHighlightedEntities(client);
            wakeSent = false;
            recoveryCycleAttackWasDown = false;
            recoveryCycleUseWasDown = false;
            morphPhase = 0;
            morphRemaining = 0;
            morphTotal = 0;
            morphSize = 0;
            dormantDebugVisible = false;
            dormantDebugRemainingTicks = 0;
            dormantDebugLastUpdateTick = 0L;
            SlimeFormState.setClientPhaseEnabled(false);
            slimeChunksEnabled = false;
            slimeChunkSources = List.of();
            return;
        }

        updateHighlightedEntities(client);
        sendRecoveryCycleInput(client);

        SlimeFormConfig config = SlimeFormConfig.get();
        boolean active = SlimeFormState.isClientVisualSlimeForm(client.player);
        if (config.slimeMorphEnabled && active
                && ClientPlayNetworking.canSend(SlimeFormPayloads.MORPH_TOGGLE_TYPE)) {
            while (MORPH_KEY.consumeClick()) {
                ClientPlayNetworking.send(new SlimeFormPayloads.MorphTogglePayload());
            }
        }

        if (config.slimeMorphEnabled && isLocalMorphBodyActive()
                && ClientPlayNetworking.canSend(SlimeFormPayloads.MORPH_INPUT_TYPE)) {
            ClientPlayNetworking.send(new SlimeFormPayloads.MorphInputPayload(
                    client.options.keyJump.isDown(),
                    client.options.keyUp.isDown(),
                    client.options.keyDown.isDown(),
                    client.options.keyLeft.isDown(),
                    client.options.keyRight.isDown(),
                    client.player.getYRot(),
                    client.player.getXRot()));
        }

        if (!SlimeFormMod.isDormant(client.player)) {
            wakeSent = false;
            return;
        }

        boolean inputDown = false;
        for (var keyMapping : client.options.keyMappings) {
            if (keyMapping.isDown()) {
                inputDown = true;
                break;
            }
        }
        if (inputDown && !wakeSent && ClientPlayNetworking.canSend(SlimeFormPayloads.WAKE_DORMANT_TYPE)) {
            ClientPlayNetworking.send(new SlimeFormPayloads.WakeDormantPayload());
            wakeSent = true;
        } else if (!inputDown) {
            wakeSent = false;
        }
    }

    /**
     * The morph state packet is the reliable local signal. Player entity tags
     * are not guaranteed to be available on the local client immediately.
     */
    public static boolean isLocalMorphActive() {
        return instance != null && instance.morphPhase != 0;
    }

    /**
     * The real morph body exists (and is controlled) for every non-idle phase,
     * including ENTERING and RETURNING.
     */
    public static boolean isLocalMorphBodyActive() {
        return instance != null && instance.morphPhase != 0;
    }

    /**
     * Returns the server-confirmed local morph state, with synchronized entity tags
     * retained as a fallback for other player render states.
     */
    public static boolean isClientMorphVisible(Player player) {
        Minecraft client = Minecraft.getInstance();
        return (player == client.player && instance != null && instance.morphPhase != 0)
                || SlimeMorphManager.isMorphedClient(player);
    }

    /**
     * Returns the packet-synchronized local size when available, otherwise the
     * size derived from the player entity.
     */
    public static int getClientMorphSize(Player player) {
        Minecraft client = Minecraft.getInstance();
        if (player == client.player && instance != null && instance.morphPhase != 0
                && instance.morphSize >= SlimeFormState.MIN_SIZE) {
            return instance.morphSize;
        }
        return SlimeFormState.getRiderSize(player);
    }

    public static SlimeAppearanceSettings getPlayerAppearanceSettings(Player player) {
        Minecraft client = Minecraft.getInstance();
        if (player == client.player) {
            return SlimeAppearanceSettings.fromConfig(SlimeFormConfig.get());
        }
        return playerAppearanceSettings.getOrDefault(player.getId(), SlimeAppearanceSettings.DEFAULT);
    }

    public static void syncPlayerAppearance() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        SlimeAppearanceSettings settings = SlimeAppearanceSettings.fromConfig(SlimeFormConfig.get());
        playerAppearanceSettings.put(client.player.getId(), settings);
        if (ClientPlayNetworking.canSend(SlimeFormPayloads.PLAYER_APPEARANCE_PREFERENCE_TYPE)) {
            ClientPlayNetworking.send(new SlimeFormPayloads.PlayerAppearancePreferencePayload(
                    settings.transparencyPercent(), settings.slimeTint(), settings.slimeShell()));
        }
    }

    public static boolean isClientSlimeChunk(int chunkX, int chunkZ) {
        if (!slimeChunksEnabled) {
            return false;
        }
        for (SlimeFormPayloads.AuraSource source : slimeChunkSources) {
            if (Math.abs(chunkX - source.chunkX()) <= 8
                    && Math.abs(chunkZ - source.chunkZ()) <= 8) {
                return true;
            }
        }
        return false;
    }

    public static int highlightColor(Entity entity) {
        if (instance == null) {
            return -1;
        }
        int entityId;
        try {
            entityId = entity.getId();
        } catch (IllegalStateException ignored) {
            return -1;
        }
        if (!instance.highlightedEntities.containsKey(entityId)) {
            return -1;
        }
        if (entity instanceof Slime) {
            return SLIME_HIGHLIGHT_COLOR;
        }
        if (entity instanceof Enemy) {
            return HOSTILE_HIGHLIGHT_COLOR;
        }
        if (entity instanceof Player || entity instanceof LivingEntity) {
            return DEFAULT_HIGHLIGHT_COLOR;
        }
        return DEFAULT_HIGHLIGHT_COLOR;
    }

    private void updateHighlightedEntities(Minecraft client) {
        if (!slimeChunksEnabled || !HIGHLIGHT_SLIMES_KEY.isDown()) {
            clearHighlightedEntities(client);
            return;
        }

        Set<Integer> current = new HashSet<>();
        for (SlimeFormPayloads.AuraSource source : slimeChunkSources) {
            double minX = (source.chunkX() - 8) * 16.0D;
            double minZ = (source.chunkZ() - 8) * 16.0D;
            AABB area = new AABB(
                    minX, client.level.getMinY(), minZ,
                    minX + 17 * 16.0D, client.level.getMaxY(), minZ + 17 * 16.0D);
            for (Entity entity : client.level.getEntities(
                    (Entity) null, area, candidate -> candidate instanceof LivingEntity)) {
                current.add(entity.getId());
                if (!highlightedEntities.containsKey(entity.getId())) {
                    highlightedEntities.put(entity.getId(), true);
                }
            }
        }

        Iterator<Map.Entry<Integer, Boolean>> iterator = highlightedEntities.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Boolean> entry = iterator.next();
            if (current.contains(entry.getKey())) {
                continue;
            }
            Entity entity = client.level.getEntity(entry.getKey());
            iterator.remove();
        }
    }

    private void clearHighlightedEntities(Minecraft client) {
        highlightedEntities.clear();
    }

    private void renderMorphHud(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker tickCounter) {
        if (morphTotal <= 0 || (morphPhase != 1 && morphPhase != 3)) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        int width = 180;
        int height = 8;
        int x = (client.getWindow().getGuiScaledWidth() - width) / 2;
        int y = client.getWindow().getGuiScaledHeight() - 45;
        int filled = Math.max(0, Math.min(width, (int) ((long) (morphTotal - morphRemaining) * width / morphTotal)));
        graphics.fill(x, y, x + width, y + height, 0xAA202020);
        graphics.fill(x, y, x + filled, y + height, morphPhase == 1 ? 0xFF55D66F : 0xFF55B7D6);
        String label = morphPhase == 1 ? "Morphing into slime" : "Returning to player";
        graphics.centeredText(client.font,
                Component.literal(label + " (" + ((morphRemaining + 19) / 20) + "s)"),
                client.getWindow().getGuiScaledWidth() / 2, y - 12, 0xFFFFFFFF);
    }

    private void renderDormantHud(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (!dormantDebugVisible || dormantDebugRemainingTicks <= 0 || client.player == null) {
            return;
        }
        int x = 8;
        int y = client.getWindow().getGuiScaledHeight() - 42;
        int width = 180;
        int height = 8;
        float elapsedTicks = client.player.level().getGameTime() - dormantDebugLastUpdateTick
                + tickCounter.getGameTimeDeltaPartialTick(false);
        float remainingTicks = Math.max(0.0F, dormantDebugRemainingTicks - elapsedTicks);
        int filled = Math.max(0, Math.min(width,
                (int) ((200.0F - remainingTicks) * width / 200.0F)));
        graphics.fill(x, y, x + width, y + height, 0xAA202020);
        graphics.fill(x, y, x + filled, y + height, 0xFFE5B84B);
        graphics.text(client.font, Component.literal("Entering AFK Mode"), x, y - 12, 0xFFFFFFFF, false);
    }
}
