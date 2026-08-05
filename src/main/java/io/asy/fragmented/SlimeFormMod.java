package io.asy.fragmented;

import com.mojang.brigadier.Command;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.resources.ResourceKey;
import io.asy.fragmented.mixin.SlimeServerConnectionAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SlimeFormMod implements ModInitializer {
    public static final String MOD_ID = "slimeform";
    public static final String SLIME_FORM_TAG = "slimeform.active";
    public static final String SLIME_DORMANT_TAG = "slimeform.dormant";
    private static final String RECOVERY_LINEAGE_TAG_PREFIX = "slimeform.recovery.";
    public static final int RECOVERY_COUNTDOWN_INTERVAL_TICKS = 20;
    public static final int RECOVERY_PROGRESS_BAR_WIDTH = 20;
    public static final int REFORM_PARTICLE_COUNT = 40;
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Map<UUID, Recovery> RECOVERIES = new HashMap<>();
    private static final Map<UUID, Long> PASSIVE_SPAWN_NEXT_ATTEMPT = new HashMap<>();
    private static final String PASSIVE_SLIME_TAG = "slimeform.passive_spawn";
    private static final double PASSIVE_SPAWN_MIN_DISTANCE = 8.0D;
    private static final double PASSIVE_SPAWN_MAX_DISTANCE = 24.0D;
    private static final int PASSIVE_SPAWN_LIGHT_THRESHOLD = 7;

    @Override
    public void onInitialize() {
        SlimeFormConfig.initialize();
        PayloadTypeRegistry.playC2S().register(
                SlimeFormPayloads.WAKE_DORMANT_TYPE,
                SlimeFormPayloads.WAKE_DORMANT_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(
                SlimeFormPayloads.WAKE_DORMANT_TYPE,
                (payload, context) -> wakeDormant(context.player()));
        LOGGER.info("Sliming.");

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickRecoveries(server);
            tickPassiveSlimeSpawning(server);
            tickDormantPlayers(server);
            SlimeFormVisuals.processPendingRemovals(server);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            SlimeFormState.applyHealth(player, false);
            ACTIVITY_TICKS.put(player.getUUID(), player.level().getGameTime());
            ACTIVITY_POSITIONS.put(player.getUUID(), player.position());
            LOGGER.info("[slimeform] Joined {}: active={}, size={}, maxHealth={}, health={}",
                    player.getName().getString(),
                    SlimeFormState.isActive(player),
                    SlimeFormState.getSize(player),
                    player.getMaxHealth(),
                    player.getHealth());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            wakeDormant(player);
            SlimeFormVisuals.remove(player, false);
            SlimeFormVisuals.queueDormantRemoval(player);
            ACTIVITY_TICKS.remove(player.getUUID());
            COMBAT_TICKS.remove(player.getUUID());
            ACTIVITY_POSITIONS.remove(player.getUUID());
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("slime")
                        .executes(context -> activateSlimeForm(context.getSource().getPlayerOrException()))
                        .then(Commands.literal("status")
                                .executes(context -> showSlimeStatus(context.getSource().getPlayerOrException())))
                        .then(Commands.literal("off")
                                .executes(context -> deactivateSlimeForm(context.getSource().getPlayerOrException())))));
    }

    private static int activateSlimeForm(ServerPlayer player) {
        SlimeFormState.activate(player);
        LOGGER.info("[slimeform] Activated slime form for {} ({})", player.getName().getString(), player.getUUID());
        player.sendSystemMessage(Component.literal("Slime form activated.").withStyle(ChatFormatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int deactivateSlimeForm(ServerPlayer player) {
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
        return player.getTags().contains(SLIME_DORMANT_TAG);
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
        player.displayClientMessage(Component.literal("Dormant mode ended.").withStyle(ChatFormatting.GREEN), true);
        ACTIVITY_TICKS.put(player.getUUID(), player.level().getGameTime());
        ACTIVITY_POSITIONS.put(player.getUUID(), player.position());
    }

    private static void tickDormantPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!SlimeFormState.isActive(player)) {
                wakeDormant(player);
                SlimeFormVisuals.tick(player);
                continue;
            }

            long now = player.level().getGameTime();
            long last = ACTIVITY_TICKS.computeIfAbsent(player.getUUID(), ignored -> now);
            if (isDormant(player)) {
                player.setInvisible(true);
                if (now % 20L == 0L) {
                    player.displayClientMessage(
                            Component.literal("AFK mode active ").withStyle(ChatFormatting.AQUA)
                                    .append(Component.literal("— left-click to wake.").withStyle(ChatFormatting.GRAY)),
                            true);
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
            if (SlimeFormConfig.get().showAfkTimerDebug
                    && SlimeFormConfig.get().afkDormantEnabled
                    && !moved
                    && now % 20L == 0L) {
                long remainingTicks = Math.max(
                        0L,
                        SlimeFormConfig.get().effectiveAfkInactivitySeconds() * 20L
                                - (now - last));
                long remainingSeconds = (remainingTicks + 19L) / 20L;
                player.displayClientMessage(
                        labeledMessage(
                                "AFK dormant in ",
                                remainingSeconds + "s",
                                ChatFormatting.YELLOW,
                                ChatFormatting.AQUA),
                        true);
            }
            if (SlimeFormConfig.get().afkDormantEnabled
                    && now - last >= SlimeFormConfig.get().effectiveAfkInactivitySeconds() * 20L) {
                String blockReason = dormantEntryBlockReason(player, now);
                if (blockReason == null) {
                    enterDormant(player);
                } else if (SlimeFormConfig.get().showAfkTimerDebug && now % 20L == 0L) {
                    player.displayClientMessage(
                            labeledMessage(
                                    "AFK dormant blocked: ",
                                    blockReason,
                                    ChatFormatting.YELLOW,
                                    ChatFormatting.RED),
                            true);
                }
            }
            SlimeFormVisuals.tick(player);
        }
    }

    private static void enterDormant(ServerPlayer player) {
        DORMANT_PREVIOUS_INVISIBILITY.put(player.getUUID(), player.isInvisible());
        player.setInvisible(true);
        player.addTag(SLIME_DORMANT_TAG);
        player.setDeltaMovement(Vec3.ZERO);
        SlimeFormVisuals.tick(player);
        player.displayClientMessage(
                Component.literal("Dormant slime engaged. ").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal("Left-click to wake.").withStyle(ChatFormatting.GRAY)),
                true);
        LOGGER.info("[slimeform] Player {} entered dormant mode; vehicle={}",
                player.getName().getString(),
                player.getVehicle() == null ? "none" : player.getVehicle().getType());
    }

    private static boolean hasMoved(ServerPlayer player) {
        Vec3 previous = ACTIVITY_POSITIONS.get(player.getUUID());
        return previous != null && player.position().distanceToSqr(previous) > 0.000001D;
    }

    private static String dormantEntryBlockReason(ServerPlayer player, long now) {
        long combat = COMBAT_TICKS.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        boolean combatCooldownComplete = combat == Long.MIN_VALUE || now - combat >= 200L;
        if (player.gameMode() != GameType.SURVIVAL) {
            return "survival mode required";
        }
        if (player.isSleeping()) {
            return "player is sleeping";
        }
        if (player.isOnFire()) {
            return "player is on fire";
        }
        if (player.isInLava()) {
            return "player is in lava";
        }
        if (player.isInWater()) {
            return "player is in water";
        }
        if (!player.onGround()) {
            return "player is not on the ground";
        }
        if (player.getVehicle() != null) {
            return "player already has a vehicle";
        }
        if (!combatCooldownComplete) {
            return "combat cooldown active";
        }
        return null;
    }

    private static double getRiderSizeMultiplier(Player player) {
        return SlimeFormState.getRiderSize(player) - SlimeFormState.MIN_SIZE;
    }

    public static double getRiderOffsetX(Player player) {
        return SlimeFormConfig.get().effectiveRiderOffsetX() * getRiderSizeMultiplier(player);
    }

    public static double getRiderOffsetY(Player player) {
        return SlimeFormConfig.get().effectiveRiderOffsetYPerSize() * getRiderSizeMultiplier(player);
    }

    public static double getRiderOffsetZ(Player player) {
        return SlimeFormConfig.get().effectiveRiderOffsetZ() * getRiderSizeMultiplier(player);
    }

    public static int getSplitDurationTicks() {
        return SlimeFormConfig.get().effectiveSplitDurationSeconds()
                * RECOVERY_COUNTDOWN_INTERVAL_TICKS;
    }

    public static String recoveryLineageTag(UUID playerId) {
        return RECOVERY_LINEAGE_TAG_PREFIX + playerId;
    }

    public static boolean hasRecoveryLineage(Slime slime) {
        return slime.getTags().stream().anyMatch(tag -> tag.startsWith(RECOVERY_LINEAGE_TAG_PREFIX));
    }

    public static void copyRecoveryLineage(Slime parent, Slime child) {
        parent.getTags().stream()
                .filter(tag -> tag.startsWith(RECOVERY_LINEAGE_TAG_PREFIX))
                .forEach(child::addTag);
    }

    public static void beginRecovery(
            ServerPlayer player,
            List<Slime> splitSlimes,
            GameType previousGameMode,
            LivingEntity originalKiller) {
        if (splitSlimes.isEmpty()) {
            return;
        }

        Entity cameraTarget = splitSlimes.get(0);
        InventorySnapshot inventory = captureAndClearInventory(player);
        RECOVERIES.put(player.getUUID(), new Recovery(
                cameraTarget.getUUID(),
                splitSlimes.stream().map(Entity::getUUID).toList(),
                splitSlimes.get(0).getTags().stream()
                        .filter(tag -> tag.startsWith(RECOVERY_LINEAGE_TAG_PREFIX))
                        .findFirst()
                        .orElseThrow(),
                originalKiller == null ? null : originalKiller.getUUID(),
                player.level().dimension(),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot(),
                previousGameMode,
                getSplitDurationTicks(),
                inventory));
        LOGGER.info("[slimeform] Recovery started for {} ({} ticks) at {} ({}, {}, {})",
                player.getName().getString(),
                getSplitDurationTicks(),
                player.level().dimension().identifier(),
                player.getX(),
                player.getY(),
                player.getZ());
    }

    private static void tickRecoveries(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Recovery>> iterator = RECOVERIES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Recovery> entry = iterator.next();
            Recovery recovery = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                cleanupSplitSlimes(server, recovery);
                iterator.remove();
                continue;
            }

            updateLastKnownSplitPosition(server, recovery);
            List<Slime> survivingSlimes = getSurvivingSplitSlimes(server, recovery);
            if (survivingSlimes.isEmpty()) {
                completeFailedRecovery(server, player, recovery, iterator);
                continue;
            }
            continueRecoveryDefense(server, recovery, survivingSlimes);
            coordinateRecoveryAssistance(recovery, survivingSlimes);
            player.setCamera(survivingSlimes.get(0));

            recovery.ticksRemaining--;
            if (recovery.ticksRemaining % RECOVERY_COUNTDOWN_INTERVAL_TICKS == 0
                    && recovery.ticksRemaining > 0) {
                player.displayClientMessage(
                        createRecoveryProgressBar(recovery.ticksRemaining, recovery.totalTicks), true);
            }
            if (recovery.ticksRemaining > 0) {
                continue;
            }

            ServerPlayer replacement = server.getPlayerList().respawn(
                    player, false, Entity.RemovalReason.KILLED);
            if (replacement == null) {
                LOGGER.warn("[slimeform] Respawn returned no player for {}", entry.getKey());
                cleanupSplitSlimes(server, recovery);
                iterator.remove();
                continue;
            }

            SlimeServerConnectionAccessor connection =
                    (SlimeServerConnectionAccessor) replacement.connection;
            connection.slimeform$setPlayer(replacement);

            GameType restoredGameMode = recovery.previousGameMode();
            replacement.gameMode.changeGameModeForPlayer(restoredGameMode);
            replacement.setGameMode(restoredGameMode);
            replacement.onUpdateAbilities();
            replacement.connection.send(new ClientboundGameEventPacket(
                    ClientboundGameEventPacket.CHANGE_GAME_MODE,
                    restoredGameMode.getId()));

            Slime destination = survivingSlimes.get(0);
            ServerLevel recoveryLevel = (ServerLevel) destination.level();
            replacement.teleportTo(
                    recoveryLevel,
                    destination.getX(),
                    destination.getY(),
                    destination.getZ(),
                    Set.of(),
                    recovery.yRot(),
                    recovery.xRot(),
                    false);
            replacement.setCamera(replacement);
            int strongestFragmentSize = survivingSlimes.stream()
                    .mapToInt(Slime::getSize)
                    .max()
                    .orElse(SlimeFormState.MIN_SIZE);
            SlimeFormState.setSize(replacement, strongestFragmentSize + 1);
            SlimeFormState.applyHealth(replacement, true);
            restoreInventory(replacement, recovery.inventory());
            cleanupSplitSlimes(server, recovery);
            playReformEffects(recoveryLevel, replacement);
            replacement.displayClientMessage(Component.empty(), true);
            replacement.sendSystemMessage(Component.literal("You have reformed.").withStyle(ChatFormatting.GREEN));
            LOGGER.info("[slimeform] Recovery completed for {} in {} at ({}, {}, {}), "
                            + "gameMode()={}, internalMode={}, spectator={}, connectionPlayer={}",
                    replacement.getName().getString(),
                    recoveryLevel.dimension().identifier(),
                    replacement.getX(),
                    replacement.getY(),
                    replacement.getZ(),
                    replacement.gameMode(),
                    replacement.gameMode.getGameModeForPlayer(),
                    replacement.isSpectator(),
                    connection.slimeform$getPlayer().getUUID());
            iterator.remove();
        }
    }

    private static void tickPassiveSlimeSpawning(MinecraftServer server) {
        SlimeFormConfig config = SlimeFormConfig.get();
        if (!config.passiveSlimeSpawning) {
            PASSIVE_SPAWN_NEXT_ATTEMPT.clear();
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            if (!SlimeFormState.isActive(player) || player.isSpectator()) {
                PASSIVE_SPAWN_NEXT_ATTEMPT.remove(playerId);
                continue;
            }

            long gameTime = player.level().getGameTime();
            long nextAttempt = PASSIVE_SPAWN_NEXT_ATTEMPT.getOrDefault(playerId, 0L);
            if (gameTime < nextAttempt) {
                continue;
            }
            PASSIVE_SPAWN_NEXT_ATTEMPT.put(
                    playerId,
                    gameTime + config.effectivePassiveSlimeSpawnCooldownSeconds()
                            * RECOVERY_COUNTDOWN_INTERVAL_TICKS);

            tryPassiveSlimeSpawn(player, config);
        }
    }

    private static void tryPassiveSlimeSpawn(ServerPlayer player, SlimeFormConfig config) {
        ServerLevel level = (ServerLevel) player.level();
        AABB nearbyArea = player.getBoundingBox().inflate(PASSIVE_SPAWN_MAX_DISTANCE);
        int passiveNearby = level.getEntitiesOfClass(
                Slime.class,
                nearbyArea,
                slime -> slime.getTags().contains(PASSIVE_SLIME_TAG)).size();
        if (passiveNearby >= config.effectiveMaxNearbySpawnedSlimes()
                || !hasMonsterMobCapSpace(level)) {
            return;
        }

        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        double distance = PASSIVE_SPAWN_MIN_DISTANCE
                + level.random.nextDouble()
                * (PASSIVE_SPAWN_MAX_DISTANCE - PASSIVE_SPAWN_MIN_DISTANCE);
        int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
        int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos spawnPos = new BlockPos(x, y, z);
        int light = level.getMaxLocalRawBrightness(spawnPos);
        boolean nighttime = level.getDayTime() % 24000L >= 13000L;
        if (!nighttime && light > PASSIVE_SPAWN_LIGHT_THRESHOLD) {
            return;
        }

        double distanceWeight = distance / PASSIVE_SPAWN_MAX_DISTANCE;
        double conditionWeight = nighttime ? 1.0D : 0.5D;
        if (light <= PASSIVE_SPAWN_LIGHT_THRESHOLD) {
            conditionWeight += 0.25D;
        }
        double chance = config.effectivePassiveSlimeSpawnChance()
                / 100.0D
                * distanceWeight
                * conditionWeight;
        if (level.random.nextDouble() >= chance
                || !Slime.checkSlimeSpawnRules(
                        EntityType.SLIME,
                        level,
                        EntitySpawnReason.NATURAL,
                        spawnPos,
                        level.random)) {
            return;
        }

        Slime slime = EntityType.SLIME.create(level, EntitySpawnReason.NATURAL);
        if (slime == null) {
            return;
        }
        slime.addTag(PASSIVE_SLIME_TAG);
        slime.finalizeSpawn(
                level,
                level.getCurrentDifficultyAt(spawnPos),
                EntitySpawnReason.NATURAL,
                null);
        slime.setPos(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D);
        if (level.addFreshEntity(slime)) {
            LOGGER.debug("[slimeform] Passive slime attracted near {}", player.getName().getString());
        }
    }

    private static boolean hasMonsterMobCapSpace(ServerLevel level) {
        NaturalSpawner.SpawnState spawnState = level.getChunkSource().getLastSpawnState();
        if (spawnState == null || spawnState.getSpawnableChunkCount() <= 0) {
            return false;
        }
        int monsterCount = spawnState.getMobCategoryCounts().getInt(MobCategory.MONSTER);
        int monsterCap = spawnState.getSpawnableChunkCount()
                * MobCategory.MONSTER.getMaxInstancesPerChunk();
        return monsterCount < monsterCap;
    }

    private static void completeFailedRecovery(
            MinecraftServer server,
            ServerPlayer player,
            Recovery recovery,
            Iterator<Map.Entry<UUID, Recovery>> iterator) {
        ServerPlayer replacement = server.getPlayerList().respawn(
                player, false, Entity.RemovalReason.KILLED);
        if (replacement == null) {
            LOGGER.warn("[slimeform] Respawn returned no player after failed recovery for {}", player.getUUID());
            cleanupSplitSlimes(server, recovery);
            iterator.remove();
            return;
        }

        SlimeServerConnectionAccessor connection =
                (SlimeServerConnectionAccessor) replacement.connection;
        connection.slimeform$setPlayer(replacement);

        GameType restoredGameMode = recovery.previousGameMode();
        replacement.gameMode.changeGameModeForPlayer(restoredGameMode);
        replacement.setGameMode(restoredGameMode);
        replacement.onUpdateAbilities();
        replacement.connection.send(new ClientboundGameEventPacket(
                ClientboundGameEventPacket.CHANGE_GAME_MODE,
                restoredGameMode.getId()));
        replacement.setCamera(replacement);
        SlimeFormState.setSize(replacement, SlimeFormState.getMaxSize());
        SlimeFormState.applyHealth(replacement, true);
        playRecoveryFailureEffects((ServerLevel) replacement.level(), replacement);
        dropInventory(server, recovery);
        cleanupSplitSlimes(server, recovery);
        replacement.sendSystemMessage(
                Component.literal("Recovery failed. ").withStyle(ChatFormatting.RED)
                        .append(Component.literal(
                                "All split slimes were lost; you respawned at your spawnpoint.")
                                .withStyle(ChatFormatting.GRAY)));
        LOGGER.info("[slimeform] Failed recovery for {}; inventory was lost and player respawned at spawnpoint",
                replacement.getName().getString());
        iterator.remove();
    }

    private static List<Slime> getSurvivingSplitSlimes(MinecraftServer server, Recovery recovery) {
        ServerLevel level = server.getLevel(recovery.dimension());
        if (level == null) {
            return List.of();
        }

        List<Slime> surviving = new ArrayList<>();
        String lineageTag = recovery.lineageTag();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Slime slime
                    && slime.getTags().contains(lineageTag)
                    && slime.isAlive()
                    && !slime.isRemoved()) {
                surviving.add(slime);
            }
        }
        return surviving;
    }

    private static void continueRecoveryDefense(
            MinecraftServer server,
            Recovery recovery,
            List<Slime> survivingSlimes) {
        UUID originalKillerId = recovery.originalKillerId();
        if (originalKillerId == null || recovery.postAvenging()) {
            if (recovery.postAvenging()) {
                commandRecoverySlimesToAttackHostiles(survivingSlimes);
            }
            return;
        }

        ServerLevel level = server.getLevel(recovery.dimension());
        if (level == null) {
            return;
        }

        Entity originalKiller = level.getEntity(originalKillerId);
        if (originalKiller instanceof LivingEntity living && living.isAlive()) {
            return;
        }

        recovery.setPostAvenging();
        commandRecoverySlimesToAttackHostiles(survivingSlimes);
    }

    private static void commandRecoverySlimesToAttackHostiles(List<Slime> survivingSlimes) {
        List<Slime> attackingSlimes = survivingSlimes.stream()
                .filter(slime -> slime.getSize() > SlimeFormState.MIN_SIZE)
                .toList();
        if (attackingSlimes.isEmpty()) {
            return;
        }

        LivingEntity sharedTarget = attackingSlimes.stream()
                .map(Slime::getTarget)
                .filter(SlimeFormMod::isRecoveryHostile)
                .findFirst()
                .orElse(null);

        if (sharedTarget == null) {
            double nearestDistance = Double.MAX_VALUE;
            for (Slime slime : attackingSlimes) {
                for (Mob mob : slime.level().getEntitiesOfClass(
                        Mob.class,
                        slime.getBoundingBox().inflate(32.0D),
                        SlimeFormMod::isRecoveryHostile)) {
                    double distance = slime.distanceToSqr(mob);
                    if (distance < nearestDistance) {
                        sharedTarget = mob;
                        nearestDistance = distance;
                    }
                }
            }
        }

        if (sharedTarget == null) {
            return;
        }

        for (Slime slime : attackingSlimes) {
            slime.setTarget(sharedTarget);
        }
    }

    private static void coordinateRecoveryAssistance(
            Recovery recovery,
            List<Slime> survivingSlimes) {
        LivingEntity sharedTarget = findRecoveryHostileTarget(survivingSlimes);
        if (sharedTarget != null) {
            for (Slime slime : survivingSlimes) {
                if (slime.getSize() > SlimeFormState.MIN_SIZE) {
                    slime.setTarget(sharedTarget);
                } else {
                    slime.setTarget(null);
                }
            }
            assistNearbyNormalSlimes(recovery, survivingSlimes, sharedTarget);
        }

        maintainSizeOneFleeing(survivingSlimes);
    }

    private static LivingEntity findRecoveryHostileTarget(List<Slime> survivingSlimes) {
        LivingEntity currentTarget = survivingSlimes.stream()
                .filter(slime -> slime.getSize() > SlimeFormState.MIN_SIZE)
                .map(Slime::getTarget)
                .filter(SlimeFormMod::isRecoveryHostile)
                .findFirst()
                .orElse(null);
        if (currentTarget != null) {
            return currentTarget;
        }

        double nearestDistance = Double.MAX_VALUE;
        LivingEntity nearest = null;
        for (Slime splitSlime : survivingSlimes) {
            AABB area = splitSlime.getBoundingBox().inflate(15.0D);
            for (Mob mob : splitSlime.level().getEntitiesOfClass(
                    Mob.class,
                    area,
                    SlimeFormMod::isRecoveryHostile)) {
                double distance = splitSlime.distanceToSqr(mob);
                if (distance < nearestDistance) {
                    nearest = mob;
                    nearestDistance = distance;
                }
            }

            for (Slime nearbySlime : splitSlime.level().getEntitiesOfClass(
                    Slime.class,
                    area,
                    slime -> isRecoveryHostile(slime.getTarget()))) {
                LivingEntity target = nearbySlime.getTarget();
                double distance = splitSlime.distanceToSqr(nearbySlime);
                if (target != null && distance < nearestDistance) {
                    nearest = target;
                    nearestDistance = distance;
                }
            }
        }
        return nearest;
    }

    private static void assistNearbyNormalSlimes(
            Recovery recovery,
            List<Slime> survivingSlimes,
            LivingEntity target) {
        for (Slime splitSlime : survivingSlimes) {
            for (Slime nearbySlime : splitSlime.level().getEntitiesOfClass(
                    Slime.class,
                    splitSlime.getBoundingBox().inflate(15.0D),
                    slime -> slime.isAlive()
                            && slime.getSize() > SlimeFormState.MIN_SIZE
                            && (!hasRecoveryLineage(slime)
                            || survivingSlimes.contains(slime)))) {
                nearbySlime.setTarget(target);
                if (!hasRecoveryLineage(nearbySlime)) {
                    recovery.assistedSlimeIds().add(nearbySlime.getUUID());
                }
            }
        }
    }

    private static void maintainSizeOneFleeing(List<Slime> survivingSlimes) {
        for (Slime slime : survivingSlimes) {
            if (slime.getSize() != SlimeFormState.MIN_SIZE) {
                continue;
            }

            slime.setTarget(null);
            Mob threat = slime.level().getEntitiesOfClass(
                            Mob.class,
                            slime.getBoundingBox().inflate(15.0D),
                            mob -> isRecoveryHostile(mob))
                    .stream()
                    .min(Comparator.comparingDouble(slime::distanceToSqr))
                    .orElse(null);
            if (threat == null) {
                continue;
            }

            if (!slime.getNavigation().isDone() && !slime.getNavigation().isStuck()) {
                continue;
            }

            Vec3 away = slime.position().subtract(threat.position());
            double angle = Math.atan2(away.z, away.x);
            if (away.horizontalDistanceSqr() < 0.0001D) {
                angle = slime.getRandom().nextDouble() * Math.PI * 2.0D;
            }
            angle += (slime.getRandom().nextDouble() - 0.5D) * 1.2D;
            double distance = 8.0D + slime.getRandom().nextDouble() * 6.0D;
            double destinationX = slime.getX() + Math.cos(angle) * distance;
            double destinationZ = slime.getZ() + Math.sin(angle) * distance;
            slime.getNavigation().moveTo(destinationX, slime.getY(), destinationZ, 1.2D);
        }
    }

    private static boolean isRecoveryHostile(LivingEntity entity) {
        return entity instanceof Enemy
                && !(entity instanceof Slime)
                && entity.isAlive();
    }

    private static void updateLastKnownSplitPosition(MinecraftServer server, Recovery recovery) {
        ServerLevel level = server.getLevel(recovery.dimension());
        if (level == null) {
            return;
        }

        String lineageTag = recovery.lineageTag();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Slime slime && slime.getTags().contains(lineageTag)) {
                recovery.lastKnownPosition = slime.position();
            }
        }
    }

    private static void dropInventory(MinecraftServer server, Recovery recovery) {
        ServerLevel level = server.getLevel(recovery.dimension());
        if (level == null) {
            return;
        }

        Vec3 position = recovery.lastKnownPosition;
        int dropped = 0;
        for (ItemStack stack : recovery.inventory().allStacks()) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemEntity item = new ItemEntity(
                    level,
                    position.x,
                    position.y,
                    position.z,
                    stack.copy());
            item.setPickUpDelay(40);
            level.addFreshEntity(item);
            dropped++;
        }
        if (dropped > 0) {
            LOGGER.info("[slimeform] Dropped {} inventory stacks after failed recovery", dropped);
        }
    }

    private static InventorySnapshot captureAndClearInventory(ServerPlayer player) {
        List<ItemStack> inventoryItems = new ArrayList<>();
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            inventoryItems.add(stack.copy());
        }

        Map<EquipmentSlot, ItemStack> equipmentItems = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : carriedEquipmentSlots()) {
            equipmentItems.put(slot, player.getItemBySlot(slot).copy());
        }

        player.getInventory().getNonEquipmentItems().replaceAll(stack -> ItemStack.EMPTY);
        for (EquipmentSlot slot : carriedEquipmentSlots()) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        player.getInventory().setChanged();
        return new InventorySnapshot(inventoryItems, equipmentItems);
    }

    private static void restoreInventory(ServerPlayer player, InventorySnapshot inventory) {
        List<ItemStack> inventoryItems = player.getInventory().getNonEquipmentItems();
        for (int index = 0; index < inventoryItems.size(); index++) {
            inventoryItems.set(index, inventory.items().get(index).copy());
        }
        for (EquipmentSlot slot : carriedEquipmentSlots()) {
            player.setItemSlot(slot, inventory.equipment().getOrDefault(slot, ItemStack.EMPTY).copy());
        }
        player.getInventory().setChanged();
    }

    private static Set<EquipmentSlot> carriedEquipmentSlots() {
        return EnumSet.of(
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET,
                EquipmentSlot.OFFHAND);
    }

    private static Component createRecoveryProgressBar(int ticksRemaining, int totalTicks) {
        int totalSeconds = totalTicks / RECOVERY_COUNTDOWN_INTERVAL_TICKS;
        int seconds = (int) Math.ceil(ticksRemaining / (double) RECOVERY_COUNTDOWN_INTERVAL_TICKS);
        double progress = ticksRemaining / (double) totalTicks;
        int filled = Math.max(0, Math.min(RECOVERY_PROGRESS_BAR_WIDTH,
                (int) Math.ceil(progress * RECOVERY_PROGRESS_BAR_WIDTH)));
        ChatFormatting color = progress > 0.5D
                ? ChatFormatting.GREEN
                : progress > 0.25D ? ChatFormatting.YELLOW : ChatFormatting.RED;

        MutableComponent bar = Component.literal("Reforming [").withStyle(ChatFormatting.GRAY);
        bar.append(Component.literal("█".repeat(filled)).withStyle(color));
        bar.append(Component.literal("░".repeat(RECOVERY_PROGRESS_BAR_WIDTH - filled))
                .withStyle(ChatFormatting.DARK_GRAY));
        bar.append(Component.literal("] " + seconds + "s / " + totalSeconds + "s")
                .withStyle(color));
        return bar;
    }

    private static void playReformEffects(ServerLevel level, ServerPlayer player) {
        level.sendParticles(
                ParticleTypes.ITEM_SLIME,
                player.getX(),
                player.getY() + 1.0D,
                player.getZ(),
                REFORM_PARTICLE_COUNT,
                0.5D,
                0.8D,
                0.5D,
                0.1D);
        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.SLIME_SQUISH,
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
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

    private static void playRecoveryFailureEffects(ServerLevel level, ServerPlayer player) {
        level.sendParticles(
                ParticleTypes.ITEM_SLIME,
                player.getX(),
                player.getY() + 0.7D,
                player.getZ(),
                24,
                0.45D,
                0.55D,
                0.45D,
                0.06D);
        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.SLIME_SQUISH,
                SoundSource.PLAYERS,
                1.0F,
                0.65F);
    }

    private static int showSlimeStatus(ServerPlayer player) {
        boolean active = SlimeFormState.isActive(player);
        int size = SlimeFormState.getSize(player);
        Recovery recovery = RECOVERIES.get(player.getUUID());
        String recoveryText = recovery == null
                ? "not recovering"
                : (int) Math.ceil(recovery.ticksRemaining
                        / (double) RECOVERY_COUNTDOWN_INTERVAL_TICKS) + " seconds remaining";
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

    private static MutableComponent labeledMessage(
            String label,
            String value,
            ChatFormatting labelColor,
            ChatFormatting valueColor) {
        return Component.literal(label).withStyle(labelColor)
                .append(Component.literal(value).withStyle(valueColor));
    }

    private static void cleanupSplitSlimes(MinecraftServer server, Recovery recovery) {
        ServerLevel level = server.getLevel(recovery.dimension());
        if (level == null) {
            return;
        }

        int removed = 0;
        String lineageTag = recovery.lineageTag();
        for (UUID assistedId : recovery.assistedSlimeIds()) {
            Entity entity = level.getEntity(assistedId);
            if (entity instanceof Slime slime && !slime.isRemoved()) {
                slime.setTarget(null);
            }
        }
        List<Entity> entities = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            entities.add(entity);
        }
        for (Entity entity : entities) {
            if (entity instanceof Slime slime
                    && slime.getTags().contains(lineageTag)
                    && !slime.isRemoved()) {
                slime.discard();
                removed++;
            }
        }
        if (removed > 0) {
            LOGGER.info("[slimeform] Removed {} temporary split slimes", removed);
        }
    }

    public static int commandNearbySlimesToAttack(Player player, LivingEntity target) {
        if (player.level().isClientSide()
                || !SlimeFormState.isActive(player)
                || target instanceof Player
                || target instanceof Slime
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
        if (commanded > 0) {
            LOGGER.info("[slimeform] {} commanded {} allied slimes to attack {} ({})",
                    player.getName().getString(),
                    commanded,
                    target.getName().getString(),
                    target.getUUID());
        }
        return commanded;
    }

    private static final class Recovery {
        private final UUID cameraTarget;
        private final List<UUID> splitSlimes;
        private final String lineageTag;
        private final UUID originalKillerId;
        private final ResourceKey<Level> dimension;
        private final double x;
        private final double y;
        private final double z;
        private final float yRot;
        private final float xRot;
        private final GameType previousGameMode;
        private int ticksRemaining;
        private final int totalTicks;
        private final InventorySnapshot inventory;
        private Vec3 lastKnownPosition;
        private boolean postAvenging;
        private final Set<UUID> assistedSlimeIds = new HashSet<>();

        private Recovery(UUID cameraTarget,
                         List<UUID> splitSlimes,
                         String lineageTag,
                         UUID originalKillerId,
                         ResourceKey<Level> dimension,
                         double x,
                         double y,
                         double z,
                         float yRot,
                         float xRot,
                         GameType previousGameMode,
                         int ticksRemaining,
                         InventorySnapshot inventory) {
            this.cameraTarget = cameraTarget;
            this.splitSlimes = splitSlimes;
            this.lineageTag = lineageTag;
            this.originalKillerId = originalKillerId;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yRot = yRot;
            this.xRot = xRot;
            this.previousGameMode = previousGameMode;
            this.ticksRemaining = ticksRemaining;
            this.totalTicks = ticksRemaining;
            this.inventory = inventory;
            this.lastKnownPosition = new Vec3(x, y, z);
        }

        private UUID cameraTarget() {
            return cameraTarget;
        }

        private List<UUID> splitSlimes() {
            return splitSlimes;
        }

        private String lineageTag() {
            return lineageTag;
        }

        private UUID originalKillerId() {
            return originalKillerId;
        }

        private boolean postAvenging() {
            return postAvenging;
        }

        private void setPostAvenging() {
            postAvenging = true;
        }

        private Set<UUID> assistedSlimeIds() {
            return assistedSlimeIds;
        }

        private GameType previousGameMode() {
            return previousGameMode;
        }

        private ResourceKey<Level> dimension() {
            return dimension;
        }

        private double x() {
            return x;
        }

        private double y() {
            return y;
        }

        private double z() {
            return z;
        }

        private float yRot() {
            return yRot;
        }

        private float xRot() {
            return xRot;
        }

        private InventorySnapshot inventory() {
            return inventory;
        }
    }

    private record InventorySnapshot(
            List<ItemStack> items,
            Map<EquipmentSlot, ItemStack> equipment) {
        private List<ItemStack> allStacks() {
            List<ItemStack> stacks = new ArrayList<>(items);
            stacks.addAll(equipment.values());
            return stacks;
        }
    }
}
