package io.asy.fragmented;

import com.mojang.math.Transformation;
import io.asy.fragmented.mixin.MobGoalSelectorAccessor;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Display.ItemDisplay;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.cubemob.Slime;
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

/**
 * Server-authoritative, non-gameplay slime representations for sleeping and dormant players.
 *
 * <p>The player "becomes a block": a size-1 visual slime, with the held and worn items floating
 * above it. The item displays ride the slime as passengers (mounted a couple of ticks after they
 * spawn, because a passengers packet that reaches a client before the display itself is dropped
 * for good) and animate with a gentle hover, bob and slow spin driven by a keyframe every few
 * ticks that the client interpolates. If mounting ever fails, the displays are moved manually
 * every tick instead.
 */
public final class SlimeFormVisuals {
    private static final String VISUAL_PREFIX = "slimeform.visual.";
    private static final String SLEEPING_SUFFIX = ".sleeping";
    private static final String DORMANT_SUFFIX = ".dormant";
    private static final Map<UUID, UUID> DORMANT_SLIMES = new HashMap<>();
    private static final Map<UUID, UUID> SLEEPING_SLIMES = new HashMap<>();
    private static final Map<UUID, SleepPose> SLEEP_POSES = new HashMap<>();
    private static final Set<UUID> VISUAL_PLAYERS = new HashSet<>();
    private static final Map<UUID, ItemDisplaySession> ITEM_DISPLAY_SESSIONS = new HashMap<>();
    private static final Map<UUID, ServerPlayer> PENDING_DORMANT_REMOVALS = new HashMap<>();
    private static final Map<UUID, Long> AMBIENT_PARTICLE_TICKS = new HashMap<>();

    /** Players without a visual are swept for leftovers (crash orphans) this often, not every tick. */
    private static final long ORPHAN_SWEEP_INTERVAL_TICKS = 20L;
    private static final long DISPLAY_CHECK_INTERVAL_TICKS = 10L;
    /** Displays are mounted this many ticks after they spawn so every client knows them first. */
    private static final long MOUNT_DELAY_TICKS = 2L;

    /**
     * The dormant slime wanders and hops with the player riding it: that movement is what tells
     * other players this slime is an AFK player. It never targets or attacks anything. Set to
     * false to make it hold still like a block.
     */
    private static final boolean DORMANT_SLIME_WANDERS = true;
    /** Nearby players get the slime's passenger list again this often, as insurance. */
    private static final long PASSENGER_RESYNC_INTERVAL_TICKS = 40L;
    private static final double PASSENGER_RESYNC_RANGE = 96.0D;

    /** Animation: one keyframe every few ticks, blended by the client. */
    private static final int KEYFRAME_TICKS = 10;
    /** Spin of 2 degrees per tick; 3600 ticks is exactly 20 turns, so the loop wraps seamlessly. */
    private static final double SPIN_RADIANS_PER_TICK = Math.toRadians(2.0D);
    private static final long SPIN_LOOP_TICKS = 3600L;
    private static final double BOB_PERIOD_TICKS = 60.0D;
    /** Built-in bob; the config value itemDisplayBobAmplitude is added on top of it. */
    private static final double ITEM_BOB_AMPLITUDE = 0.008D;
    /** How far above the slime's top the items hover. */
    private static final double ITEM_HOVER_HEIGHT = 0.03D;

    /** Items scatter inside this radius around the slime's top centre (rotation independent). */
    private static final double ITEM_SCATTER_RADIUS = 0.20D;
    /** Each item sits this much higher than the previous one so flat items never z-fight. */
    private static final double ITEM_LAYER_STEP = 0.012D;
    private static final double GOLDEN_ANGLE = 2.399963229728653D;

    /** The visual slime sleeps anywhere inside the two bed blocks, keeping its 0.52 body inside. */
    private static final double BED_ALONG_RANGE = 0.70D;
    private static final double BED_ACROSS_RANGE = 0.20D;
    private static final double BED_SLIME_HEIGHT = 0.55D;

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
    }

    private enum VisualMode {
        SLEEPING,
        DORMANT
    }

    /** Where the sleeping slime lies, relative to the bed centre, chosen once per sleep. */
    private record SleepPose(double along, double across, float yaw) {
    }

    /** Local position (relative to the slime's top centre) and spin of one floating item. */
    private record ItemLayout(double x, double y, double z, float spin) {
        private static final ItemLayout ZERO = new ItemLayout(0.0D, 0.0D, 0.0D, 0.0F);
    }

    private static final class ItemDisplaySession {
        private final VisualMode mode;
        private final ServerLevel level;
        private final UUID slimeId;
        private final float yaw;
        private final Map<DisplaySlot, UUID> displayIds = new EnumMap<>(DisplaySlot.class);
        private final Map<DisplaySlot, ItemLayout> layouts = new EnumMap<>(DisplaySlot.class);
        private final Map<DisplaySlot, ItemStack> snapshots = new EnumMap<>(DisplaySlot.class);
        private boolean mounted;
        private long mountAtTime;
        private boolean manualFollow;

        private ItemDisplaySession(VisualMode mode, ServerLevel level, Slime slime) {
            this.mode = mode;
            this.level = level;
            this.slimeId = slime.getUUID();
            this.yaw = slime.getYRot();
        }
    }

    private SlimeFormVisuals() {
    }

    public static void tick(ServerPlayer player) {
        SlimeFormConfig config = SlimeFormConfig.get();
        boolean active = SlimeFormState.isActive(player);
        boolean sleeping = active && player.isSleeping();
        boolean dormant = player.entityTags().contains(SlimeFormMod.SLIME_DORMANT_TAG);
        if (active && !sleeping && !dormant) {
            tickAmbientParticles(player);
        } else if (!active) {
            AMBIENT_PARTICLE_TICKS.remove(player.getUUID());
        }
        if (sleeping || dormant) {
            VISUAL_PLAYERS.add(player.getUUID());
            tickVisual(player, config, dormant);
        } else if (VISUAL_PLAYERS.remove(player.getUUID()) || isSweepTick(player)) {
            // Clean up right after a visual ends, and sweep for crash leftovers now and then.
            remove(player, false);
            remove(player, true);
        }
    }

    private static boolean isSweepTick(ServerPlayer player) {
        return (player.level().getGameTime() + player.getId()) % ORPHAN_SWEEP_INTERVAL_TICKS == 0L;
    }

    private static void tickVisual(ServerPlayer player, SlimeFormConfig config, boolean dormant) {
        VisualMode mode = dormant ? VisualMode.DORMANT : VisualMode.SLEEPING;
        String tag = visualTag(player, dormant);
        ServerLevel level = player.level();

        SleepPose pose = dormant ? null
                : SLEEP_POSES.computeIfAbsent(player.getUUID(), ignored -> createSleepPose(level));
        Vec3 anchor = dormant ? player.position() : sleepingSlimePosition(player, pose);
        float yaw = dormant ? player.getYRot() : pose.yaw();

        Slime slime = findVisual(level, player, tag, dormant);
        if (slime == null) {
            slime = create(level, player, tag, dormant, anchor, yaw);
        }
        if (slime == null) {
            return;
        }

        if (!dormant) {
            slime.setPos(anchor.x, anchor.y, anchor.z);
            slime.setYRot(yaw);
            slime.setYHeadRot(yaw);
            slime.yRotO = yaw;
            slime.yHeadRotO = yaw;
            slime.setDeltaMovement(0.0D, 0.0D, 0.0D);
        } else if (player.getVehicle() != slime && !player.isPassenger()) {
            player.startRiding(slime, true, true);
        }
        tickItemDisplays(player, config, slime, mode);
    }

    private static void tickAmbientParticles(ServerPlayer player) {
        if (!SlimeFormConfig.get().slimeFootstepParticles) {
            AMBIENT_PARTICLE_TICKS.remove(player.getUUID());
            return;
        }
        long now = player.level().getGameTime();
        Long last = AMBIENT_PARTICLE_TICKS.get(player.getUUID());
        boolean moving = player.getDeltaMovement().horizontalDistanceSqr() > 0.0001D;
        long interval = moving ? 4L : 20L;
        // A missing entry means "emit now". (The old Long.MIN_VALUE default overflowed in the
        // subtraction and made the elapsed time negative, so particles never appeared.)
        if (last != null && now - last < interval) {
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

    // ---------------------------------------------------------------- sleeping pose

    private static SleepPose createSleepPose(ServerLevel level) {
        return new SleepPose(
                randomBetween(level, -BED_ALONG_RANGE, BED_ALONG_RANGE),
                randomBetween(level, -BED_ACROSS_RANGE, BED_ACROSS_RANGE),
                (float) (level.getRandom().nextDouble() * 360.0D));
    }

    /**
     * The bed spans two blocks: the head block (the sleeping position) and the foot block behind
     * it. The pose offsets are measured from the middle of those two blocks, along the bed and
     * across it.
     */
    private static Vec3 sleepingSlimePosition(ServerPlayer player, SleepPose pose) {
        BlockPos bedPos = player.getSleepingPos().orElse(null);
        Direction bedFacing = player.getBedOrientation();
        if (bedPos == null || bedFacing == null || !bedFacing.getAxis().isHorizontal()) {
            return player.position();
        }

        double alongX = bedFacing.getStepX();
        double alongZ = bedFacing.getStepZ();
        double acrossX = -alongZ;
        double acrossZ = alongX;
        return new Vec3(
                bedPos.getX() + 0.5D - alongX * 0.5D + alongX * pose.along() + acrossX * pose.across(),
                bedPos.getY() + BED_SLIME_HEIGHT,
                bedPos.getZ() + 0.5D - alongZ * 0.5D + alongZ * pose.along() + acrossZ * pose.across());
    }

    // ---------------------------------------------------------------- removal

    public static void remove(ServerPlayer player, boolean dormant) {
        String tag = visualTag(player, dormant);
        Slime tracked = trackedVisual(player, dormant);
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
        (dormant ? DORMANT_SLIMES : SLEEPING_SLIMES).remove(player.getUUID());
        if (!dormant) {
            SLEEP_POSES.remove(player.getUUID());
        }
        VISUAL_PLAYERS.remove(player.getUUID());
        AMBIENT_PARTICLE_TICKS.remove(player.getUUID());
    }

    /** Rebuilds the floating items (new snapshot, new layout) on the next tick. */
    public static void refreshItemDisplays(ServerPlayer player) {
        removeItemDisplays(player);
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

    // ---------------------------------------------------------------- visual slime

    private static List<Slime> find(ServerLevel level, ServerPlayer player, String tag) {
        return level.getEntitiesOfClass(
                Slime.class,
                new AABB(player.blockPosition()).inflate(2.5D),
                slime -> slime.entityTags().contains(tag));
    }

    private static Slime findVisual(ServerLevel level, ServerPlayer player, String tag, boolean dormant) {
        Slime tracked = trackedVisual(player, dormant);
        if (tracked != null && tracked.isAlive() && tracked.entityTags().contains(tag)) {
            return tracked;
        }
        if (dormant && player.getVehicle() instanceof Slime vehicle && vehicle.entityTags().contains(tag)) {
            DORMANT_SLIMES.put(player.getUUID(), vehicle.getUUID());
            return vehicle;
        }
        // Only reached when nothing is tracked (first tick, or a visual left over from before a restart).
        Slime found = find(level, player, tag).stream().findFirst().orElse(null);
        if (found != null) {
            (dormant ? DORMANT_SLIMES : SLEEPING_SLIMES).put(player.getUUID(), found.getUUID());
        }
        return found;
    }

    private static Slime trackedVisual(ServerPlayer player, boolean dormant) {
        Map<UUID, UUID> map = dormant ? DORMANT_SLIMES : SLEEPING_SLIMES;
        UUID slimeId = map.get(player.getUUID());
        if (slimeId == null) {
            return null;
        }
        if (player.level().getEntity(slimeId) instanceof Slime slime && !slime.isRemoved()) {
            return slime;
        }
        map.remove(player.getUUID());
        return null;
    }

    private static Slime create(
            ServerLevel level, ServerPlayer player, String tag, boolean dormant, Vec3 position, float yaw) {
        Slime slime = EntityTypes.SLIME.create(level, EntitySpawnReason.TRIGGERED);
        if (slime == null) {
            return null;
        }
        slime.setSize(1, false);
        slime.addTag(tag);
        if (dormant) {
            slime.addTag(SlimeFormMod.PLAYER_DORMANT_SLIME_TAG);
        }
        slime.setInvulnerable(true);
        slime.setNoAi(!(dormant && DORMANT_SLIME_WANDERS));
        slime.setNoGravity(!dormant);
        if (dormant) {
            // A wandering AFK slime is only an indicator: no target goals, so it never attacks.
            ((MobGoalSelectorAccessor) (Object) slime).slimeform$getTargetSelector()
                    .removeAllGoals(goal -> true);
        }
        slime.setSilent(true);
        slime.setPersistenceRequired();
        slime.setPos(position.x, position.y, position.z);
        slime.setYRot(yaw);
        slime.setYHeadRot(yaw);
        slime.yRotO = yaw;
        slime.yHeadRotO = yaw;
        level.addFreshEntity(slime);
        (dormant ? DORMANT_SLIMES : SLEEPING_SLIMES).put(player.getUUID(), slime.getUUID());
        return slime;
    }

    // ---------------------------------------------------------------- floating items

    private static void tickItemDisplays(
            ServerPlayer player, SlimeFormConfig config, Slime slime, VisualMode mode) {
        ItemDisplaySession session = ITEM_DISPLAY_SESSIONS.get(player.getUUID());
        if (!config.floatingItemDisplays) {
            if (session != null) {
                removeItemDisplays(player);
            }
            return;
        }

        if (session == null
                || session.mode != mode
                || session.level != player.level()
                || !session.slimeId.equals(slime.getUUID())) {
            removeItemDisplays(player);
            ITEM_DISPLAY_SESSIONS.put(
                    player.getUUID(), createItemDisplaySession(player, player.level(), slime, mode));
            return;
        }

        long time = slime.level().getGameTime();
        if (!session.mounted && time >= session.mountAtTime) {
            mountDisplays(slime, session);
        }
        if (time % DISPLAY_CHECK_INTERVAL_TICKS == 0L) {
            ensureDisplays(player, slime, session, time);
        }
        if (session.manualFollow) {
            followManually(slime, session);
        } else if (session.mounted && time % PASSENGER_RESYNC_INTERVAL_TICKS == 0L) {
            resyncPassengers(player, slime);
        }
        animateDisplays(slime, session, time);
    }

    private static ItemDisplaySession createItemDisplaySession(
            ServerPlayer player, ServerLevel level, Slime slime, VisualMode mode) {
        ItemDisplaySession session = new ItemDisplaySession(mode, level, slime);
        for (DisplaySlot slot : DisplaySlot.values()) {
            ItemStack snapshot = itemFor(player, slot).copy();
            if (!snapshot.isEmpty()) {
                session.snapshots.put(slot, snapshot);
            }
        }
        session.layouts.putAll(createLayouts(level, session.snapshots.keySet()));
        for (DisplaySlot slot : DisplaySlot.values()) {
            ItemStack snapshot = session.snapshots.get(slot);
            if (snapshot != null) {
                spawnItemDisplay(player, level, slime, slot, snapshot,
                        session.layouts.getOrDefault(slot, ItemLayout.ZERO), session);
            }
        }
        session.mounted = false;
        session.mountAtTime = level.getGameTime() + MOUNT_DELAY_TICKS;
        return session;
    }

    /**
     * Scatters the items with a rotated Vogel spiral: well spread (no clumping) yet random,
     * because the starting angle, radius jitter and spin are random. Held items come first and
     * so land nearest the centre; every item gets its own height layer on top of the hover height.
     */
    private static Map<DisplaySlot, ItemLayout> createLayouts(ServerLevel level, Set<DisplaySlot> slots) {
        Map<DisplaySlot, ItemLayout> layouts = new EnumMap<>(DisplaySlot.class);
        int count = slots.size();
        if (count == 0) {
            return layouts;
        }
        double baseAngle = level.getRandom().nextDouble() * Math.PI * 2.0D;
        int index = 0;
        for (DisplaySlot slot : DisplaySlot.values()) {
            if (!slots.contains(slot)) {
                continue;
            }
            double angle = baseAngle + index * GOLDEN_ANGLE;
            double radius = ITEM_SCATTER_RADIUS * Math.sqrt((index + 0.5D) / count)
                    * (0.85D + 0.15D * level.getRandom().nextDouble());
            layouts.put(slot, new ItemLayout(
                    Math.cos(angle) * radius + configuredOffsetX(slot),
                    ITEM_HOVER_HEIGHT + index * ITEM_LAYER_STEP + configuredOffsetY(slot),
                    Math.sin(angle) * radius + configuredOffsetZ(slot),
                    (float) (level.getRandom().nextDouble() * Math.PI * 2.0D)));
            index++;
        }
        return layouts;
    }

    /**
     * Spawns one display at the slime's top with its first animation keyframe already set (so it
     * never pops from a default pose). Mounting happens later, see {@link #mountDisplays}.
     */
    private static void spawnItemDisplay(
            ServerPlayer player,
            ServerLevel level,
            Slime slime,
            DisplaySlot slot,
            ItemStack stack,
            ItemLayout layout,
            ItemDisplaySession session) {
        ItemDisplay display = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (display == null) {
            return;
        }
        display.addTag(itemDisplayTag(player, slot.tagName));
        display.setInvulnerable(true);
        display.getSlot(0).set(stack.copy());
        ((SlimeItemDisplayAccess) display).slimeform$setItemTransform(ItemDisplayContext.GROUND);
        ((SlimeDisplayAccess) display).slimeform$setTransformation(
                animatedTransformation(layout, slot, level.getGameTime()));
        display.setPos(slime.getX(), slime.getY() + slime.getBbHeight(), slime.getZ());
        display.setYRot(session.yaw);
        level.addFreshEntity(display);
        session.displayIds.put(slot, display.getUUID());
    }

    /** Puts every display on the slime. A failure switches the session to manual following. */
    private static void mountDisplays(Slime slime, ItemDisplaySession session) {
        ServerLevel level = (ServerLevel) slime.level();
        int failures = 0;
        for (DisplaySlot slot : DisplaySlot.values()) {
            UUID displayId = session.displayIds.get(slot);
            if (displayId == null
                    || !(level.getEntity(displayId) instanceof ItemDisplay display)
                    || display.isRemoved()) {
                continue;
            }
            if (display.getVehicle() != slime && !display.startRiding(slime, true, true)) {
                failures++;
            }
        }
        session.mounted = true;
        SlimeFormMod.LOGGER.info("[slimeform] Mounted item displays on the visual slime ({} failed)", failures);
        if (failures > 0 && !session.manualFollow) {
            session.manualFollow = true;
            SlimeFormMod.LOGGER.warn(
                    "[slimeform] {} item display(s) could not ride the visual slime; following it manually",
                    failures);
        }
    }

    /**
     * Sends the slime's passenger list again to nearby players other than the owner. A client that
     * missed the original packet (for example because it did not know a display yet) would
     * otherwise never see the displays ride along, and the server only resends on a change.
     */
    private static void resyncPassengers(ServerPlayer owner, Slime slime) {
        ServerLevel level = (ServerLevel) slime.level();
        ClientboundSetPassengersPacket packet = new ClientboundSetPassengersPacket(slime);
        double rangeSqr = PASSENGER_RESYNC_RANGE * PASSENGER_RESYNC_RANGE;
        for (ServerPlayer observer : level.players()) {
            if (observer != owner && observer.distanceToSqr(slime) <= rangeSqr) {
                observer.connection.send(packet);
            }
        }
    }

    /** Fallback when mounting is not possible: move the displays to the slime every tick. */
    private static void followManually(Slime slime, ItemDisplaySession session) {
        ServerLevel level = (ServerLevel) slime.level();
        double x = slime.getX();
        double y = slime.getY() + slime.getBbHeight();
        double z = slime.getZ();
        for (UUID displayId : session.displayIds.values()) {
            if (level.getEntity(displayId) instanceof ItemDisplay display && !display.isRemoved()) {
                display.setPos(x, y, z);
            }
        }
    }

    /** Re-creates a display that disappeared (same layout, no re-roll) and re-mounts loose ones. */
    private static void ensureDisplays(
            ServerPlayer player, Slime slime, ItemDisplaySession session, long time) {
        ServerLevel level = (ServerLevel) slime.level();
        for (DisplaySlot slot : DisplaySlot.values()) {
            ItemStack snapshot = session.snapshots.get(slot);
            if (snapshot == null) {
                continue;
            }
            UUID displayId = session.displayIds.get(slot);
            if (displayId != null
                    && level.getEntity(displayId) instanceof ItemDisplay display
                    && !display.isRemoved()) {
                if (!session.manualFollow && display.getVehicle() != slime) {
                    session.mounted = false;
                    session.mountAtTime = time;
                }
                continue;
            }
            spawnItemDisplay(player, level, slime, slot, snapshot,
                    session.layouts.getOrDefault(slot, ItemLayout.ZERO), session);
            session.mounted = false;
            session.mountAtTime = time + MOUNT_DELAY_TICKS;
        }
    }

    /**
     * Sends the next animation keyframe. Each display is staggered by its slot so the packets are
     * spread over the ticks. The client blends between keyframes (transformation interpolation),
     * giving smooth motion from one update per {@link #KEYFRAME_TICKS} ticks.
     */
    private static void animateDisplays(Slime slime, ItemDisplaySession session, long time) {
        int keyframe = KEYFRAME_TICKS;
        ServerLevel level = (ServerLevel) slime.level();
        for (DisplaySlot slot : DisplaySlot.values()) {
            if ((time + slot.ordinal()) % keyframe != 0L) {
                continue;
            }
            UUID displayId = session.displayIds.get(slot);
            if (displayId == null
                    || !(level.getEntity(displayId) instanceof ItemDisplay display)
                    || display.isRemoved()) {
                continue;
            }
            SlimeDisplayAccess access = (SlimeDisplayAccess) display;
            access.slimeform$setTransformationInterpolationDuration(keyframe);
            access.slimeform$setTransformationInterpolationDelay(0);
            access.slimeform$setTransformation(animatedTransformation(
                    session.layouts.getOrDefault(slot, ItemLayout.ZERO), slot, time + keyframe));
        }
    }

    /** The pose the item should have at {@code time}: hovering, bobbing and slowly spinning. */
    private static Transformation animatedTransformation(ItemLayout layout, DisplaySlot slot, long time) {
        double spin = layout.spin() + SPIN_RADIANS_PER_TICK * (time % SPIN_LOOP_TICKS);
        double amplitude = ITEM_BOB_AMPLITUDE + SlimeFormConfig.get().effectiveItemDisplayBobAmplitude();
        double bob = Math.sin(time * Math.PI * 2.0D / BOB_PERIOD_TICKS + slot.ordinal() * Math.PI / 3.0D)
                * amplitude;
        SlimeFormConfig config = SlimeFormConfig.get();
        return new Transformation(
                new Vector3f((float) layout.x(), (float) (layout.y() + bob), (float) layout.z()),
                new Quaternionf()
                        .rotateY((float) spin)
                        .rotateX((float) Math.toRadians(config.effectiveItemDisplayRotationX()))
                        .rotateY((float) Math.toRadians(config.effectiveItemDisplayRotationY()))
                        .rotateZ((float) Math.toRadians(config.effectiveItemDisplayRotationZ())),
                new Vector3f((float) config.effectiveItemDisplayScale()),
                new Quaternionf());
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
                display -> display.entityTags().stream().anyMatch(tag -> tag.startsWith(itemDisplayPrefix(player))))) {
            if (removed.add(display.getUUID())) {
                display.discard();
            }
        }
    }

    private static String itemDisplayPrefix(ServerPlayer player) {
        return VISUAL_PREFIX + player.getUUID() + ".item.";
    }

    private static String itemDisplayTag(ServerPlayer player, String hand) {
        return itemDisplayPrefix(player) + hand;
    }

    private static double configuredOffsetX(DisplaySlot slot) {
        return slot == DisplaySlot.MAINHAND
                ? SlimeFormConfig.get().effectiveItemMainHandOffsetX()
                : slot == DisplaySlot.OFFHAND ? SlimeFormConfig.get().effectiveItemOffHandOffsetX() : 0.0D;
    }

    private static double configuredOffsetY(DisplaySlot slot) {
        return slot == DisplaySlot.MAINHAND
                ? SlimeFormConfig.get().effectiveItemMainHandOffsetY()
                : slot == DisplaySlot.OFFHAND ? SlimeFormConfig.get().effectiveItemOffHandOffsetY() : 0.0D;
    }

    private static double configuredOffsetZ(DisplaySlot slot) {
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

    private static double randomBetween(ServerLevel level, double first, double second) {
        return first + level.getRandom().nextDouble() * (second - first);
    }

    private static String visualTag(ServerPlayer player, boolean dormant) {
        return VISUAL_PREFIX + player.getUUID() + (dormant ? DORMANT_SUFFIX : SLEEPING_SUFFIX);
    }
}
