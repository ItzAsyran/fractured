package io.asy.fragmented;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.server.level.ServerBossEvent;
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
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
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
import java.util.function.ToIntFunction;

public class SlimeFormMod implements ModInitializer {
    public static final String MOD_ID = "slimeform";
    public static final String SLIME_FORM_TAG = "slimeform.active";
    public static final String SLIME_DORMANT_TAG = "slimeform.dormant";
    public static final int RECOVERY_COUNTDOWN_INTERVAL_TICKS = 20;
    private static final int SIZE_ONE_RECOVERY_MAX_TICKS = 3 * 60 * RECOVERY_COUNTDOWN_INTERVAL_TICKS;
    public static final int RECOVERY_PROGRESS_BAR_WIDTH = 20;
    public static final int REFORM_PARTICLE_COUNT = 40;
    /** Runtime-tunable independent offsets relative to each safe-zone anchor. */
    public static double ITEM_MAIN_HAND_OFFSET_X = 0.0D;
    public static double ITEM_MAIN_HAND_OFFSET_Y = 0.0D;
    public static double ITEM_MAIN_HAND_OFFSET_Z = 0.0D;
    public static double ITEM_OFF_HAND_OFFSET_X = 0.0D;
    public static double ITEM_OFF_HAND_OFFSET_Y = 0.0D;
    public static double ITEM_OFF_HAND_OFFSET_Z = 0.0D;
    public static double ITEM_DEBUG_SCALE = 0.3D;
    public static float ITEM_DEBUG_ROTATION_X = 90.0F;
    public static float ITEM_DEBUG_ROTATION_Y = 0.0F;
    public static float ITEM_DEBUG_ROTATION_Z = 0.0F;
    public static double ITEM_DISPLAY_BOB_AMPLITUDE = 0.01D;
    public static final long ITEM_DISPLAY_BOB_PERIOD_TICKS = 40L;
    public static boolean ITEM_DEBUG_SHOW_AXES = false;
    private static CalibrationSession CALIBRATION_SESSION;
    private static final Map<CalibrationOrientation, CalibrationBounds> CALIBRATED_ZONES =
            new EnumMap<>(CalibrationOrientation.class);
    static final double RECOVERY_FLEE_THREAT_RADIUS = 32.0D;
    private static final long RECOVERY_ACTIVE_DAMAGE_SOURCE_TICKS = 40L;
    private static final int RECOVERY_FLEE_DIRECTION_COUNT = 8;
    private static final double[] RECOVERY_FLEE_WAYPOINT_DISTANCES = {8.0D, 12.0D, 16.0D};
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Map<UUID, Recovery> RECOVERIES = new HashMap<>();
    private static final Map<UUID, Long> PASSIVE_SPAWN_NEXT_ATTEMPT = new HashMap<>();
    private static final String PASSIVE_SLIME_TAG = "slimeform.passive_spawn";
    private static final String RECOVERY_DEBUG_NAME_TAG = "slimeform.recovery_debug_name";
    private static final double PASSIVE_SPAWN_MIN_DISTANCE = 8.0D;
    private static final double PASSIVE_SPAWN_MAX_DISTANCE = 24.0D;
    private static final int PASSIVE_SPAWN_LIGHT_THRESHOLD = 7;

    static {
        CALIBRATED_ZONES.put(
                CalibrationOrientation.WEST_EAST,
                new CalibrationBounds(-0.150D, 0.150D, -0.100D, 0.200D));
        CALIBRATED_ZONES.put(
                CalibrationOrientation.NORTH_SOUTH,
                new CalibrationBounds(-0.150D, 0.150D, -0.200D, 0.110D));
    }

    private enum CalibrationOrientation {
        WEST_EAST("westeast"),
        NORTH_SOUTH("northsouth");

        private final String commandName;

        CalibrationOrientation(String commandName) {
            this.commandName = commandName;
        }
    }

    private enum CalibrationCorner {
        TOP_LEFT("top-left"),
        BOTTOM_RIGHT("bottom-right");

        private final String displayName;

        CalibrationCorner(String displayName) {
            this.displayName = displayName;
        }
    }

    private enum CalibrationAxis {
        X("X"),
        Z("Z");

        private final String displayName;

        CalibrationAxis(String displayName) {
            this.displayName = displayName;
        }
    }

    private static final class CalibrationSession {
        private final UUID operatorId;
        private final CalibrationOrientation orientation;
        private CalibrationCorner corner = CalibrationCorner.TOP_LEFT;
        private CalibrationAxis axis = CalibrationAxis.X;
        private double x;
        private double z;
        private double topLeftX;
        private double topLeftZ;

        private CalibrationSession(UUID operatorId, CalibrationOrientation orientation) {
            this.operatorId = operatorId;
            this.orientation = orientation;
        }

        private double currentValue() {
            return axis == CalibrationAxis.X ? x : z;
        }

        private void setCurrentValue(double value) {
            if (axis == CalibrationAxis.X) {
                x = value;
            } else {
                z = value;
            }
        }
    }

    public record CalibrationBounds(double minX, double maxX, double minZ, double maxZ) {
        private static CalibrationBounds from(CalibrationSession session) {
            return new CalibrationBounds(
                    Math.min(session.topLeftX, session.x),
                    Math.max(session.topLeftX, session.x),
                    Math.min(session.topLeftZ, session.z),
                    Math.max(session.topLeftZ, session.z));
        }
    }

    public static CalibrationBounds calibrationZone(boolean northSouth) {
        return CALIBRATED_ZONES.get(northSouth
                ? CalibrationOrientation.NORTH_SOUTH
                : CalibrationOrientation.WEST_EAST);
    }

    @Override
    public void onInitialize() {
        SlimeFormConfig.initialize();
        PayloadTypeRegistry.playC2S().register(
                SlimeFormPayloads.CLIENT_COMPANION_TYPE,
                SlimeFormPayloads.CLIENT_COMPANION_CODEC);
        PayloadTypeRegistry.playC2S().register(
                SlimeFormPayloads.WAKE_DORMANT_TYPE,
                SlimeFormPayloads.WAKE_DORMANT_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(
                SlimeFormPayloads.CLIENT_COMPANION_TYPE,
                (payload, context) -> CLIENT_COMPANION_PLAYERS.add(context.player().getUUID()));
        ServerPlayNetworking.registerGlobalReceiver(
                SlimeFormPayloads.WAKE_DORMANT_TYPE,
                (payload, context) -> wakeDormant(context.player()));
        LOGGER.info("Sliming.");

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickRecoveries(server);
            tickRecoveryDebugNames(server);
            tickPassiveSlimeSpawning(server);
            tickDormantPlayers(server);
            SlimeFormVisuals.processPendingRemovals(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(SlimeFormVisuals::restoreAllHiddenInventories);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            CLIENT_COMPANION_PLAYERS.remove(player.getUUID());
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
            clearCalibrationFor(player);
            wakeDormant(player);
            restoreSleepingVisibility(player);
            SlimeFormVisuals.restoreHiddenInventory(player);
            SlimeFormVisuals.remove(player, false);
            SlimeFormVisuals.queueDormantRemoval(player);
            ACTIVITY_TICKS.remove(player.getUUID());
            COMBAT_TICKS.remove(player.getUUID());
            ACTIVITY_POSITIONS.remove(player.getUUID());
            CLIENT_COMPANION_PLAYERS.remove(player.getUUID());
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("slime")
                        .executes(context -> executeClientCommand(context, SlimeFormMod::activateSlimeForm))
                        .then(Commands.literal("status")
                                .executes(context -> executeClientCommand(context, SlimeFormMod::showSlimeStatus)))
                        .then(Commands.literal("off")
                                .executes(context -> executeClientCommand(context, SlimeFormMod::deactivateSlimeForm)))
                        .then(Commands.literal("calibrate")
                                .then(Commands.literal("westeast")
                                        .executes(context -> executeClientCommand(context,
                                                player -> beginCalibration(player, CalibrationOrientation.WEST_EAST))))
                                .then(Commands.literal("northsouth")
                                        .executes(context -> executeClientCommand(context,
                                                player -> beginCalibration(player, CalibrationOrientation.NORTH_SOUTH))))
                                .then(Commands.literal("done")
                                        .executes(context -> executeClientCommand(context,
                                                SlimeFormMod::finishCalibrationCorner)))
                                .then(Commands.literal("cancel")
                                        .executes(context -> executeClientCommand(context,
                                                SlimeFormMod::cancelCalibration)))
                                .then(Commands.literal("status")
                                        .executes(context -> executeClientCommand(context,
                                                SlimeFormMod::showCalibrationStatus))))
                        .then(Commands.literal("itemdebug")
                                .then(itemDebugHandCommands("mainhand"))
                                .then(itemDebugHandCommands("offhand"))
                                .then(Commands.literal("size")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.01D, 4.0D))
                                                .executes(context -> executeClientCommand(context,
                                                        player -> setItemDebugScale(player,
                                                                DoubleArgumentType.getDouble(context, "value"))))))
                                .then(Commands.literal("axes")
                                        .executes(context -> executeClientCommand(context,
                                                SlimeFormMod::toggleItemDebugAxes)))
                                .then(Commands.literal("rotation")
                                        .then(Commands.literal("x")
                                                .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                                        .executes(context -> executeClientCommand(context,
                                                                player -> setItemDebugRotation(player,
                                                                'x',
                                                                DoubleArgumentType.getDouble(context, "value"))))))
                                        .then(Commands.literal("y")
                                                .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                                        .executes(context -> executeClientCommand(context,
                                                                player -> setItemDebugRotation(player,
                                                                'y',
                                                                DoubleArgumentType.getDouble(context, "value"))))))
                                        .then(Commands.literal("z")
                                                .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                                        .executes(context -> executeClientCommand(context,
                                                                player -> setItemDebugRotation(player,
                                                                'z',
                                                                DoubleArgumentType.getDouble(context, "value"))))))))));
    }

    private static int executeClientCommand(
            CommandContext<CommandSourceStack> context,
            ToIntFunction<ServerPlayer> command) {
        ServerPlayer player = context.getSource().getEntity() instanceof ServerPlayer serverPlayer
                ? serverPlayer
                : null;
        if (player == null) {
            context.getSource().sendFailure(Component.literal(
                    "This command requires the Fractured client companion."));
            return 0;
        }
        if (!hasClientCompanion(player)) {
            player.sendSystemMessage(Component.literal(
                    "The Fractured client companion is required to use /slime.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        return command.applyAsInt(player);
    }

    public static boolean hasClientCompanion(ServerPlayer player) {
        return CLIENT_COMPANION_PLAYERS.contains(player.getUUID());
    }

    private static int beginCalibration(ServerPlayer player, CalibrationOrientation orientation) {
        if (CALIBRATION_SESSION != null && !CALIBRATION_SESSION.operatorId.equals(player.getUUID())) {
            player.sendSystemMessage(Component.literal(
                    "A calibration session is already active for another operator.").withStyle(ChatFormatting.RED));
            return 0;
        }
        CALIBRATED_ZONES.remove(orientation);
        CALIBRATION_SESSION = new CalibrationSession(player.getUUID(), orientation);
        player.sendSystemMessage(Component.literal(String.format(
                java.util.Locale.ROOT,
                "Global %s calibration started. Enter the top-left X value in chat.",
                orientation.commandName)).withStyle(ChatFormatting.AQUA));
        return Command.SINGLE_SUCCESS;
    }

    private static int finishCalibrationCorner(ServerPlayer player) {
        CalibrationSession session = CALIBRATION_SESSION;
        if (session == null) {
            player.sendSystemMessage(Component.literal("No calibration session is active.")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        if (!session.operatorId.equals(player.getUUID())) {
            player.sendSystemMessage(Component.literal("Only the calibration operator can finish this session.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        if (session.axis != CalibrationAxis.X) {
            player.sendSystemMessage(Component.literal(
                    "Enter the Z value before completing this corner.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        if (session.corner == CalibrationCorner.TOP_LEFT) {
            session.topLeftX = session.x;
            session.topLeftZ = session.z;
            session.corner = CalibrationCorner.BOTTOM_RIGHT;
            session.x = 0.0D;
            session.z = 0.0D;
            player.sendSystemMessage(Component.literal(
                    "Top-left saved. Enter the bottom-right X value in chat.").withStyle(ChatFormatting.AQUA));
            return Command.SINGLE_SUCCESS;
        }

        CalibrationBounds bounds = CalibrationBounds.from(session);
        CALIBRATED_ZONES.put(session.orientation, bounds);
        player.sendSystemMessage(Component.literal(
                formatCalibrationBounds(session.orientation, bounds)).withStyle(ChatFormatting.GREEN));
        CALIBRATION_SESSION = null;
        if (CALIBRATED_ZONES.size() == CalibrationOrientation.values().length) {
            player.sendSystemMessage(Component.literal(formatAllCalibrationBounds())
                    .withStyle(ChatFormatting.GOLD));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int cancelCalibration(ServerPlayer player) {
        if (CALIBRATION_SESSION == null) {
            player.sendSystemMessage(Component.literal("No calibration session is active.")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        if (!CALIBRATION_SESSION.operatorId.equals(player.getUUID())) {
            player.sendSystemMessage(Component.literal("Only the calibration operator can cancel this session.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        CALIBRATION_SESSION = null;
        player.sendSystemMessage(Component.literal("Global calibration cancelled.")
                .withStyle(ChatFormatting.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private static int showCalibrationStatus(ServerPlayer player) {
        if (CALIBRATION_SESSION == null) {
            player.sendSystemMessage(Component.literal(formatAllCalibrationBounds())
                    .withStyle(ChatFormatting.AQUA));
            return Command.SINGLE_SUCCESS;
        }
        CalibrationSession session = CALIBRATION_SESSION;
        player.sendSystemMessage(Component.literal(String.format(
                java.util.Locale.ROOT,
                "Calibration active: %s %s %s=%.3f. Use numeric chat input or /slime calibrate done.",
                session.orientation.commandName,
                session.corner.displayName,
                session.axis.displayName,
                session.currentValue())).withStyle(ChatFormatting.AQUA));
        return Command.SINGLE_SUCCESS;
    }

    public static boolean handleCalibrationChat(ServerPlayer player, String message) {
        CalibrationSession session = CALIBRATION_SESSION;
        if (session == null || !session.operatorId.equals(player.getUUID())) {
            return false;
        }

        String input = message.trim();
        if (input.equalsIgnoreCase("done")) {
            finishCalibrationCorner(player);
            return true;
        }
        if (input.equalsIgnoreCase("cancel")) {
            cancelCalibration(player);
            return true;
        }

        try {
            double value = Double.parseDouble(input);
            if (!Double.isFinite(value)) {
                throw new NumberFormatException();
            }
            session.setCurrentValue(value);
            if (session.axis == CalibrationAxis.X) {
                session.axis = CalibrationAxis.Z;
            } else {
                session.axis = CalibrationAxis.X;
            }
            player.sendSystemMessage(Component.literal(String.format(
                    java.util.Locale.ROOT,
                    "%s %s %s updated to %.3f. Enter the next %s value.",
                    session.orientation.commandName,
                    session.corner.displayName,
                    session.axis == CalibrationAxis.Z ? "X" : "Z",
                    value,
                    session.axis.displayName)).withStyle(ChatFormatting.AQUA));
        } catch (NumberFormatException exception) {
            player.sendSystemMessage(Component.literal(
                    "Calibration expects a numeric value, or type 'done'/'cancel'.")
                    .withStyle(ChatFormatting.YELLOW));
        }
        return true;
    }

    public static boolean isCalibrationActive() {
        return CALIBRATION_SESSION != null;
    }

    public static Vec3 calibrationPreviewOffset() {
        CalibrationSession session = CALIBRATION_SESSION;
        return session == null ? null : new Vec3(session.x, 0.0D, session.z);
    }

    public static void clearCalibrationFor(ServerPlayer player) {
        if (CALIBRATION_SESSION != null && CALIBRATION_SESSION.operatorId.equals(player.getUUID())) {
            CALIBRATION_SESSION = null;
        }
    }

    private static String formatCalibrationBounds(
            CalibrationOrientation orientation, CalibrationBounds bounds) {
        return String.format(
                java.util.Locale.ROOT,
                "%s calibration complete: minX=%.3f maxX=%.3f minZ=%.3f maxZ=%.3f | new SafeZone(%.3fD, %.3fD, %.3fD, %.3fD)",
                orientation.commandName,
                bounds.minX,
                bounds.maxX,
                bounds.minZ,
                bounds.maxZ,
                bounds.minX,
                bounds.maxX,
                bounds.minZ,
                bounds.maxZ);
    }

    private static String formatAllCalibrationBounds() {
        if (CALIBRATED_ZONES.isEmpty()) {
            return "No calibrated zones available.";
        }
        StringBuilder output = new StringBuilder("Calibrated zones:");
        for (CalibrationOrientation orientation : CalibrationOrientation.values()) {
            CalibrationBounds bounds = CALIBRATED_ZONES.get(orientation);
            if (bounds != null) {
                output.append(" ").append(formatCalibrationBounds(orientation, bounds));
            }
        }
        return output.toString();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> itemDebugHandCommands(String hand) {
        return Commands.literal(hand)
                .then(Commands.literal("x")
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                .executes(context -> executeClientCommand(context,
                                        player -> setItemDebugOffset(player, hand, 'x',
                                                DoubleArgumentType.getDouble(context, "value"))))))
                .then(Commands.literal("y")
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                .executes(context -> executeClientCommand(context,
                                        player -> setItemDebugOffset(player, hand, 'y',
                                                DoubleArgumentType.getDouble(context, "value"))))))
                .then(Commands.literal("z")
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                .executes(context -> executeClientCommand(context,
                                        player -> setItemDebugOffset(player, hand, 'z',
                                                DoubleArgumentType.getDouble(context, "value"))))));
    }

    private static int setItemDebugOffset(ServerPlayer player, String hand, char axis, double value) {
        switch (axis) {
            case 'x' -> setItemDebugOffsetX(hand, value);
            case 'y' -> setItemDebugOffsetY(hand, value);
            case 'z' -> setItemDebugOffsetZ(hand, value);
            default -> throw new IllegalArgumentException("Unknown item debug axis: " + axis);
        }
        SlimeFormVisuals.refreshItemDisplays(player);
        player.sendSystemMessage(Component.literal(String.format(
                java.util.Locale.ROOT,
                "%s item display %s offset set to %.3f.",
                hand,
                axis,
                value)).withStyle(ChatFormatting.AQUA));
        return Command.SINGLE_SUCCESS;
    }

    private static void setItemDebugOffsetX(String hand, double value) {
        SlimeFormConfig config = SlimeFormConfig.get();
        if (hand.equals("mainhand")) {
            ITEM_MAIN_HAND_OFFSET_X = value;
            config.itemMainHandOffsetX = value;
        } else {
            ITEM_OFF_HAND_OFFSET_X = value;
            config.itemOffHandOffsetX = value;
        }
        AutoConfig.getConfigHolder(SlimeFormConfig.class).save();
    }

    private static void setItemDebugOffsetY(String hand, double value) {
        SlimeFormConfig config = SlimeFormConfig.get();
        if (hand.equals("mainhand")) {
            ITEM_MAIN_HAND_OFFSET_Y = value;
            config.itemMainHandOffsetY = value;
        } else {
            ITEM_OFF_HAND_OFFSET_Y = value;
            config.itemOffHandOffsetY = value;
        }
        AutoConfig.getConfigHolder(SlimeFormConfig.class).save();
    }

    private static void setItemDebugOffsetZ(String hand, double value) {
        SlimeFormConfig config = SlimeFormConfig.get();
        if (hand.equals("mainhand")) {
            ITEM_MAIN_HAND_OFFSET_Z = value;
            config.itemMainHandOffsetZ = value;
        } else {
            ITEM_OFF_HAND_OFFSET_Z = value;
            config.itemOffHandOffsetZ = value;
        }
        AutoConfig.getConfigHolder(SlimeFormConfig.class).save();
    }

    private static int setItemDebugScale(ServerPlayer player, double value) {
        ITEM_DEBUG_SCALE = value;
        SlimeFormConfig.get().itemDisplayScale = value;
        AutoConfig.getConfigHolder(SlimeFormConfig.class).save();
        SlimeFormVisuals.refreshItemDisplays(player);
        player.sendSystemMessage(Component.literal(String.format(
                java.util.Locale.ROOT,
                "Item display size set to %.3f.",
                value)).withStyle(ChatFormatting.AQUA));
        return Command.SINGLE_SUCCESS;
    }

    private static int toggleItemDebugAxes(ServerPlayer player) {
        ITEM_DEBUG_SHOW_AXES = !ITEM_DEBUG_SHOW_AXES;
        SlimeFormConfig.get().itemDebugShowAxes = ITEM_DEBUG_SHOW_AXES;
        AutoConfig.getConfigHolder(SlimeFormConfig.class).save();
        player.sendSystemMessage(Component.literal(String.format(
                java.util.Locale.ROOT,
                "Item display local axes %s.",
                ITEM_DEBUG_SHOW_AXES ? "enabled" : "disabled")).withStyle(ChatFormatting.AQUA));
        return Command.SINGLE_SUCCESS;
    }


    private static int setItemDebugRotation(ServerPlayer player, char axis, double value) {
        SlimeFormConfig config = SlimeFormConfig.get();
        switch (axis) {
            case 'x' -> {
                ITEM_DEBUG_ROTATION_X = (float) value;
                config.itemDisplayRotationX = value;
            }
            case 'y' -> {
                ITEM_DEBUG_ROTATION_Y = (float) value;
                config.itemDisplayRotationY = value;
            }
            case 'z' -> {
                ITEM_DEBUG_ROTATION_Z = (float) value;
                config.itemDisplayRotationZ = value;
            }
            default -> throw new IllegalArgumentException("Unknown item debug rotation axis: " + axis);
        }
        AutoConfig.getConfigHolder(SlimeFormConfig.class).save();
        SlimeFormVisuals.refreshItemDisplays(player);
        player.sendSystemMessage(Component.literal(String.format(
                java.util.Locale.ROOT,
                "Item display %s rotation offset set to %.3f degrees.",
                axis,
                value)).withStyle(ChatFormatting.AQUA));
        return Command.SINGLE_SUCCESS;
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
    private static final Map<UUID, Boolean> SLEEPING_PREVIOUS_INVISIBILITY = new HashMap<>();
    private static final Set<UUID> CLIENT_COMPANION_PLAYERS = new HashSet<>();

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
        SlimeFormVisuals.restoreHiddenInventory(player);
        Boolean previousInvisibility = DORMANT_PREVIOUS_INVISIBILITY.remove(player.getUUID());
        player.setInvisible(previousInvisibility != null && previousInvisibility);
        player.displayClientMessage(Component.literal("Dormant mode ended.").withStyle(ChatFormatting.GREEN), true);
        ACTIVITY_TICKS.put(player.getUUID(), player.level().getGameTime());
        ACTIVITY_POSITIONS.put(player.getUUID(), player.position());
    }

    private static void tickDormantPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            updateSleepingVisibility(player);
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
            SlimeFormConfig config = SlimeFormConfig.get();
            long inactivityLimit = config.effectiveAfkInactivitySeconds() * 20L;
            if (config.afkDormantEnabled
                    && config.afkDormantDebug
                    && now - last < inactivityLimit
                    && now % 20L == 0L) {
                int secondsRemaining = (int) Math.ceil((inactivityLimit - (now - last)) / 20.0D);
                player.displayClientMessage(
                        Component.literal("Dormant in ").withStyle(ChatFormatting.AQUA)
                                .append(Component.literal(secondsRemaining + "s")
                                        .withStyle(ChatFormatting.YELLOW)),
                        true);
            }
            if (config.afkDormantEnabled && now - last >= inactivityLimit) {
                String blockReason = dormantEntryBlockReason(player, now);
                if (blockReason == null) {
                    enterDormant(player);
                } else if (config.afkDormantDebug && now % 20L == 0L) {
                    player.displayClientMessage(
                            Component.literal("Dormant paused: ").withStyle(ChatFormatting.YELLOW)
                                    .append(Component.literal(blockReason).withStyle(ChatFormatting.GRAY)),
                            true);
                }
            }
            SlimeFormVisuals.tick(player);
        }
    }

    private static void updateSleepingVisibility(ServerPlayer player) {
        boolean shouldHide = SlimeFormState.isActive(player)
                && player.isSleeping();
        if (shouldHide) {
            SLEEPING_PREVIOUS_INVISIBILITY.putIfAbsent(player.getUUID(), player.isInvisible());
            player.setInvisible(true);
        } else {
            restoreSleepingVisibility(player);
        }
    }

    private static void restoreSleepingVisibility(ServerPlayer player) {
        Boolean previous = SLEEPING_PREVIOUS_INVISIBILITY.remove(player.getUUID());
        if (previous != null) {
            player.setInvisible(previous);
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
        return SlimeFormState.getRiderSize(player);
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

    public static String createRecoveryLineageId(ServerPlayer player) {
        return player.getUUID() + "_recovery_" + UUID.randomUUID();
    }

    public static boolean hasRecoveryLineage(Slime slime) {
        return getRecoveryLineage(slime) != null;
    }

    public static String getRecoveryLineage(Slime slime) {
        return ((SlimeRecoveryLineage) slime).slimeform$getRecoveryLineage();
    }

    public static UUID getRecoveryParent(Slime slime) {
        return ((SlimeRecoveryLineage) slime).slimeform$getRecoveryParent();
    }

    public static int getRecoveryGeneration(Slime slime) {
        return ((SlimeRecoveryLineage) slime).slimeform$getRecoveryGeneration();
    }

    public static String getRecoveryDebugLabel(Slime slime) {
        String lineageId = getRecoveryLineage(slime);
        if (lineageId == null) {
            return null;
        }
        String compactId = lineageId.replace("-", "");
        int recoverySeparator = compactId.indexOf("_recovery_");
        if (recoverySeparator >= 0) {
            compactId = compactId.substring(recoverySeparator + "_recovery_".length());
        }
        compactId = compactId.substring(0, Math.min(4, compactId.length())).toUpperCase(java.util.Locale.ROOT);
        return "Recovery " + compactId + " · G" + getRecoveryGeneration(slime);
    }

    private static void tickRecoveryDebugNames(MinecraftServer server) {
        boolean debug = SlimeFormConfig.get().recoveryLineageDebug;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof Slime slime) || !hasRecoveryLineage(slime)) {
                    continue;
                }

                if (debug) {
                    slime.addTag(RECOVERY_DEBUG_NAME_TAG);
                    slime.setCustomName(Component.literal(getRecoveryDebugLabel(slime)));
                    slime.setCustomNameVisible(true);
                } else if (slime.getTags().contains(RECOVERY_DEBUG_NAME_TAG)) {
                    slime.removeTag(RECOVERY_DEBUG_NAME_TAG);
                    slime.setCustomName(null);
                    slime.setCustomNameVisible(false);
                }
            }
        }
    }

    public static void assignRecoveryLineage(
            Slime slime, String lineageId, UUID parentId, int generation) {
        ((SlimeRecoveryLineage) slime).slimeform$setRecoveryLineage(lineageId, parentId, generation);
        trackRecoveryLineageEntity(slime);
        LOGGER.info(
                "[slimeform] Recovery slime created uuid={} size={} lineage={} parent={} generation={}",
                slime.getUUID(),
                slime.getSize(),
                lineageId,
                parentId,
                generation);
    }

    public static boolean assignRecoveryLineage(Slime parent, Slime child) {
        String lineageId = getRecoveryLineage(parent);
        if (lineageId == null) {
            return false;
        }
        assignRecoveryLineage(
                child,
                lineageId,
                parent.getUUID(),
                getRecoveryGeneration(parent) + 1);
        return true;
    }

    public static void trackRecoveryLineageEntity(Slime slime) {
        String lineageId = getRecoveryLineage(slime);
        if (lineageId == null) {
            return;
        }
        for (Recovery recovery : RECOVERIES.values()) {
            if (lineageId.equals(recovery.lineageId())) {
                recovery.lineageEntityIds().add(slime.getUUID());
            }
        }
    }

    public static void beginRecovery(
            ServerPlayer player,
            List<Slime> splitSlimes,
            GameType previousGameMode,
            LivingEntity originalKiller) {
        if (splitSlimes.isEmpty()) {
            return;
        }

        InventorySnapshot inventory = captureAndClearInventory(player);
        RECOVERIES.put(player.getUUID(), new Recovery(
                getRecoveryLineage(splitSlimes.get(0)),
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
        Recovery recovery = RECOVERIES.get(player.getUUID());
        if (recovery != null) {
            splitSlimes.forEach(slime -> recovery.lineageEntityIds().add(slime.getUUID()));
            commandRecoverySlimesToAttackRecordedKiller(player.level().getServer(), recovery, splitSlimes);
        }
        LOGGER.info("[slimeform] Recovery started for {} ({} ticks) at {} ({}, {}, {})",
                player.getName().getString(),
                getSplitDurationTicks(),
                player.level().dimension().identifier(),
                player.getX(),
                player.getY(),
                player.getZ());
    }

    /**
     * Re-sends the camera packet because the client can reset its camera to
     * the player while processing the death/immediate-respawn sequence.
     */
    public static void syncRecoveryCamera(ServerPlayer player, Entity target) {
        player.setCamera(target);
        player.connection.send(new ClientboundSetCameraPacket(target));
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
                if (recovery.emptyLineageChecks() == 0) {
                    recovery.incrementEmptyLineageChecks();
                    continue;
                }
                completeFailedRecovery(server, player, recovery, iterator);
                continue;
            }
            recovery.resetEmptyLineageChecks();
            continueRecoveryDefense(server, recovery, survivingSlimes);
            coordinateRecoveryAssistance(server, recovery, survivingSlimes);
            updateRecoveryFleeDangerBar(recovery, player, survivingSlimes);
            if (recovery.cameraResyncTicksRemaining > 0) {
                syncRecoveryCamera(player, survivingSlimes.get(0));
                recovery.cameraResyncTicksRemaining--;
            } else {
                player.setCamera(survivingSlimes.get(0));
            }

            recovery.recoveryElapsedTicks++;
            boolean sizeOneRecovery = survivingSlimes.stream()
                    .allMatch(slime -> slime.getSize() == SlimeFormState.MIN_SIZE);
            boolean reformCountdownComplete = false;
            if (sizeOneRecovery) {
                if (hasNearbyRecoveryHostile(survivingSlimes)) {
                    // Preserve the exact remaining countdown while danger is
                    // present. It resumes when the safety radius is clear.
                    if (recovery.recoveryElapsedTicks % RECOVERY_COUNTDOWN_INTERVAL_TICKS == 0) {
                        player.displayClientMessage(createHostileReformStatus(recovery), true);
                    }
                } else {
                    if (!recovery.sizeOneReformCountdownStarted) {
                        recovery.sizeOneReformCountdownStarted = true;
                        recovery.ticksRemaining = recovery.totalTicks;
                    }
                    recovery.ticksRemaining--;
                    if (recovery.ticksRemaining % RECOVERY_COUNTDOWN_INTERVAL_TICKS == 0
                            && recovery.ticksRemaining > 0) {
                        player.displayClientMessage(
                                createRecoveryProgressBar(recovery.ticksRemaining, recovery.totalTicks), true);
                    }
                    reformCountdownComplete = recovery.ticksRemaining <= 0;
                }
            } else {
                // Keep size 2+ recovery behavior timer-based and unchanged.
                recovery.ticksRemaining--;
                if (recovery.ticksRemaining % RECOVERY_COUNTDOWN_INTERVAL_TICKS == 0
                        && recovery.ticksRemaining > 0) {
                    player.displayClientMessage(
                            createRecoveryProgressBar(recovery.ticksRemaining, recovery.totalTicks), true);
                }
                reformCountdownComplete = recovery.ticksRemaining <= 0;
            }

            boolean maxTimeoutReached = recovery.recoveryElapsedTicks >= SIZE_ONE_RECOVERY_MAX_TICKS;
            if (!reformCountdownComplete && !(sizeOneRecovery && maxTimeoutReached)) {
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
            Vec3 destinationPosition = destination.position();
            int strongestFragmentSize = survivingSlimes.stream()
                    .mapToInt(Slime::getSize)
                    .max()
                    .orElse(SlimeFormState.MIN_SIZE);
            cleanupSplitSlimes(server, recovery);
            replacement.teleportTo(
                    recoveryLevel,
                    destinationPosition.x,
                    destinationPosition.y,
                    destinationPosition.z,
                    Set.of(),
                    recovery.yRot(),
                    recovery.xRot(),
                    false);
            replacement.setCamera(replacement);
            SlimeFormState.setSize(replacement, strongestFragmentSize + 1);
            SlimeFormState.applyHealth(replacement, true);
            restoreInventory(replacement, recovery.inventory());
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

    private static boolean hasNearbyRecoveryHostile(List<Slime> survivingSlimes) {
        if (!SlimeFormConfig.get().recoveryHostileReformBlock) {
            return false;
        }
        double safetyRadius = SlimeFormConfig.get().effectiveRecoveryReformSafetyRadius();
        for (Slime slime : survivingSlimes) {
            if (!slime.level().getEntitiesOfClass(
                    Mob.class,
                    slime.getBoundingBox().inflate(safetyRadius),
                    SlimeFormMod::isRecoveryHostile).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static boolean hasRecoveryFleeThreatNearby(Slime slime) {
        return !getRecoveryFleeThreats(slime).isEmpty();
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

    private static void updateRecoveryFleeDangerBar(
            Recovery recovery,
            ServerPlayer player,
            List<Slime> survivingSlimes) {
        ServerBossEvent bar = recovery.fleeDangerBar;
        if (!SlimeFormConfig.get().recoveryFleeDangerDebug) {
            bar.setVisible(false);
            return;
        }

        double nearestDistance = RECOVERY_FLEE_THREAT_RADIUS;
        for (Slime slime : survivingSlimes) {
            if (slime.getSize() != SlimeFormState.MIN_SIZE) {
                continue;
            }
            for (LivingEntity threat : getRecoveryFleeThreats(slime)) {
                nearestDistance = Math.min(nearestDistance, slime.distanceTo(threat));
            }
        }

        if (nearestDistance >= RECOVERY_FLEE_THREAT_RADIUS) {
            bar.setVisible(false);
            return;
        }

        double danger = 1.0D - nearestDistance / RECOVERY_FLEE_THREAT_RADIUS;
        bar.setProgress((float) Math.max(0.0D, Math.min(1.0D, danger)));
        bar.setColor(danger >= 0.66D
                ? BossEvent.BossBarColor.RED
                : danger >= 0.33D ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.GREEN);
        bar.setName(Component.literal(String.format(
                java.util.Locale.ROOT,
                "Recovery danger: %.1f blocks",
                nearestDistance)));
        bar.addPlayer(player);
        bar.setVisible(true);
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
        level.addFreshEntity(slime);
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
        cleanupSplitSlimes(server, recovery);
        ServerPlayer replacement = server.getPlayerList().respawn(
                player, false, Entity.RemovalReason.KILLED);
        if (replacement == null) {
            LOGGER.warn("[slimeform] Respawn returned no player after failed recovery for {}", player.getUUID());
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
        List<Slime> surviving = new ArrayList<>();
        String lineageId = recovery.lineageId();

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof Slime slime)) {
                    continue;
                }

                boolean matchingLineage = lineageId.equals(getRecoveryLineage(slime));
                boolean alive = slime.isAlive();
                boolean removed = slime.isRemoved();

                if (!matchingLineage) {
                    continue;
                }

                if (alive && !removed) {
                    surviving.add(slime);
                }
            }
        }
        return surviving;
    }

    private static List<Slime> getLineageSlimes(MinecraftServer server, String lineageId) {
        List<Slime> lineageSlimes = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Slime slime && lineageId.equals(getRecoveryLineage(slime))) {
                    lineageSlimes.add(slime);
                }
            }
        }
        return lineageSlimes;
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

        LivingEntity originalKiller = findValidRecordedKiller(server, recovery);
        if (originalKiller != null) {
            commandRecoverySlimesToAttackTarget(survivingSlimes, originalKiller);
            return;
        }

        recovery.setPostAvenging();
        commandRecoverySlimesToAttackHostiles(survivingSlimes);
    }

    private static void commandRecoverySlimesToAttackRecordedKiller(
            MinecraftServer server,
            Recovery recovery,
            List<Slime> survivingSlimes) {
        LivingEntity recordedKiller = findValidRecordedKiller(server, recovery);
        if (recordedKiller != null) {
            commandRecoverySlimesToAttackTarget(survivingSlimes, recordedKiller);
        }
    }

    private static LivingEntity findValidRecordedKiller(
            MinecraftServer server,
            Recovery recovery) {
        UUID originalKillerId = recovery.originalKillerId();
        if (originalKillerId == null) {
            return null;
        }

        ServerLevel level = server.getLevel(recovery.dimension());
        if (level == null) {
            return null;
        }

        Entity originalKiller = level.getEntity(originalKillerId);
        if (!(originalKiller instanceof LivingEntity living)
                || !living.isAlive()
                || living.isRemoved()
                || (living instanceof ServerPlayer player && player.isSpectator())) {
            return null;
        }
        return living;
    }

    private static void commandRecoverySlimesToAttackTarget(
            List<Slime> survivingSlimes,
            LivingEntity target) {
        for (Slime slime : survivingSlimes) {
            if (slime.getSize() > SlimeFormState.MIN_SIZE) {
                slime.setTarget(target);
            }
        }
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
            MinecraftServer server,
            Recovery recovery,
            List<Slime> survivingSlimes) {
        LivingEntity sharedTarget = recovery.postAvenging()
                ? null
                : findValidRecordedKiller(server, recovery);
        if (sharedTarget == null) {
            sharedTarget = findRecoveryHostileTarget(survivingSlimes);
        }
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

    static Path findRecoveryFleePath(Slime slime, List<LivingEntity> threats) {
        Vec3 away = Vec3.ZERO;
        for (LivingEntity threat : threats) {
            away = away.add(slime.position().subtract(threat.position()));
        }
        double baseAngle = away.horizontalDistanceSqr() < 0.0001D
                ? slime.getRandom().nextDouble() * Math.PI * 2.0D
                : Math.atan2(away.z, away.x);

        double currentSafety = minimumThreatDistanceSqr(slime.blockPosition(), threats);
        Path bestSafePath = null;
        double bestSafeRouteDistance = -1.0D;
        double bestSafeEndpointDistance = -1.0D;
        double bestSafeEndpointDisplacement = -1.0D;
        Path bestFallbackPath = null;
        double bestFallbackRouteDistance = -1.0D;
        double bestFallbackEndpointDistance = -1.0D;
        double bestFallbackEndpointDisplacement = -1.0D;
        for (int direction = 0; direction < RECOVERY_FLEE_DIRECTION_COUNT; direction++) {
            double angle = baseAngle
                    + (Math.PI * 2.0D * direction / RECOVERY_FLEE_DIRECTION_COUNT);
            for (double distance : RECOVERY_FLEE_WAYPOINT_DISTANCES) {
                BlockPos destination = BlockPos.containing(
                        slime.getX() + Math.cos(angle) * distance,
                        slime.getY(),
                        slime.getZ() + Math.sin(angle) * distance);
                Path candidate = slime.getNavigation().createPath(destination, 0);
                if (candidate == null || !candidate.canReach()) {
                    continue;
                }
                if (candidate.getNodeCount() < 2) {
                    continue;
                }

                BlockPos endpoint = candidate.getEndNode() == null
                        ? candidate.getTarget()
                        : candidate.getEndNode().asBlockPos();
                if (endpoint == null || endpoint.distSqr(destination) > 4.0D) {
                    // Navigation can return a partial path when the requested
                    // waypoint is blocked. Do not mistake a wall-adjacent
                    // partial route for a successful escape.
                    continue;
                }
                if (!isRecoveryFleePathCollisionFree(slime, candidate)) {
                    continue;
                }

                double routeSafety = minimumPathThreatDistanceSqr(candidate, threats);
                double endpointSafety = minimumThreatDistanceSqr(endpoint, threats);
                double endpointDisplacement = endpoint.distSqr(slime.blockPosition());

                if (isBetterFleePath(
                        endpointSafety,
                        routeSafety,
                        endpointDisplacement,
                        bestFallbackRouteDistance,
                        bestFallbackEndpointDistance,
                        bestFallbackEndpointDisplacement)) {
                    bestFallbackPath = candidate;
                    bestFallbackRouteDistance = routeSafety;
                    bestFallbackEndpointDistance = endpointSafety;
                    bestFallbackEndpointDisplacement = endpointDisplacement;
                }

                // Prefer paths that never bring the slime meaningfully closer
                // to a threat after it leaves its current position.
                if (routeSafety >= currentSafety - 1.0D
                        && isBetterFleePath(
                                endpointSafety,
                                routeSafety,
                                endpointDisplacement,
                                bestSafeRouteDistance,
                                bestSafeEndpointDistance,
                                bestSafeEndpointDisplacement)) {
                    bestSafePath = candidate;
                    bestSafeRouteDistance = routeSafety;
                    bestSafeEndpointDistance = endpointSafety;
                    bestSafeEndpointDisplacement = endpointDisplacement;
                }
            }
        }
        return bestSafePath != null ? bestSafePath : bestFallbackPath;
    }

    static List<LivingEntity> getRecoveryFleeThreats(Slime slime) {
        Recovery recovery = findRecovery(slime);
        if (recovery == null) {
            return List.of();
        }

        Map<UUID, LivingEntity> threats = new HashMap<>();
        for (Slime fragment : getLineageSlimes((MinecraftServer) slime.level().getServer(), recovery.lineageId())
                .stream()
                .filter(fragment -> fragment.getSize() == SlimeFormState.MIN_SIZE)
                .toList()) {
            for (Mob threat : fragment.level().getEntitiesOfClass(
                    Mob.class,
                    fragment.getBoundingBox().inflate(RECOVERY_FLEE_THREAT_RADIUS),
                    SlimeFormMod::isRecoveryHostile)) {
                threats.put(threat.getUUID(), threat);
            }

            for (LivingEntity attacker : fragment.level().getEntitiesOfClass(
                    LivingEntity.class,
                    fragment.getBoundingBox().inflate(RECOVERY_FLEE_THREAT_RADIUS),
                    entity -> entity.isAlive()
                            && entity != fragment
                            && entity instanceof Mob mob
                            && mob.getTarget() == fragment)) {
                threats.put(attacker.getUUID(), attacker);
            }

            addRecentDamageSources(fragment, threats);
            UUID originalKillerId = recovery.originalKillerId();
            if (originalKillerId != null) {
                Entity originalKiller = fragment.level().getEntity(originalKillerId);
                if (originalKiller instanceof LivingEntity killer
                        && killer.isAlive()
                        && fragment.distanceToSqr(killer)
                                <= RECOVERY_FLEE_THREAT_RADIUS * RECOVERY_FLEE_THREAT_RADIUS) {
                    threats.put(killer.getUUID(), killer);
                }
            }
        }
        return List.copyOf(threats.values());
    }

    private static void addRecentDamageSources(
            Slime fragment,
            Map<UUID, LivingEntity> threats) {
        long gameTime = fragment.level().getGameTime();
        LivingEntity mobAttacker = fragment.getLastHurtByMob();
        if (isRecentDamageSource(fragment, mobAttacker,
                gameTime - fragment.getLastHurtByMobTimestamp())) {
            threats.put(mobAttacker.getUUID(), mobAttacker);
        }

        Player playerAttacker = fragment.getLastHurtByPlayer();
        if (playerAttacker != null
                && playerAttacker.isAlive()
                && fragment.getLastHurtByPlayerMemoryTime() > 0
                && fragment.distanceToSqr(playerAttacker)
                        <= RECOVERY_FLEE_THREAT_RADIUS * RECOVERY_FLEE_THREAT_RADIUS) {
            threats.put(playerAttacker.getUUID(), playerAttacker);
        }
    }

    private static boolean isRecentDamageSource(
            Slime fragment,
            LivingEntity source,
            long ageOrMemoryTicks) {
        return source != null
                && source.isAlive()
                && source != fragment
                && ageOrMemoryTicks >= 0L
                && ageOrMemoryTicks <= RECOVERY_ACTIVE_DAMAGE_SOURCE_TICKS
                && fragment.distanceToSqr(source)
                        <= RECOVERY_FLEE_THREAT_RADIUS * RECOVERY_FLEE_THREAT_RADIUS;
    }

    static RecoveryFleePathResult getRecoveryFleePath(
            Slime slime,
            List<LivingEntity> threats,
            int localRouteVersion,
            int localWaypointIndex) {
        Recovery recovery = findRecovery(slime);
        if (recovery == null || threats.isEmpty()) {
            return new RecoveryFleePathResult(null, localRouteVersion, localWaypointIndex, false);
        }

        Set<UUID> threatIds = new HashSet<>();
        Map<UUID, BlockPos> threatPositions = new HashMap<>();
        for (LivingEntity threat : threats) {
            threatIds.add(threat.getUUID());
            threatPositions.put(threat.getUUID(), threat.blockPosition());
        }

        if (recovery.sharedFleeRoute().isEmpty()
                || !threatIds.equals(recovery.sharedFleeThreatIds())
                || !threatPositions.equals(recovery.sharedFleeThreatPositions())) {
            Slime origin = findRecoveryRouteOrigin(slime, recovery);
            Path routePath = origin == null ? null : findRecoveryFleePath(origin, threats);
            if (routePath == null) {
                recovery.clearSharedFleeRoute();
                return new RecoveryFleePathResult(null, recovery.sharedFleeRouteVersion(), 0, false);
            }
            recovery.setSharedFleeRoute(routePath, threatIds, threatPositions);
        }

        int waypointIndex = localRouteVersion == recovery.sharedFleeRouteVersion()
                ? localWaypointIndex
                : 0;
        while (waypointIndex < recovery.sharedFleeRoute().size()
                && isNearSharedFleeWaypoint(slime, recovery.sharedFleeRoute().get(waypointIndex))) {
            waypointIndex++;
        }
        if (waypointIndex >= recovery.sharedFleeRoute().size()) {
            recovery.clearSharedFleeRoute();
            return new RecoveryFleePathResult(null, recovery.sharedFleeRouteVersion(), waypointIndex, false);
        }

        BlockPos waypoint = recovery.sharedFleeRoute().get(waypointIndex);
        Path localPath = slime.getNavigation().createPath(waypoint, 0);
        if (localPath == null
                || !localPath.canReach()
                || localPath.getNodeCount() < 2
                || localPath.getEndNode() == null
                || localPath.getEndNode().asBlockPos().distSqr(waypoint) > 4.0D
            || !isRecoveryFleePathCollisionFree(slime, localPath)) {
            if (isNearSharedFleeWaypoint(slime, waypoint)) {
                return new RecoveryFleePathResult(
                        null,
                        recovery.sharedFleeRouteVersion(),
                        waypointIndex,
                        true);
            }
            return new RecoveryFleePathResult(
                    null,
                    recovery.sharedFleeRouteVersion(),
                    waypointIndex,
                    true);
        }
        return new RecoveryFleePathResult(
                localPath,
                recovery.sharedFleeRouteVersion(),
                waypointIndex,
                true);
    }

    static void clearRecoveryFleeRoute(Slime slime) {
        Recovery recovery = findRecovery(slime);
        if (recovery != null) {
            recovery.clearSharedFleeRoute();
        }
    }

    private static boolean isNearSharedFleeWaypoint(Slime slime, BlockPos waypoint) {
        return slime.distanceToSqr(
                waypoint.getX() + 0.5D,
                waypoint.getY(),
                waypoint.getZ() + 0.5D) <= 2.25D;
    }

    private static Recovery findRecovery(Slime slime) {
        String lineageId = getRecoveryLineage(slime);
        if (lineageId == null) {
            return null;
        }
        for (Recovery recovery : RECOVERIES.values()) {
            if (lineageId.equals(recovery.lineageId())) {
                return recovery;
            }
        }
        return null;
    }

    private static Slime findRecoveryRouteOrigin(Slime slime, Recovery recovery) {
        MinecraftServer server = (MinecraftServer) slime.level().getServer();
        ServerPlayer player = findRecoveryPlayer(server, recovery);
        if (player != null && player.getCamera() instanceof Slime cameraSlime
                && recovery.lineageId().equals(getRecoveryLineage(cameraSlime))) {
            return cameraSlime;
        }
        return slime;
    }

    private static ServerPlayer findRecoveryPlayer(MinecraftServer server, Recovery recovery) {
        for (Map.Entry<UUID, Recovery> entry : RECOVERIES.entrySet()) {
            if (entry.getValue() == recovery) {
                return server.getPlayerList().getPlayer(entry.getKey());
            }
        }
        return null;
    }

    private static boolean isRecoveryFleePathCollisionFree(Slime slime, Path path) {
        for (int index = 1; index < path.getNodeCount(); index++) {
            BlockPos node = path.getNodePos(index);
            AABB nodeBox = slime.getBoundingBox().move(
                    node.getX() + 0.5D - slime.getX(),
                    node.getY() - slime.getY(),
                    node.getZ() + 0.5D - slime.getZ());
            if (!slime.level().noCollision(slime, nodeBox)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBetterFleePath(
            double endpointDistance,
            double routeDistance,
            double endpointDisplacement,
            double bestRouteDistance,
            double bestEndpointDistance,
            double bestEndpointDisplacement) {
        return endpointDistance > bestEndpointDistance
                || (endpointDistance == bestEndpointDistance && routeDistance > bestRouteDistance)
                || (endpointDistance == bestEndpointDistance
                && routeDistance == bestRouteDistance
                && endpointDisplacement > bestEndpointDisplacement);
    }

    static boolean isRecoveryFleePathSafe(Slime slime, Path path, List<LivingEntity> threats) {
        if (path == null || threats.isEmpty() || path.getNodeCount() < 2) {
            return false;
        }
        double currentSafety = minimumThreatDistanceSqr(slime.blockPosition(), threats);
        return minimumPathThreatDistanceSqr(path, threats) >= currentSafety - 1.0D;
    }

    static void visualizeRecoveryFleePath(Slime slime, Path path, List<LivingEntity> threats) {
        if (!SlimeFormConfig.get().recoveryFleePathDebug
                || slime.level().isClientSide()
                || path == null
                || slime.level().getGameTime() % 4L != 0L) {
            return;
        }

        ServerLevel level = (ServerLevel) slime.level();
        ServerPlayer viewer = null;
        String lineageId = getRecoveryLineage(slime);
        Recovery recovery = findRecovery(slime);
        for (Map.Entry<UUID, Recovery> entry : RECOVERIES.entrySet()) {
            if (!entry.getValue().lineageId().equals(lineageId)) {
                continue;
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null && player.getCamera() == slime) {
                viewer = player;
            }
            break;
        }
        if (viewer == null) {
            return;
        }

        List<BlockPos> route = recovery != null && !recovery.sharedFleeRoute().isEmpty()
                ? recovery.sharedFleeRoute()
                : path == null ? List.of() : pathNodes(path);
        for (BlockPos node : route) {
            level.sendParticles(
                    viewer,
                    ParticleTypes.END_ROD,
                    false,
                    false,
                    node.getX() + 0.5D,
                    node.getY() + 0.15D,
                    node.getZ() + 0.5D,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D);
        }

        BlockPos endpoint = route.isEmpty() ? null : route.get(route.size() - 1);
        if (endpoint != null) {
            level.sendParticles(
                    viewer,
                    ParticleTypes.SOUL_FIRE_FLAME,
                    false,
                    false,
                    endpoint.getX() + 0.5D,
                    endpoint.getY() + 0.25D,
                    endpoint.getZ() + 0.5D,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D);
        }

        for (LivingEntity threat : threats) {
            level.sendParticles(
                    viewer,
                    ParticleTypes.FLAME,
                    false,
                    false,
                    threat.getX(),
                    threat.getY() + threat.getBbHeight() * 0.5D,
                    threat.getZ(),
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D);
        }
    }

    private static List<BlockPos> pathNodes(Path path) {
        List<BlockPos> nodes = new ArrayList<>();
        for (int index = 1; index < path.getNodeCount(); index++) {
            nodes.add(path.getNodePos(index));
        }
        return nodes;
    }

    private static double minimumPathThreatDistanceSqr(Path path, List<LivingEntity> threats) {
        double minimumDistance = Double.MAX_VALUE;
        for (int index = 1; index < path.getNodeCount(); index++) {
            minimumDistance = Math.min(
                    minimumDistance,
                    minimumThreatDistanceSqr(path.getNodePos(index), threats));
        }
        return minimumDistance;
    }

    private static double minimumThreatDistanceSqr(BlockPos position, List<LivingEntity> threats) {
        double minimumDistance = Double.MAX_VALUE;
        for (LivingEntity threat : threats) {
            minimumDistance = Math.min(minimumDistance, position.distSqr(threat.blockPosition()));
        }
        return minimumDistance;
    }

    static boolean isRecoveryHostile(LivingEntity entity) {
        // Slimes are not threats to recovery fragments, even though they are
        // living Enemy entities in vanilla's classification.
        return entity instanceof Enemy
                && entity instanceof Mob
                && !(entity instanceof Slime)
                && entity.isAlive();
    }

    private static void updateLastKnownSplitPosition(MinecraftServer server, Recovery recovery) {
        String lineageId = recovery.lineageId();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Slime slime && lineageId.equals(getRecoveryLineage(slime))) {
                    recovery.lastKnownPosition = slime.position();
                }
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

    private static Component createHostileReformStatus(Recovery recovery) {
        int remainingTicks = Math.max(0, SIZE_ONE_RECOVERY_MAX_TICKS - recovery.recoveryElapsedTicks);
        int remainingSeconds = (int) Math.ceil(
                remainingTicks / (double) RECOVERY_COUNTDOWN_INTERVAL_TICKS);
        int minutes = remainingSeconds / 60;
        int seconds = remainingSeconds % 60;
        return Component.literal("Reforming: [Hostile Mob Nearby]\n")
                .withStyle(ChatFormatting.RED)
                .append(Component.literal(String.format(
                        java.util.Locale.ROOT,
                        "Time to force reform: %dm %02ds",
                        minutes,
                        seconds)).withStyle(ChatFormatting.YELLOW));
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
        recovery.clearFleeDangerBar();
        int removed = 0;
        String lineageId = recovery.lineageId();
        List<UUID> lineageEntityIds = List.copyOf(recovery.lineageEntityIds());
        List<UUID> assistedSlimeIds = List.copyOf(recovery.assistedSlimeIds());

        // UUIDs are only a fallback for entities that may no longer be returned
        // by the normal entity scan. Entity-owned lineage remains authoritative.
        for (ServerLevel level : server.getAllLevels()) {
            for (UUID lineageEntityId : lineageEntityIds) {
                Entity entity = level.getEntity(lineageEntityId);
                if (entity instanceof Slime slime && !slime.isRemoved()) {
                    slime.discard();
                    removed++;
                }
            }
            for (UUID assistedId : assistedSlimeIds) {
                Entity entity = level.getEntity(assistedId);
                if (entity instanceof Slime slime && !slime.isRemoved()) {
                    slime.setTarget(null);
                }
            }
        }

        // Snapshot before discard because removing entities can mutate the
        // level's entity collections during iteration.
        List<Slime> lineageSlimes = getLineageSlimes(server, lineageId);
        for (Slime slime : lineageSlimes) {
            if (!slime.isRemoved()) {
                slime.discard();
                removed++;
            }
        }
        recovery.assistedSlimeIds().clear();
        recovery.lineageEntityIds().clear();
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
        private final String lineageId;
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
        private int recoveryElapsedTicks;
        private int cameraResyncTicksRemaining = 10;
        private boolean sizeOneReformCountdownStarted;
        private final InventorySnapshot inventory;
        private Vec3 lastKnownPosition;
        private boolean postAvenging;
        private final Set<UUID> assistedSlimeIds = new HashSet<>();
        private final Set<UUID> lineageEntityIds = new HashSet<>();
        private List<BlockPos> sharedFleeRoute = List.of();
        private Set<UUID> sharedFleeThreatIds = Set.of();
        private Map<UUID, BlockPos> sharedFleeThreatPositions = Map.of();
        private int sharedFleeRouteVersion;
        private int emptyLineageChecks;
        private final ServerBossEvent fleeDangerBar;

        private Recovery(String lineageId,
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
            this.lineageId = lineageId;
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
            this.fleeDangerBar = new ServerBossEvent(
                    Component.literal("Recovery danger"),
                    BossEvent.BossBarColor.GREEN,
                    BossEvent.BossBarOverlay.PROGRESS);
            this.fleeDangerBar.setVisible(false);
        }

        private String lineageId() {
            return lineageId;
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

        private Set<UUID> lineageEntityIds() {
            return lineageEntityIds;
        }

        private List<BlockPos> sharedFleeRoute() {
            return sharedFleeRoute;
        }

        private Set<UUID> sharedFleeThreatIds() {
            return sharedFleeThreatIds;
        }

        private Map<UUID, BlockPos> sharedFleeThreatPositions() {
            return sharedFleeThreatPositions;
        }

        private int sharedFleeRouteVersion() {
            return sharedFleeRouteVersion;
        }

        private void setSharedFleeRoute(
                Path path,
                Set<UUID> threatIds,
                Map<UUID, BlockPos> threatPositions) {
            List<BlockPos> waypoints = new ArrayList<>();
            for (int index = 1; index < path.getNodeCount(); index++) {
                waypoints.add(path.getNodePos(index));
            }
            sharedFleeRoute = List.copyOf(waypoints);
            sharedFleeThreatIds = Set.copyOf(threatIds);
            sharedFleeThreatPositions = Map.copyOf(threatPositions);
            sharedFleeRouteVersion++;
        }

        private void clearSharedFleeRoute() {
            sharedFleeRoute = List.of();
            sharedFleeThreatIds = Set.of();
            sharedFleeThreatPositions = Map.of();
            sharedFleeRouteVersion++;
        }

        private int emptyLineageChecks() {
            return emptyLineageChecks;
        }

        private void incrementEmptyLineageChecks() {
            emptyLineageChecks++;
        }

        private void resetEmptyLineageChecks() {
            emptyLineageChecks = 0;
        }

        private void clearFleeDangerBar() {
            fleeDangerBar.removeAllPlayers();
            fleeDangerBar.setVisible(false);
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

    static record RecoveryFleePathResult(
            Path path,
            int routeVersion,
            int waypointIndex,
            boolean routeActive) {
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
