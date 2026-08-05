package io.asy.fragmented;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
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
    private static final Map<UUID, ServerPlayer> PENDING_DORMANT_REMOVALS = new HashMap<>();

    private SlimeFormVisuals() {
    }

    public static void tick(ServerPlayer player) {
        boolean sleeping = SlimeFormState.isActive(player) && player.isSleeping();
        boolean dormant = player.getTags().contains(SlimeFormMod.SLIME_DORMANT_TAG);
        if (sleeping || dormant) {
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
                    slime.setYRot(player.getYRot());
                    slime.setYHeadRot(player.getYRot());
                    slime.yRotO = player.yRotO;
                    slime.yHeadRotO = player.yHeadRotO;
                    slime.setDeltaMovement(0.0D, 0.0D, 0.0D);
                } else if (player.getVehicle() != slime && !player.isPassenger()) {
                    player.startRiding(slime, true, true);
                }
            }
        } else {
            remove(player, false);
            remove(player, true);
        }
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
        if (dormant) {
            DORMANT_SLIMES.remove(player.getUUID());
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

    private static String visualTag(ServerPlayer player, boolean dormant) {
        return VISUAL_PREFIX + player.getUUID() + (dormant ? DORMANT_SUFFIX : SLEEPING_SUFFIX);
    }
}
