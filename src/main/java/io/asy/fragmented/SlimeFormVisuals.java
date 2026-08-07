package io.asy.fragmented;

import com.mojang.math.Transformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Display.ItemDisplay;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative, non-gameplay slime representations. */
public final class SlimeFormVisuals {
    private static final String VISUAL_PREFIX = "slimeform.visual.";
    private static final String SLEEPING_SUFFIX = ".sleeping";
    private static final String DORMANT_SUFFIX = ".dormant";
    private static final Map<UUID, UUID> DORMANT_SLIMES = new HashMap<>();
    private static final Map<UUID, ItemDisplaySession> ITEM_DISPLAY_SESSIONS = new HashMap<>();
    private static final Map<UUID, ServerPlayer> PENDING_DORMANT_REMOVALS = new HashMap<>();
    private static final Map<UUID, Long> AMBIENT_PARTICLE_TICKS = new HashMap<>();
    private static final double ITEM_DEBUG_AXIS_MARKER_DISTANCE = 0.55D;
    private static final double MIN_HAND_SEPARATION = 0.12D;
    private static final int MAX_HAND_POSITION_ATTEMPTS = 24;

    private enum DisplaySlot {
        MAINHAND("mainhand"),
        OFFHAND("offhand"),
        HEAD("head"),
        CHEST("chest"),
        LEGS("legs"),
        FEET("feet");

        private final String tagName;

        DisplaySlot(String tagName) {
            this.tagName = tagName;
        }

        private boolean isHand() {
            return this == MAINHAND || this == OFFHAND;
        }
    }

    private record SafeZone(double minX, double maxX, double minZ, double maxZ) {
    }

    private enum VisualMode {
        SLEEPING,
        DORMANT
    }

    private static final class ItemDisplaySession {
        private final VisualMode mode;
        private final ServerLevel level;
        private final UUID slimeId;
        private final long createdGameTime;
        private final Map<DisplaySlot, UUID> displayIds = new EnumMap<>(DisplaySlot.class);
        private final Map<DisplaySlot, Vec3> offsets = new EnumMap<>(DisplaySlot.class);
        private final Map<DisplaySlot, ItemStack> snapshots = new EnumMap<>(DisplaySlot.class);

        private ItemDisplaySession(VisualMode mode, ServerLevel level, UUID slimeId) {
            this.mode = mode;
            this.level = level;
            this.slimeId = slimeId;
            this.createdGameTime = level.getGameTime();
        }
    }

    private SlimeFormVisuals() {
    }

    public static void tick(ServerPlayer player) {
        boolean sleeping = SlimeFormState.isActive(player) && player.isSleeping();
        boolean dormant = player.getTags().contains(SlimeFormMod.SLIME_DORMANT_TAG);
        if (SlimeFormState.isActive(player) && !sleeping && !dormant) {
            tickAmbientParticles(player);
        } else if (!SlimeFormState.isActive(player)) {
            AMBIENT_PARTICLE_TICKS.remove(player.getUUID());
        }
        if (sleeping || dormant) {
            VisualMode mode = dormant ? VisualMode.DORMANT : VisualMode.SLEEPING;
            String tag = visualTag(player, dormant);
            ServerLevel level = player.level();
            Slime slime = dormant ? findDormant(level, player, tag) : find(level, player, tag).stream().findFirst().orElse(null);
            if (slime == null) {
                slime = create(level, player, tag, dormant);
            }
            if (slime != null) {
                if (!dormant) {
                    Vec3 position = sleeping
                            ? sleepingSlimePosition(player)
                            : player.position();
                    slime.setPos(position.x, position.y, position.z);
                    float rotation = sleeping ? sleepingSlimeRotation(player) : player.getYRot();
                    slime.setYRot(rotation);
                    slime.setYHeadRot(rotation);
                    slime.yRotO = rotation;
                    slime.yHeadRotO = rotation;
                    slime.setDeltaMovement(0.0D, 0.0D, 0.0D);
                } else if (player.getVehicle() != slime && !player.isPassenger()) {
                    player.startRiding(slime, true, true);
                }
                if (!SlimeFormConfig.get().floatingItemDisplays) {
                    removeItemDisplays(player);
                } else {
                    ItemDisplaySession session = ITEM_DISPLAY_SESSIONS.get(player.getUUID());
                    if (session == null
                            || session.mode != mode
                            || session.level != player.level()
                            || !session.slimeId.equals(slime.getUUID())) {
                        removeItemDisplays(player);
                        session = createItemDisplaySession(
                                player, player.level(), slime, mode);
                        if (session != null) {
                            ITEM_DISPLAY_SESSIONS.put(player.getUUID(), session);
                        }
                    }
                    if (session != null) {
                        syncItemDisplays(player, slime, session);
                    }
                }
            }
        } else {
            remove(player, false);
            remove(player, true);
        }
    }

    private static void tickAmbientParticles(ServerPlayer player) {
        long now = player.level().getGameTime();
        long last = AMBIENT_PARTICLE_TICKS.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        boolean moving = player.getDeltaMovement().horizontalDistanceSqr() > 0.0001D;
        long interval = moving ? 4L : 20L;
        if (now - last < interval) {
            return;
        }
        AMBIENT_PARTICLE_TICKS.put(player.getUUID(), now);
        ServerLevel level = player.level();
        level.sendParticles(
                ParticleTypes.ITEM_SLIME,
                player.getX(),
                player.getY() + 0.15D,
                player.getZ(),
                moving ? 2 : 1,
                0.22D,
                0.08D,
                0.22D,
                0.01D);
    }

    private static Vec3 sleepingSlimePosition(ServerPlayer player) {
        BlockPos bedPos = player.getSleepingPos().orElse(null);
        Direction bedFacing = player.getBedOrientation();
        if (bedPos == null || bedFacing == null || !bedFacing.getAxis().isHorizontal()) {
            return player.position();
        }

        return new Vec3(
                bedPos.getX() + 0.5D - bedFacing.getStepX() * 0.5D,
                bedPos.getY() + 0.55D,
                bedPos.getZ() + 0.5D - bedFacing.getStepZ() * 0.5D);
    }

    private static float sleepingSlimeRotation(ServerPlayer player) {
        Direction bedFacing = player.getBedOrientation();
        return bedFacing != null && bedFacing.getAxis().isHorizontal()
                ? bedFacing.toYRot()
                : player.getYRot();
    }

    public static void remove(ServerPlayer player, boolean dormant) {
        String tag = visualTag(player, dormant);
        Slime tracked = dormant ? trackedDormant(player) : null;
        Set<UUID> removed = new HashSet<>();
        if (tracked != null && tracked.isAlive() && !tracked.isRemoved()
                && removed.add(tracked.getUUID())) {
            tracked.discard();
        }
        for (Slime slime : find(player.level(), player, tag)) {
            if (slime.isAlive() && !slime.isRemoved() && removed.add(slime.getUUID())) {
                slime.discard();
            }
        }
        removeItemDisplays(player);
        if (dormant) {
            DORMANT_SLIMES.remove(player.getUUID());
        }
        AMBIENT_PARTICLE_TICKS.remove(player.getUUID());
    }

    public static void refreshItemDisplays(ServerPlayer player) {
        boolean dormant = player.getTags().contains(SlimeFormMod.SLIME_DORMANT_TAG);
        boolean sleeping = SlimeFormState.isActive(player) && player.isSleeping();
        if (!dormant && !sleeping) {
            return;
        }

        String tag = visualTag(player, dormant);
        Slime slime = dormant
                ? findDormant(player.level(), player, tag)
                : find(player.level(), player, tag).stream().findFirst().orElse(null);
        ItemDisplaySession session = ITEM_DISPLAY_SESSIONS.get(player.getUUID());
        if (slime != null && session != null) {
            syncItemDisplays(player, slime, session);
        }
    }

    public static void queueDormantRemoval(ServerPlayer player) {
        PENDING_DORMANT_REMOVALS.put(player.getUUID(), player);
    }

    public static void processPendingRemovals(MinecraftServer server) {
        if (!server.isSameThread()) {
            server.execute(() -> processPendingRemovals(server));
            return;
        }

        if (PENDING_DORMANT_REMOVALS.isEmpty()) {
            return;
        }

        List<ServerPlayer> pending = List.copyOf(PENDING_DORMANT_REMOVALS.values());
        PENDING_DORMANT_REMOVALS.clear();
        for (ServerPlayer player : pending) {
            remove(player, true);
        }
    }

    private static List<Slime> find(ServerLevel level, ServerPlayer player, String tag) {
        return level.getEntitiesOfClass(
                Slime.class,
                new AABB(player.blockPosition()).inflate(2.5D),
                slime -> slime.getTags().contains(tag));
    }

    private static Slime findDormant(ServerLevel level, ServerPlayer player, String tag) {
        Slime tracked = trackedDormant(player);
        if (tracked != null && tracked.isAlive() && tracked.getTags().contains(tag)) {
            return tracked;
        }
        if (player.getVehicle() instanceof Slime vehicle && vehicle.getTags().contains(tag)) {
            DORMANT_SLIMES.put(player.getUUID(), vehicle.getUUID());
            return vehicle;
        }
        return find(level, player, tag).stream().findFirst().orElse(null);
    }

    private static Slime trackedDormant(ServerPlayer player) {
        UUID slimeId = DORMANT_SLIMES.get(player.getUUID());
        if (slimeId == null) {
            return null;
        }
        if (player.level().getEntity(slimeId) instanceof Slime slime && !slime.isRemoved()) {
            return slime;
        }
        DORMANT_SLIMES.remove(player.getUUID());
        return null;
    }

    private static Slime create(ServerLevel level, ServerPlayer player, String tag, boolean dormant) {
        Slime slime = EntityType.SLIME.create(level, EntitySpawnReason.TRIGGERED);
        if (slime == null) {
            return null;
        }
        slime.setSize(1, false);
        slime.addTag(tag);
        slime.setInvulnerable(true);
        slime.setNoAi(!dormant);
        slime.setNoGravity(!dormant);
        slime.setSilent(true);
        slime.setPersistenceRequired();
        slime.setPos(player.getX(), player.getY(), player.getZ());
        level.addFreshEntity(slime);
        if (dormant) {
            DORMANT_SLIMES.put(player.getUUID(), slime.getUUID());
        }
        return slime;
    }

    private static void syncItemDisplays(
            ServerPlayer player, Slime slime, ItemDisplaySession session) {
        if (!SlimeFormConfig.get().floatingItemDisplays) {
            removeItemDisplays(player);
            return;
        }

        if (session.level != slime.level() || !session.slimeId.equals(slime.getUUID())) {
            invalidateItemDisplaySession(player, session);
            return;
        }

        long time = slime.level().getGameTime();
        if (time == session.createdGameTime) {
            return;
        }

        if (!hasAllExpectedDisplays((ServerLevel) slime.level(), session)) {
            invalidateItemDisplaySession(player, session);
            return;
        }

        if (session.displayIds.isEmpty()) {
            return;
        }

        double topY = slime.getY() + slime.getBbHeight();
        if (SlimeFormConfig.get().itemDebugShowAxes && time % 5L == 0L) {
            sendItemDebugAxisMarkers(slime, topY);
        }
        for (DisplaySlot slot : DisplaySlot.values()) {
            UUID displayId = session.displayIds.get(slot);
            if (displayId == null) {
                continue;
            }
            if (!(slime.level().getEntity(displayId) instanceof ItemDisplay display)
                    || display.isRemoved()) {
                invalidateItemDisplaySession(player, session);
                return;
            }

            boolean calibrationProbe = SlimeFormMod.isCalibrationActive() && slot == DisplaySlot.MAINHAND;
            display.setInvisible(SlimeFormMod.isCalibrationActive() && slot != DisplaySlot.MAINHAND);
            double phase = slot.ordinal() * Math.PI / 3.0D;
            double bob = SlimeFormMod.isCalibrationActive()
                    ? 0.0D
                    : Math.sin((time * Math.PI * 2.0D / SlimeFormMod.ITEM_DISPLAY_BOB_PERIOD_TICKS) + phase)
                            * SlimeFormConfig.get().effectiveItemDisplayBobAmplitude();
            Vec3 anchor = calibrationProbe
                    ? SlimeFormMod.calibrationPreviewOffset()
                    : session.offsets.get(slot);
            if (anchor == null) {
                continue;
            }
            Vec3 localOffset = slimeLocalHorizontalOffset(
                    slime,
                    anchor.x + debugOffsetX(slot),
                    anchor.z + debugOffsetZ(slot));
            display.setPos(
                    slime.getX() + localOffset.x,
                    topY + debugOffsetY(slot) + bob,
                    slime.getZ() + localOffset.z);
            display.setYRot(slime.getYRot());
            ((SlimeDisplayAccess) display).slimeform$setTransformation(new Transformation(
                    new Vector3f(),
                    new Quaternionf()
                            .rotateX((float) Math.toRadians(SlimeFormConfig.get().effectiveItemDisplayRotationX()))
                            .rotateY((float) Math.toRadians(SlimeFormConfig.get().effectiveItemDisplayRotationY()))
                            .rotateZ((float) Math.toRadians(SlimeFormConfig.get().effectiveItemDisplayRotationZ())),
                    new Vector3f((float) SlimeFormConfig.get().effectiveItemDisplayScale()),
                    new Quaternionf()));
        }
    }

    private static Vec3 slimeLocalHorizontalOffset(Slime slime, double localX, double localZ) {
        double yaw = Math.toRadians(slime.getYRot());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        return new Vec3(
                localX * cos + localZ * sin,
                0.0D,
                localZ * cos - localX * sin);
    }

    private static void sendItemDebugAxisMarkers(Slime slime, double topY) {
        ServerLevel level = (ServerLevel) slime.level();
        sendItemDebugAxisMarker(level, slime, topY, ITEM_DEBUG_AXIS_MARKER_DISTANCE, 0.0D, ParticleTypes.FLAME);
        sendItemDebugAxisMarker(level, slime, topY, -ITEM_DEBUG_AXIS_MARKER_DISTANCE, 0.0D, ParticleTypes.SOUL_FIRE_FLAME);
        sendItemDebugAxisMarker(level, slime, topY, 0.0D, ITEM_DEBUG_AXIS_MARKER_DISTANCE, ParticleTypes.HAPPY_VILLAGER);
        sendItemDebugAxisMarker(level, slime, topY, 0.0D, -ITEM_DEBUG_AXIS_MARKER_DISTANCE, ParticleTypes.END_ROD);
    }

    private static void sendItemDebugAxisMarker(
            ServerLevel level,
            Slime slime,
            double topY,
            double localX,
            double localZ,
            ParticleOptions particle) {
        Vec3 offset = slimeLocalHorizontalOffset(slime, localX, localZ);
        level.sendParticles(
                particle,
                slime.getX() + offset.x,
                topY,
                slime.getZ() + offset.z,
                1,
                0.0D,
                0.0D,
                0.0D,
                0.0D);
    }

    private static ItemDisplaySession createItemDisplaySession(
            ServerPlayer player, ServerLevel level, Slime slime, VisualMode mode) {
        ItemDisplaySession session = new ItemDisplaySession(mode, level, slime.getUUID());
        Map<DisplaySlot, ItemStack> snapshots = session.snapshots;
        for (DisplaySlot slot : DisplaySlot.values()) {
            ItemStack snapshot = itemFor(player, slot).copy();
            if (!snapshot.isEmpty()) {
                snapshots.put(slot, snapshot);
            }
        }
        session.offsets.putAll(createRandomOffsets(level, slime.getYRot(), snapshots));
        for (DisplaySlot slot : DisplaySlot.values()) {
            ItemStack snapshot = snapshots.get(slot);
            if (snapshot != null) {
                createItemDisplay(player, level, slime, snapshot, slot,
                        session.offsets.getOrDefault(slot, Vec3.ZERO), session);
            }
        }
        return session;
    }

    private static boolean hasAllExpectedDisplays(
            ServerLevel level, ItemDisplaySession session) {
        for (DisplaySlot slot : DisplaySlot.values()) {
            if (!session.snapshots.containsKey(slot)) {
                continue;
            }
            UUID displayId = session.displayIds.get(slot);
            if (!(level.getEntity(displayId) instanceof ItemDisplay display)
                    || display.isRemoved()) {
                return false;
            }
        }
        return true;
    }

    private static Map<DisplaySlot, Vec3> createRandomOffsets(
            ServerLevel level, float slimeYaw, Map<DisplaySlot, ItemStack> snapshots) {
        Map<DisplaySlot, Vec3> offsets = new EnumMap<>(DisplaySlot.class);
        boolean northSouth = isNorthSouthAlignment(slimeYaw);
        SlimeFormMod.CalibrationBounds calibrated = SlimeFormMod.calibrationZone(northSouth);
        if (calibrated == null) {
            return offsets;
        }
        SafeZone safeZone = new SafeZone(
                calibrated.minX(), calibrated.maxX(), calibrated.minZ(), calibrated.maxZ());
        for (DisplaySlot slot : DisplaySlot.values()) {
            if (!snapshots.containsKey(slot)) {
                continue;
            }
            Vec3 offset = randomOffset(level, safeZone);
            int attempts = 0;
            while (!isSeparated(slot, offset, offsets)
                    && attempts++ < MAX_HAND_POSITION_ATTEMPTS) {
                offset = randomOffset(level, safeZone);
            }
            if (!isSeparated(slot, offset, offsets)) {
                offset = mostSeparatedOffset(safeZone, offsets.values());
            }
            offsets.put(slot, offset);
        }
        return offsets;
    }

    private static boolean isSeparated(DisplaySlot slot, Vec3 candidate, Map<DisplaySlot, Vec3> offsets) {
        for (Map.Entry<DisplaySlot, Vec3> entry : offsets.entrySet()) {
            double minimum = slot.isHand() && entry.getKey().isHand() ? MIN_HAND_SEPARATION : 0.06D;
            if (horizontalDistance(candidate, entry.getValue()) < minimum) {
                return false;
            }
        }
        return true;
    }

    private static Vec3 randomOffset(ServerLevel level, SafeZone safeZone) {
        return new Vec3(
                randomBetween(level, safeZone.minX(), safeZone.maxX()),
                0.0D,
                randomBetween(level, safeZone.minZ(), safeZone.maxZ()));
    }

    private static Vec3 mostSeparatedOffset(SafeZone safeZone, Iterable<Vec3> references) {
        Vec3[] candidates = {
                new Vec3(safeZone.minX(), 0.0D, safeZone.minZ()),
                new Vec3(safeZone.minX(), 0.0D, safeZone.maxZ()),
                new Vec3(safeZone.maxX(), 0.0D, safeZone.minZ()),
                new Vec3(safeZone.maxX(), 0.0D, safeZone.maxZ()),
                new Vec3((safeZone.minX() + safeZone.maxX()) / 2.0D, 0.0D,
                        (safeZone.minZ() + safeZone.maxZ()) / 2.0D)
        };
        Vec3 farthest = candidates[0];
        double farthestDistance = minimumDistance(farthest, references);
        for (int index = 1; index < candidates.length; index++) {
            double distance = minimumDistance(candidates[index], references);
            if (distance > farthestDistance) {
                farthest = candidates[index];
                farthestDistance = distance;
            }
        }
        return farthest;
    }

    private static double minimumDistance(Vec3 candidate, Iterable<Vec3> references) {
        double minimum = Double.MAX_VALUE;
        for (Vec3 reference : references) {
            minimum = Math.min(minimum, horizontalDistance(candidate, reference));
        }
        return minimum;
    }

    private static double randomBetween(ServerLevel level, double first, double second) {
        return first + level.random.nextDouble() * (second - first);
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        return Math.sqrt(first.distanceToSqr(second));
    }

    private static boolean isNorthSouthAlignment(float yaw) {
        double radians = Math.toRadians(yaw);
        return Math.abs(Math.cos(radians)) >= Math.abs(Math.sin(radians));
    }

    private static void createItemDisplay(
            ServerPlayer player,
            ServerLevel level,
            Slime slime,
            ItemStack stack,
            DisplaySlot slot,
            Vec3 offset,
            ItemDisplaySession session) {
        ItemDisplay display = EntityType.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (display == null) {
            return;
        }
        display.addTag(itemDisplayTag(player, slot.tagName));
        display.setInvulnerable(true);
        display.getSlot(0).set(stack.copy());
        ((SlimeItemDisplayAccess) display).slimeform$setItemTransform(ItemDisplayContext.GROUND);
        display.setPos(slime.getX(), slime.getY() + slime.getBbHeight(), slime.getZ());
        level.addFreshEntity(display);
        session.displayIds.put(slot, display.getUUID());
    }

    private static void removeItemDisplays(ServerPlayer player) {
        Set<UUID> removed = new HashSet<>();
        ItemDisplaySession session = ITEM_DISPLAY_SESSIONS.remove(player.getUUID());
        if (session != null) {
            for (UUID displayId : session.displayIds.values()) {
                if (session.level.getEntity(displayId) instanceof ItemDisplay display
                        && removed.add(display.getUUID())) {
                    display.discard();
                }
            }
            discardTaggedItemDisplays(session.level, player, removed);
        }

        discardTaggedItemDisplays(player.level(), player, removed);
    }

    private static void discardTaggedItemDisplays(
            ServerLevel level, ServerPlayer player, Set<UUID> removed) {
        for (ItemDisplay display : level.getEntitiesOfClass(
                ItemDisplay.class,
                new AABB(player.blockPosition()).inflate(16.0D),
                display -> display.getTags().stream().anyMatch(tag -> tag.startsWith(itemDisplayPrefix(player))))) {
            if (removed.add(display.getUUID())) {
                display.discard();
            }
        }
    }

    private static void invalidateItemDisplaySession(
            ServerPlayer player, ItemDisplaySession session) {
        if (ITEM_DISPLAY_SESSIONS.get(player.getUUID()) == session) {
            removeItemDisplays(player);
        }
    }

    private static String itemDisplayPrefix(ServerPlayer player) {
        return VISUAL_PREFIX + player.getUUID() + ".item.";
    }

    private static String itemDisplayTag(ServerPlayer player, String hand) {
        return itemDisplayPrefix(player) + hand;
    }

    private static double debugOffsetX(DisplaySlot slot) {
        return slot == DisplaySlot.MAINHAND
                ? SlimeFormConfig.get().effectiveItemMainHandOffsetX()
                : slot == DisplaySlot.OFFHAND ? SlimeFormConfig.get().effectiveItemOffHandOffsetX() : 0.0D;
    }

    private static double debugOffsetY(DisplaySlot slot) {
        return slot == DisplaySlot.MAINHAND
                ? SlimeFormConfig.get().effectiveItemMainHandOffsetY()
                : slot == DisplaySlot.OFFHAND ? SlimeFormConfig.get().effectiveItemOffHandOffsetY() : 0.0D;
    }

    private static double debugOffsetZ(DisplaySlot slot) {
        return slot == DisplaySlot.MAINHAND
                ? SlimeFormConfig.get().effectiveItemMainHandOffsetZ()
                : slot == DisplaySlot.OFFHAND ? SlimeFormConfig.get().effectiveItemOffHandOffsetZ() : 0.0D;
    }

    private static ItemStack itemFor(ServerPlayer player, DisplaySlot slot) {
        return switch (slot) {
            case MAINHAND -> player.getMainHandItem();
            case OFFHAND -> player.getOffhandItem();
            case HEAD -> player.getItemBySlot(EquipmentSlot.HEAD);
            case CHEST -> player.getItemBySlot(EquipmentSlot.CHEST);
            case LEGS -> player.getItemBySlot(EquipmentSlot.LEGS);
            case FEET -> player.getItemBySlot(EquipmentSlot.FEET);
        };
    }

    private static String visualTag(ServerPlayer player, boolean dormant) {
        return VISUAL_PREFIX + player.getUUID() + (dormant ? DORMANT_SUFFIX : SLEEPING_SUFFIX);
    }
}
