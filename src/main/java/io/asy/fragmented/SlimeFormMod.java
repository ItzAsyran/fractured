package io.asy.fragmented;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SlimeFormMod implements ModInitializer {
    public static final String MOD_ID = "slimeform";
    public static final String SLIME_FORM_TAG = "slimeform.active";
    public static final String MORPH_TAG = "slimeform.morphed";
    public static final String SLIME_DORMANT_TAG = "slimeform.dormant";
    public static final String PLAYER_RECOVERY_SLIME_TAG = "slimeform.player_recovery";
    public static final String PLAYER_DORMANT_SLIME_TAG = "slimeform.player_dormant";
    public static final int RECOVERY_COUNTDOWN_INTERVAL_TICKS = 20;
    private static final int DORMANT_HUD_LEAD_SECONDS = 10;
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static SlimeFormPayloads.SlimeChunksStatePayload lastSlimeChunksState;

    @Override
    public void onInitialize() {
        SlimeFormConfig.initialize();
        PayloadTypeRegistry.serverboundPlay().register(
                SlimeFormPayloads.WAKE_DORMANT_TYPE,
                SlimeFormPayloads.WAKE_DORMANT_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                SlimeFormPayloads.PHASE_STATE_TYPE,
                SlimeFormPayloads.PHASE_STATE_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                SlimeFormPayloads.SLIME_CHUNKS_STATE_TYPE,
                SlimeFormPayloads.SLIME_CHUNKS_STATE_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                SlimeFormPayloads.DORMANT_DEBUG_TYPE,
                SlimeFormPayloads.DORMANT_DEBUG_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                SlimeFormPayloads.MORPH_TOGGLE_TYPE,
                SlimeFormPayloads.MORPH_TOGGLE_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                SlimeFormPayloads.MORPH_INPUT_TYPE,
                SlimeFormPayloads.MORPH_INPUT_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                SlimeFormPayloads.MORPH_STATE_TYPE,
                SlimeFormPayloads.MORPH_STATE_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                SlimeFormPayloads.PLAYER_APPEARANCE_PREFERENCE_TYPE,
                SlimeFormPayloads.PLAYER_APPEARANCE_PREFERENCE_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                SlimeFormPayloads.PLAYER_APPEARANCE_STATE_TYPE,
                SlimeFormPayloads.PLAYER_APPEARANCE_STATE_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                SlimeFormPayloads.RECOVERY_CYCLE_TYPE,
                SlimeFormPayloads.RECOVERY_CYCLE_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                SlimeFormPayloads.CORE_ACTION_TYPE,
                SlimeFormPayloads.CORE_ACTION_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                SlimeFormPayloads.OPEN_CORE_SCREEN_TYPE,
                SlimeFormPayloads.OPEN_CORE_SCREEN_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(
                SlimeFormPayloads.WAKE_DORMANT_TYPE,
                (payload, context) -> wakeDormant(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(
                SlimeFormPayloads.RECOVERY_CYCLE_TYPE,
                (payload, context) -> context.server().execute(
                        () -> SlimeRecoveryManager.cycleCamera(context.player(), payload.next())));
        ServerPlayNetworking.registerGlobalReceiver(
                SlimeFormPayloads.MORPH_TOGGLE_TYPE,
                (payload, context) -> context.server().execute(
                        () -> SlimeMorphManager.handleToggle(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(
                SlimeFormPayloads.MORPH_INPUT_TYPE,
                (payload, context) -> context.server().execute(
                        () -> SlimeMorphManager.handleInput(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(
                SlimeFormPayloads.PLAYER_APPEARANCE_PREFERENCE_TYPE,
                (payload, context) -> context.server().execute(
                        () -> SlimeAppearanceServerState.update(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(
                SlimeFormPayloads.CORE_ACTION_TYPE,
                (payload, context) -> context.server().execute(
                        () -> handleCoreAction(context.player(), payload)));

        SlimeAppearanceServerState.registerTrackingEvents();
        LOGGER.info("Sliming.");

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                SlimeRiderScale.tick(player);
            }
            SlimeRecoveryManager.tickRecoveries(server);
            tickDormantPlayers(server);
            tickSlimeChunksState(server);
            SlimeMorphManager.tick(server);
            SlimeFormVisuals.processPendingRemovals(server);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            ACTIVITY_TICKS.put(player.getUUID(), player.level().getGameTime());
            ACTIVITY_POSITIONS.put(player.getUUID(), player.position());
            DORMANT_PREVIOUS_INVISIBILITY.remove(player.getUUID());
            SlimeRecoveryManager.handlePlayerJoin(player);
            if (SlimeFormConfig.get().autoActivateSlimeForm && !SlimeFormState.isActive(player)) {
                activateSlimeForm(player);
            }
            if (SlimeFormState.isActive(player)) {
                SlimeFormState.applyHealthModifier(player);
            }
            ServerPlayNetworking.send(player,
                    new SlimeFormPayloads.PhaseStatePayload(SlimeFormConfig.get().doPhaseEnabled));
            ServerPlayNetworking.send(player, currentSlimeChunksState(server));
            SlimeAppearanceServerState.sendToTracker(player);
            SlimeAppearanceServerState.sendTrackersTo(player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            SlimeRecoveryManager.handlePlayerDisconnect(player);
            SlimeMorphManager.stop(player);
            SlimeFormVisuals.remove(player, false);
            SlimeFormVisuals.queueDormantRemoval(player);
            ACTIVITY_TICKS.remove(player.getUUID());
            COMBAT_TICKS.remove(player.getUUID());
            ACTIVITY_POSITIONS.remove(player.getUUID());
            DORMANT_PREVIOUS_INVISIBILITY.remove(player.getUUID());
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("slime")
                        .executes(context -> openSlimeCore(context.getSource().getPlayerOrException()))
                        .then(Commands.literal("gui")
                                .executes(context -> openSlimeCore(context.getSource().getPlayerOrException())))
                        .then(Commands.literal("menu")
                                .executes(context -> openSlimeCore(context.getSource().getPlayerOrException())))
                        .then(Commands.literal("on")
                                .executes(context -> activateSlimeForm(context.getSource().getPlayerOrException())))
                        .then(Commands.literal("status")
                                .executes(context -> showSlimeStatus(context.getSource().getPlayerOrException())))
                        .then(Commands.literal("off")
                                .executes(context -> deactivateSlimeForm(context.getSource().getPlayerOrException())))
                        .then(Commands.literal("morph")
                                .then(Commands.literal("size")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                                .executes(context -> setMorphSize(
                                                        context.getSource().getPlayerOrException(),
                                                        IntegerArgumentType.getInteger(context, "value"))))
                                        .then(Commands.literal("reset")
                                                .executes(context -> resetMorphSize(
                                                        context.getSource().getPlayerOrException())))))
                        .then(experimentalCommands())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> experimentalCommands() {
        return Commands.literal("experimental")
                .then(Commands.literal("doPhase")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> setDoPhase(
                                        context.getSource().getPlayerOrException(),
                                        BoolArgumentType.getBool(context, "enabled")))))
                .then(Commands.literal("phaseDebug")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> setPhaseDebug(
                                        context.getSource().getPlayerOrException(),
                                        BoolArgumentType.getBool(context, "enabled")))))
                .then(Commands.literal("slimeChunks")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> setSlimeChunks(
                                        context.getSource().getPlayerOrException(),
                                        BoolArgumentType.getBool(context, "enabled")))));
    }

    private static int openSlimeCore(ServerPlayer player) {
        ServerPlayNetworking.send(player, new SlimeFormPayloads.OpenCoreScreenPayload());
        return Command.SINGLE_SUCCESS;
    }

    public static void handleCoreAction(ServerPlayer player, SlimeFormPayloads.SlimeCoreActionPayload payload) {
        switch (payload.actionId()) {
            case SlimeFormPayloads.SlimeCoreActionPayload.ACTION_TOGGLE_FORM -> {
                if (SlimeFormState.isActive(player)) {
                    deactivateSlimeForm(player);
                } else {
                    activateSlimeForm(player);
                }
            }
            case SlimeFormPayloads.SlimeCoreActionPayload.ACTION_SET_MORPH_SIZE -> setMorphSize(player, payload.intParam());
            case SlimeFormPayloads.SlimeCoreActionPayload.ACTION_RESET_MORPH_SIZE -> resetMorphSize(player);
            case SlimeFormPayloads.SlimeCoreActionPayload.ACTION_TRIGGER_MORPH -> SlimeMorphManager.handleToggle(player);
            case SlimeFormPayloads.SlimeCoreActionPayload.ACTION_SET_DO_PHASE -> setDoPhase(player, payload.intParam() != 0);
            case SlimeFormPayloads.SlimeCoreActionPayload.ACTION_SET_SLIME_CHUNKS -> setSlimeChunks(player, payload.intParam() != 0);
            default -> LOGGER.warn("[slimeform] Unknown core action id: {}", payload.actionId());
        }
    }

    private static int setDoPhase(ServerPlayer player, boolean enabled) {
        SlimeFormConfig config = SlimeFormConfig.get();
        config.doPhaseEnabled = enabled;
        SlimeFormConfig.save();
        SlimeFormPayloads.PhaseStatePayload payload =
                new SlimeFormPayloads.PhaseStatePayload(enabled);
        for (ServerPlayer onlinePlayer : ((ServerLevel) player.level()).getServer().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(onlinePlayer, payload);
        }
        player.sendSystemMessage(Component.literal(String.format(
                java.util.Locale.ROOT,
                "SlimeForm experimental block phasing %s.",
                enabled ? "enabled" : "disabled")).withStyle(ChatFormatting.AQUA));
        return Command.SINGLE_SUCCESS;
    }

    private static int setPhaseDebug(ServerPlayer player, boolean enabled) {
        SlimeFormConfig.get().phaseDebugEnabled = enabled;
        SlimeFormConfig.save();
        if (enabled) {
            SlimeFormPhaseDebug.reset();
        }
        player.sendSystemMessage(Component.literal(String.format(
                java.util.Locale.ROOT,
                "SlimeForm phase diagnostics %s.",
                enabled ? "enabled" : "disabled")));
        return Command.SINGLE_SUCCESS;
    }

    private static int setSlimeChunks(ServerPlayer player, boolean enabled) {
        SlimeFormConfig config = SlimeFormConfig.get();
        config.slimeChunksEnabled = enabled;
        SlimeFormConfig.save();
        SlimeFormPayloads.SlimeChunksStatePayload payload = currentSlimeChunksState(
                ((ServerLevel) player.level()).getServer());
        lastSlimeChunksState = payload;
        for (ServerPlayer onlinePlayer : ((ServerLevel) player.level()).getServer().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(onlinePlayer, payload);
        }
        player.sendSystemMessage(Component.literal(String.format(
                java.util.Locale.ROOT,
                "SlimeForm experimental slime-chunk aura %s.",
                enabled ? "enabled" : "disabled")).withStyle(ChatFormatting.AQUA));
        return Command.SINGLE_SUCCESS;
    }

    private static SlimeFormPayloads.SlimeChunksStatePayload currentSlimeChunksState(MinecraftServer server) {
        List<SlimeFormPayloads.AuraSource> sources = new ArrayList<>();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld != null && SlimeFormConfig.get().slimeChunksEnabled) {
            for (ServerPlayer player : overworld.players()) {
                if (SlimeFormState.isActive(player) && !isDormant(player)) {
                    sources.add(new SlimeFormPayloads.AuraSource(
                            player.blockPosition().getX() >> 4,
                            player.blockPosition().getZ() >> 4));
                }
            }
        }
        return new SlimeFormPayloads.SlimeChunksStatePayload(
                SlimeFormConfig.get().slimeChunksEnabled, sources);
    }

    private static void tickSlimeChunksState(MinecraftServer server) {
        SlimeFormPayloads.SlimeChunksStatePayload state = currentSlimeChunksState(server);
        if (state.equals(lastSlimeChunksState)) {
            return;
        }
        lastSlimeChunksState = state;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, state);
        }
    }

    public static boolean isSlimeChunkAuraActive(LevelAccessor level, BlockPos pos) {
        if (!SlimeFormConfig.get().slimeChunksEnabled
                || !(level instanceof ServerLevel serverLevel)
                || !serverLevel.dimension().equals(Level.OVERWORLD)) {
            return false;
        }

        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            if (player.level() != serverLevel
                    || !SlimeFormState.isActive(player)
                    || isDormant(player)) {
                continue;
            }
            int playerChunkX = player.blockPosition().getX() >> 4;
            int playerChunkZ = player.blockPosition().getZ() >> 4;
            if (Math.abs(chunkX - playerChunkX) <= 8
                    && Math.abs(chunkZ - playerChunkZ) <= 8) {
                return true;
            }
        }
        return false;
    }

    private static int activateSlimeForm(ServerPlayer player) {
        SlimeFormState.activate(player);
        LOGGER.info("[slimeform] Activated slime form for {} ({})", player.getName().getString(), player.getUUID());
        player.sendSystemMessage(Component.literal("Slime form activated.").withStyle(ChatFormatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int deactivateSlimeForm(ServerPlayer player) {
        SlimeMorphManager.stop(player);
        wakeDormant(player);
        SlimeFormState.deactivate(player);
        LOGGER.info("[slimeform] Deactivated slime form for {} ({})", player.getName().getString(), player.getUUID());
        player.sendSystemMessage(Component.literal("Slime form deactivated.").withStyle(ChatFormatting.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private static final Map<UUID, Long> ACTIVITY_TICKS = new HashMap<>();
    private static final Map<UUID, Long> COMBAT_TICKS = new HashMap<>();
    private static final Map<UUID, Vec3> ACTIVITY_POSITIONS = new HashMap<>();
    private static final Map<UUID, Boolean> DORMANT_PREVIOUS_INVISIBILITY = new HashMap<>();

    public static boolean isDormant(Player player) {
        return player.entityTags().contains(SLIME_DORMANT_TAG);
    }

    public static void recordActivity(ServerPlayer player) {
        long now = player.level().getGameTime();
        ACTIVITY_TICKS.put(player.getUUID(), now);
        ACTIVITY_POSITIONS.put(player.getUUID(), player.position());
        if (isDormant(player)) {
            wakeDormant(player);
        }
    }

    public static void recordCombat(ServerPlayer player) {
        long now = player.level().getGameTime();
        COMBAT_TICKS.put(player.getUUID(), now);
        recordActivity(player);
    }

    public static void wakeDormant(ServerPlayer player) {
        if (!isDormant(player)) {
            return;
        }
        player.stopRiding();
        player.removeTag(SLIME_DORMANT_TAG);
        SlimeFormVisuals.queueDormantRemoval(player);
        Boolean previousInvisibility = DORMANT_PREVIOUS_INVISIBILITY.remove(player.getUUID());
        player.setInvisible(previousInvisibility != null && previousInvisibility);
        player.sendOverlayMessage(Component.literal("Dormant mode ended.").withStyle(ChatFormatting.GREEN));
        ACTIVITY_TICKS.put(player.getUUID(), player.level().getGameTime());
        ACTIVITY_POSITIONS.put(player.getUUID(), player.position());
    }

    private static void tickDormantPlayers(MinecraftServer server) {
        SlimeFormConfig config = SlimeFormConfig.get();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!SlimeFormState.isActive(player)) {
                if (player.level().getGameTime() % 20L == 0L) {
                    sendDormantDebug(player, false, 0);
                }
                wakeDormant(player);
                SlimeFormVisuals.tick(player);
                continue;
            }

            long now = player.level().getGameTime();
            long last = ACTIVITY_TICKS.computeIfAbsent(player.getUUID(), ignored -> now);
            if (isDormant(player)) {
                if (player.level().getGameTime() % 20L == 0L) {
                    sendDormantDebug(player, false, 0);
                }
                player.setInvisible(true);
                if (now % 20L == 0L) {
                    player.sendOverlayMessage(
                            Component.literal("AFK mode active ").withStyle(ChatFormatting.AQUA)
                                    .append(Component.literal("— left-click to wake.").withStyle(ChatFormatting.GRAY)));
                }
                SlimeFormVisuals.tick(player);
                continue;
            }

            boolean moved = hasMoved(player);
            if (moved) {
                ACTIVITY_TICKS.put(player.getUUID(), now);
                ACTIVITY_POSITIONS.put(player.getUUID(), player.position());
                last = now;
            }
            long inactivityLimit = config.effectiveAfkInactivitySeconds() * 20L;
            if (config.afkDormantEnabled
                    && config.afkDormantDebug
                    && now - last < inactivityLimit
                    && now % 20L == 0L) {
                int secondsRemaining = (int) Math.ceil((inactivityLimit - (now - last)) / 20.0D);
                player.sendOverlayMessage(
                        Component.literal("Dormant in ").withStyle(ChatFormatting.AQUA)
                                .append(Component.literal(secondsRemaining + "s")
                                        .withStyle(ChatFormatting.YELLOW)));
            }
            if (now % 20L == 0L) {
                boolean showHudTimer = config.afkDormantEnabled
                        && config.afkDormantHudDebug
                        && now - last < inactivityLimit
                        && inactivityLimit - (now - last) <= DORMANT_HUD_LEAD_SECONDS * 20L;
                int hudRemainingTicks = showHudTimer
                        ? (int) Math.max(0L, inactivityLimit - (now - last)) : 0;
                sendDormantDebug(player, showHudTimer, hudRemainingTicks);
            }
            if (config.afkDormantEnabled && now - last >= inactivityLimit) {
                String blockReason = dormantEntryBlockReason(player, now);
                if (blockReason == null) {
                    enterDormant(player);
                } else if (config.afkDormantDebug && now % 20L == 0L) {
                    player.sendOverlayMessage(
                            Component.literal("Dormant paused: ").withStyle(ChatFormatting.YELLOW)
                                    .append(Component.literal(blockReason).withStyle(ChatFormatting.GRAY)));
                }
            }
            SlimeFormVisuals.tick(player);
        }
    }

    private static void sendDormantDebug(ServerPlayer player, boolean visible, int secondsRemaining) {
        ServerPlayNetworking.send(player,
                new SlimeFormPayloads.DormantDebugPayload(visible, secondsRemaining));
    }

    private static void enterDormant(ServerPlayer player) {
        DORMANT_PREVIOUS_INVISIBILITY.put(player.getUUID(), player.isInvisible());
        player.setInvisible(true);
        player.addTag(SLIME_DORMANT_TAG);
        SlimeFormVisuals.attachDormant(player);
        player.sendOverlayMessage(Component.literal("Dormant mode active — left-click to wake.")
                .withStyle(ChatFormatting.AQUA));
    }

    private static String dormantEntryBlockReason(ServerPlayer player, long now) {
        if (!player.onGround() || player.isSwimming() || player.isInWater() || player.isFallFlying()) {
            return "moving or airborne";
        }
        if (player.isPassenger() || player.isVehicle()) {
            return "riding or carrying";
        }
        Long combat = COMBAT_TICKS.get(player.getUUID());
        if (combat != null && now - combat < 100L) {
            return "recent combat";
        }
        AABB dangerArea = player.getBoundingBox().inflate(8.0D, 4.0D, 8.0D);
        List<Enemy> enemies = player.level().getEntitiesOfClass(
                Enemy.class, dangerArea, entity -> entity instanceof LivingEntity living && living.isAlive());
        for (Enemy enemy : enemies) {
            if (enemy instanceof LivingEntity living && living.hasLineOfSight(player)) {
                return "threat nearby";
            }
        }
        return null;
    }

    private static boolean hasMoved(ServerPlayer player) {
        Vec3 last = ACTIVITY_POSITIONS.get(player.getUUID());
        if (last == null) {
            return true;
        }
        return player.position().distanceToSqr(last) > 0.0004D;
    }

    public static void playSlimePlayerEffect(ServerPlayer player, int particleCount, float pitch) {
        ServerLevel level = player.level();
        level.sendParticles(
                ParticleTypes.ITEM_SLIME,
                player.getX(),
                player.getY() + 0.7D,
                player.getZ(),
                particleCount,
                0.35D,
                0.5D,
                0.35D,
                0.08D);
        player.playSound(SoundEvents.SLIME_SQUISH, 1.0F, pitch);
    }

    public static void playSlimeFragmentSpawnEffects(Slime slime) {
        ServerLevel level = (ServerLevel) slime.level();
        level.sendParticles(
                ParticleTypes.ITEM_SLIME,
                slime.getX(),
                slime.getY() + 0.35D,
                slime.getZ(),
                8,
                0.18D,
                0.12D,
                0.18D,
                0.04D);
        level.playSound(
                null,
                slime.getX(),
                slime.getY(),
                slime.getZ(),
                SoundEvents.SLIME_JUMP,
                SoundSource.HOSTILE,
                0.7F,
                1.2F);
    }

    private static int showSlimeStatus(ServerPlayer player) {
        boolean active = SlimeFormState.isActive(player);
        int size = SlimeFormState.getSize(player);
        String recoveryText = !SlimeRecoveryManager.isRecovering(player.getUUID())
                ? "not recovering"
                : SlimeRecoveryManager.recoverySecondsRemaining(player.getUUID())
                        + " seconds remaining";
        MutableComponent status = Component.literal("Slime form: ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(active ? "active" : "inactive")
                        .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                .append(Component.literal(" | size: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(size)).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" | health: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(player.getHealth() + "/" + player.getMaxHealth())
                        .withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" | recovery: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(recoveryText).withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(status);
        return Command.SINGLE_SUCCESS;
    }

    private static int setMorphSize(ServerPlayer player, int size) {
        SlimeFormState.setMorphSize(player, size);
        player.sendSystemMessage(Component.literal(
                "Custom morph size set to " + size + ". It will apply to your next morph.")
                .withStyle(ChatFormatting.GREEN));
        return size;
    }

    private static int resetMorphSize(ServerPlayer player) {
        SlimeFormState.resetMorphSize(player);
        player.sendSystemMessage(Component.literal(
                "Custom morph size reset. Morphing will use your normal slime size.")
                .withStyle(ChatFormatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    public static int commandNearbySlimesToAttack(Player player, LivingEntity target) {
        if (player.level().isClientSide()
                || !SlimeFormState.isActive(player)
                || target instanceof Player
                || (target instanceof Slime && !(target instanceof MagmaCube))
                || !target.isAlive()) {
            return 0;
        }

        int commanded = 0;
        AABB area = player.getBoundingBox().inflate(32.0D);
        for (Slime slime : player.level().getEntitiesOfClass(Slime.class, area)) {
            if (slime.getSize() > SlimeFormState.MIN_SIZE && slime.isAlliedTo(player)) {
                slime.setTarget(target);
                commanded++;
            }
        }
        return commanded;
    }
}
