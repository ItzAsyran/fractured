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
import io.asy.fragmented.gui.SlimeCoreScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
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
                    InputConstants.KEY_G,
                    CONTROLS_CATEGORY));
    public static final KeyMapping MORPH_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.slimeform.morph",
                    InputConstants.KEY_H,
                    CONTROLS_CATEGORY));
    public static final KeyMapping OPEN_CORE_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.slimeform.open_core",
                    InputConstants.Type.KEYBOARD,
                    InputConstants.KEY_K,
                    CONTROLS_CATEGORY));
    public static final KeyMapping ABSORB_CYCLE_PREV_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.slimeform.absorb_cycle_prev",
                    InputConstants.KEY_LBRACKET,
                    CONTROLS_CATEGORY));
    public static final KeyMapping ABSORB_CYCLE_NEXT_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.slimeform.absorb_cycle_next",
                    InputConstants.KEY_RBRACKET,
                    CONTROLS_CATEGORY));
    public static final KeyMapping ABSORB_CONFIRM_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.slimeform.absorb_confirm",
                    InputConstants.KEY_V,
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
    private static float trappedStruggleProgress = -1.0F;
    private static net.minecraft.client.CameraType previousTrappedCameraType;
    private static int bossQteBossId = -1;
    private static String bossQteHazardName = "";
    private static int bossQteDurationTicks = 0;
    private static int bossQteRemainingTicks = 0;
    private static boolean bossQteActive = false;

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
                SlimeFormPayloads.OPEN_CORE_SCREEN_TYPE,
                (payload, context) -> context.client().execute(
                        () -> context.client().setScreenAndShow(new SlimeCoreScreen(null))));
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
        ClientPlayNetworking.registerGlobalReceiver(
                SlimeFormPayloads.ABSORB_SYNC_TYPE,
                (payload, context) -> context.client().execute(() -> {
                    Minecraft mc = context.client();
                    boolean wasTrapped = mc.player != null && SlimeAbsorbClientState.isTrapped(mc.player.getId());
                    SlimeAbsorbClientState.update(
                            payload.holderEntityId(), payload.ownerEntityId(), payload.trappedEntityIds(), payload.digestingEntityIds());
                    boolean isTrapped = mc.player != null && SlimeAbsorbClientState.isTrapped(mc.player.getId());
                    if (!wasTrapped && isTrapped) {
                        if (mc.options.getCameraType() == net.minecraft.client.CameraType.FIRST_PERSON) {
                            previousTrappedCameraType = mc.options.getCameraType();
                            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                        }
                    } else if (wasTrapped && !isTrapped) {
                        if (previousTrappedCameraType != null) {
                            mc.options.setCameraType(previousTrappedCameraType);
                            previousTrappedCameraType = null;
                        }
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(
                SlimeFormPayloads.STRUGGLE_SYNC_TYPE,
                (payload, context) -> context.client().execute(() -> {
                    trappedStruggleProgress = payload.progress();
                    if (trappedStruggleProgress < 0.0F && previousTrappedCameraType != null) {
                        context.client().options.setCameraType(previousTrappedCameraType);
                        previousTrappedCameraType = null;
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(
                SlimeFormPayloads.BOSS_QTE_SYNC_TYPE,
                (payload, context) -> context.client().execute(() -> {
                    bossQteBossId = payload.bossEntityId();
                    bossQteHazardName = payload.hazardName();
                    bossQteDurationTicks = payload.qteDurationTicks();
                    bossQteRemainingTicks = payload.qteDurationTicks();
                    bossQteActive = payload.active();
                }));
        ClientPlayNetworking.registerGlobalReceiver(
                SlimeFormPayloads.MUTATION_SYNC_TYPE,
                (payload, context) -> context.client().execute(() -> {
                    SlimeFormState.setClientMutations(payload.mutations());
                }));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(SlimeFormClient::syncPlayerAppearance));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            playerAppearanceSettings.clear();
            slimeChunksEnabled = false;
            slimeChunkSources = List.of();
            SlimeAbsorbClientState.clear();
            trappedStruggleProgress = -1.0F;
            previousTrappedCameraType = null;
            bossQteActive = false;
            bossQteRemainingTicks = 0;
        });
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.BOSS_BAR,
                Identifier.fromNamespaceAndPath(SlimeFormMod.MOD_ID, "morph_hud"),
                this::renderMorphHud);
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.BOSS_BAR,
                Identifier.fromNamespaceAndPath(SlimeFormMod.MOD_ID, "dormant_hud"),
                this::renderDormantHud);
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.BOSS_BAR,
                Identifier.fromNamespaceAndPath(SlimeFormMod.MOD_ID, "absorb_hud"),
                this::renderAbsorbHud);
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.BOSS_BAR,
                Identifier.fromNamespaceAndPath(SlimeFormMod.MOD_ID, "struggle_hud"),
                this::renderStruggleHud);
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.BOSS_BAR,
                Identifier.fromNamespaceAndPath(SlimeFormMod.MOD_ID, "boss_qte_hud"),
                this::renderBossQteHud);
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

        while (OPEN_CORE_KEY.consumeClick()) {
            client.setScreenAndShow(new SlimeCoreScreen(null));
        }

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

        if (trappedStruggleProgress >= 0.0F) {
            while (client.options.keyJump.consumeClick()) {
                if (ClientPlayNetworking.canSend(SlimeFormPayloads.STRUGGLE_JUMP_TYPE)) {
                    ClientPlayNetworking.send(new SlimeFormPayloads.StruggleJumpPayload());
                }
            }
        }

        if (bossQteActive) {
            if (bossQteRemainingTicks > 0) {
                bossQteRemainingTicks--;
            } else {
                bossQteActive = false;
            }
            while (ABSORB_CONFIRM_KEY.consumeClick()) {
                if (ClientPlayNetworking.canSend(SlimeFormPayloads.ABSORB_ACTION_TYPE)) {
                    ClientPlayNetworking.send(new SlimeFormPayloads.AbsorbActionPayload(
                            SlimeFormPayloads.AbsorbActionPayload.ACTION_BRACE, bossQteBossId));
                }
            }
        }

        if (client.player != null && SlimeAbsorbClientState.isHolding(client.player.getId())) {
            int playerId = client.player.getId();
            boolean shift = InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)
                    || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT)
                    || client.options.keyShift.isDown();

            while (ABSORB_CYCLE_PREV_KEY.consumeClick()) {
                if (shift) {
                    SlimeAbsorbClientState.cycleSelectedEntity(playerId, -1);
                } else {
                    int selected = SlimeAbsorbClientState.getSelectedEntityId(playerId);
                    SlimeAbsorbClientState.cycleMode(selected);
                }
            }
            while (ABSORB_CYCLE_NEXT_KEY.consumeClick()) {
                if (shift) {
                    SlimeAbsorbClientState.cycleSelectedEntity(playerId, 1);
                } else {
                    int selected = SlimeAbsorbClientState.getSelectedEntityId(playerId);
                    SlimeAbsorbClientState.cycleMode(selected);
                }
            }
            while (ABSORB_CONFIRM_KEY.consumeClick()) {
                int targetId = SlimeAbsorbClientState.getSelectedEntityId(playerId);
                int action = (SlimeAbsorbClientState.getMode() == SlimeAbsorbClientState.AbsorbMode.DISSOLVE
                        && SlimeAbsorbClientState.canDissolve(targetId))
                        ? SlimeFormPayloads.AbsorbActionPayload.ACTION_DISSOLVE
                        : SlimeFormPayloads.AbsorbActionPayload.ACTION_EJECT;
                if (ClientPlayNetworking.canSend(SlimeFormPayloads.ABSORB_ACTION_TYPE)) {
                    ClientPlayNetworking.send(new SlimeFormPayloads.AbsorbActionPayload(action, targetId));
                }
            }
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
        if (player == client.player || player instanceof io.asy.fragmented.gui.SlimePreviewPlayer) {
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && SlimeFormState.hasMutation(mc.player, SlimeFormState.MUTATION_WARDEN)
                && (!slimeChunksEnabled || !HIGHLIGHT_SLIMES_KEY.isDown())) {
            return 0x00E5D3;
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
        boolean sculkTremorsense = client.player != null && SlimeFormState.hasMutation(client.player, SlimeFormState.MUTATION_WARDEN);
        boolean keyAuraActive = slimeChunksEnabled && HIGHLIGHT_SLIMES_KEY.isDown();

        if (!keyAuraActive && !sculkTremorsense) {
            clearHighlightedEntities(client);
            return;
        }

        Set<Integer> current = new HashSet<>();

        if (sculkTremorsense && client.player != null && client.level != null) {
            double range = isLocalMorphBodyActive() ? 24.0D : 16.0D;
            AABB senseArea = client.player.getBoundingBox().inflate(range);
            for (Entity entity : client.level.getEntities(
                    client.player, senseArea, e -> e instanceof LivingEntity living && living.isAlive())) {
                if (entity.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D || (entity instanceof Player p && p.isSprinting())) {
                    current.add(entity.getId());
                    if (!highlightedEntities.containsKey(entity.getId())) {
                        highlightedEntities.put(entity.getId(), true);
                    }
                }
            }
        }

        if (keyAuraActive && client.level != null) {
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

    private void renderAbsorbHud(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        int playerId = client.player.getId();
        if (!SlimeAbsorbClientState.isHolding(playerId)) {
            return;
        }

        List<Integer> trappedIds = SlimeAbsorbClientState.getTrappedIds(playerId);
        if (trappedIds.isEmpty()) {
            return;
        }

        int selectedId = SlimeAbsorbClientState.getSelectedEntityId(playerId);
        Entity selectedEntity = client.level != null ? client.level.getEntity(selectedId) : null;
        Component entityName = selectedEntity != null
                ? selectedEntity.getDisplayName()
                : Component.literal("Unknown");

        // Capacity calculation
        int size = getClientMorphSize(client.player);
        double maxCapacity = SlimeAbsorbHelper.getSlimeCapacity(size, SlimeFormConfig.get().effectiveAbsorbCapacityFactor());
        double heldVolume = 0.0D;
        if (client.level != null) {
            for (int id : trappedIds) {
                Entity e = client.level.getEntity(id);
                if (e != null) {
                    heldVolume += SlimeAbsorbHelper.getEntityVolume(e);
                }
            }
        }

        int width = 260;
        int height = 28;
        int x = (client.getWindow().getGuiScaledWidth() - width) / 2;

        int bottomMargin = 26;
        if (client.gameMode != null && client.gameMode.canHurtPlayer()) {
            float totalHealth = client.player.getMaxHealth() + client.player.getAbsorptionAmount();
            int healthRows = Math.max(1, (int) Math.ceil(totalHealth / 20.0f));
            int armorRows = client.player.getArmorValue() > 0 ? 1 : 0;
            int airRows = (client.player.getAirSupply() < client.player.getMaxAirSupply()) ? 1 : 0;
            int extraRows = Math.max(healthRows + armorRows, 1 + airRows);
            bottomMargin = 24 + (extraRows * 10);
        }
        int y = Math.max(10, client.getWindow().getGuiScaledHeight() - bottomMargin - height - 4);

        // Bedrock styled dark container
        graphics.fill(x, y, x + width, y + height, 0xCC181818);
        graphics.fill(x, y, x + width, y + 1, 0x44FFFFFF);
        graphics.fill(x, y + height - 1, x + width, y + height, 0x33000000);

        // Mode selector
        SlimeAbsorbClientState.AbsorbMode mode = SlimeAbsorbClientState.getMode();
        boolean isEject = mode == SlimeAbsorbClientState.AbsorbMode.EJECT;
        boolean isDigesting = SlimeAbsorbClientState.isDigesting(selectedId);
        boolean canDissolve = SlimeAbsorbClientState.canDissolve(selectedId);

        String ejectText = isEject ? "[ EJECT ]" : "  EJECT  ";
        int ejectColor = isEject ? 0xFF55FF55 : 0xFF888888;
        graphics.text(client.font, Component.literal(ejectText), x + 8, y + 4, ejectColor, false);

        int sepX = x + 8 + client.font.width(ejectText) + 3;
        graphics.text(client.font, Component.literal("|"), sepX, y + 4, 0xFF555555, false);

        String rightModeText;
        int rightModeColor;
        if (isDigesting || !canDissolve) {
            rightModeText = "[ DIGESTING ]";
            rightModeColor = 0xFFFF5555;
        } else {
            rightModeText = !isEject ? "[ DISSOLVE ]" : "  DISSOLVE  ";
            rightModeColor = !isEject ? 0xFFFF5555 : 0xFF888888;
        }
        int dissX = sepX + 5;
        graphics.text(client.font, Component.literal(rightModeText), dissX, y + 4, rightModeColor, false);

        // Selected entity name, counter & remaining health
        int entityStartX = dissX + client.font.width(rightModeText) + 6;
        int selectedIndex = trappedIds.indexOf(selectedId) + 1;
        String countStr = "(" + (selectedIndex > 0 ? selectedIndex : 1) + "/" + trappedIds.size() + ")";

        String hpStr = "";
        if (selectedEntity instanceof LivingEntity living) {
            float hp = living.getHealth();
            float maxHp = living.getMaxHealth();
            hpStr = String.format(java.util.Locale.ROOT, "%.1f/%.1f HP", Math.max(0.0f, hp), maxHp);
        }

        String rightLabel = hpStr.isEmpty() ? countStr : countStr + " " + hpStr;
        int rightLabelWidth = client.font.width(rightLabel);
        String nameStr = entityName.getString();
        int maxNameWidth = (x + width - 6) - entityStartX - rightLabelWidth - 4;
        if (client.font.width(nameStr) > maxNameWidth && maxNameWidth > 12) {
            nameStr = client.font.plainSubstrByWidth(nameStr, maxNameWidth - 8) + "..";
        }
        graphics.text(client.font, Component.literal(nameStr + " " + rightLabel), entityStartX, y + 4, isDigesting ? 0xFFFFAA88 : 0xFFFFFFFF, false);

        // Capacity progress bar
        int barX = x + 8;
        int barY = y + 16;
        int barWidth = width - 16;
        int barHeight = 3;
        float progress = maxCapacity > 0.0D ? (float) Math.min(1.0D, heldVolume / maxCapacity) : 0.0F;
        int filled = (int) (progress * barWidth);

        int barColor = progress >= 0.95F ? 0xFFFF4444 : progress >= 0.75F ? 0xFFFFBB33 : 0xFF55D66F;
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);
        if (filled > 0) {
            graphics.fill(barX, barY, barX + filled, barY + barHeight, barColor);
        }

        // Capacity and Key hints text
        String capStr = String.format(java.util.Locale.ROOT, "%.2f/%.2f", heldVolume, maxCapacity);
        graphics.text(client.font, Component.literal(capStr), barX, y + 20, 0xFFAAAAAA, false);

        String hintStr = "V: Action  [: Mode  Shift+[: Target";
        int hintWidth = client.font.width(hintStr);
        graphics.text(client.font, Component.literal(hintStr), x + width - hintWidth - 8, y + 20, 0xFF777777, false);
    }

    private void renderStruggleHud(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !client.player.isAlive() || trappedStruggleProgress < 0.0F) {
            return;
        }

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        // Subtle edge vignette indicating containment without obstructing the 3D slime body
        int borderSize = Math.min(48, screenHeight / 6);
        graphics.fillGradient(0, 0, screenWidth, borderSize, 0x55186020, 0x00186020);
        graphics.fillGradient(0, screenHeight - borderSize, screenWidth, screenHeight, 0x00186020, 0x55186020);
        graphics.fillGradient(0, 0, borderSize, screenHeight, 0x55186020, 0x00186020);
        graphics.fillGradient(screenWidth - borderSize, 0, screenWidth, screenHeight, 0x00186020, 0x55186020);

        int width = 180;
        int height = 24;
        int x = (screenWidth - width) / 2;
        int y = screenHeight - 78;

        // Bedrock styled dark container
        graphics.fill(x, y, x + width, y + height, 0xCC181818);
        graphics.fill(x, y, x + width, y + 1, 0x44FFFFFF);
        graphics.fill(x, y + height - 1, x + width, y + height, 0x33000000);

        // Header text: Title on left, Health readout on right
        int pct = Math.max(0, Math.min(100, (int) trappedStruggleProgress));
        String title = String.format(java.util.Locale.ROOT, "BREAKING FREE: %d%%", pct);
        graphics.text(client.font, Component.literal(title), x + 8, y + 3, 0xFFFFFFFF, false);

        int currentHp = (int) Math.ceil(client.player.getHealth());
        int maxHp = (int) client.player.getMaxHealth();
        String hpText = String.format(java.util.Locale.ROOT, "❤ %d/%d", currentHp, maxHp);
        int hpWidth = client.font.width(hpText);
        graphics.text(client.font, Component.literal(hpText), x + width - hpWidth - 8, y + 3, 0xFFFF5555, false);

        // Progress bar
        int barX = x + 8;
        int barY = y + 14;
        int barWidth = width - 16;
        int barHeight = 4;
        float progress = Math.max(0.0F, Math.min(1.0F, trappedStruggleProgress / 100.0F));
        int filled = (int) (progress * barWidth);

        int barColor = progress >= 0.70F ? 0xFF55FF55 : progress >= 0.35F ? 0xFFFFAA00 : 0xFFFF4444;
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);
        if (filled > 0) {
            graphics.fill(barX, barY, barX + filled, barY + barHeight, barColor);
        }

        // Subtext hint positioned above the container to avoid overlapping vanilla hotbar & hearts
        String hint = "[SPACE] Tap Jump to Struggle!";
        int hintWidth = client.font.width(hint);
        graphics.text(client.font, Component.literal(hint), x + (width - hintWidth) / 2, y - 11, 0xFFEEEEEE, true);
    }

    private void renderBossQteHud(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !client.player.isAlive() || !bossQteActive || bossQteRemainingTicks <= 0) {
            return;
        }

        int width = 180;
        int height = 28;
        int x = (client.getWindow().getGuiScaledWidth() - width) / 2;
        int y = 45;

        boolean flash = (client.player.tickCount / 4) % 2 == 0;
        int bgColor = flash ? 0xDD661111 : 0xDD331111;
        graphics.fill(x, y, x + width, y + height, bgColor);
        graphics.fill(x, y, x + width, y + 2, 0xFFFF5555);
        graphics.fill(x, y + height - 2, x + width, y + height, 0xFFFF5555);

        String alertText = "! BRACE ! Press [V]";
        int textWidth = client.font.width(alertText);
        graphics.text(client.font, Component.literal(alertText), x + (width - textWidth) / 2, y + 4, 0xFFFFAA00, true);

        int barX = x + 8;
        int barY = y + 17;
        int barWidth = width - 16;
        int barHeight = 4;
        float progress = bossQteDurationTicks > 0 ? (float) bossQteRemainingTicks / bossQteDurationTicks : 0.0F;
        int filled = (int) (progress * barWidth);

        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF222222);
        if (filled > 0) {
            graphics.fill(barX, barY, barX + filled, barY + barHeight, 0xFFFF3333);
        }
    }
}
