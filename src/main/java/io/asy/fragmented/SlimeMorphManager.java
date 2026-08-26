package io.asy.fragmented;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import io.asy.fragmented.mixin.MobGoalSelectorAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Dedicated, server-authoritative temporary slime body control. */
public final class SlimeMorphManager {
    public static final String MORPH_TAG = "slimeform.morphed";
    public static final String BODY_TAG = "slimeform.morph_body";
    private static final Map<UUID, State> STATES = new HashMap<>();

    private SlimeMorphManager() {
    }

    public static boolean isMorphBody(Entity entity) {
        return entity.getTags().contains(BODY_TAG);
    }

    public static ServerPlayer ownerOf(Slime body) {
        for (Map.Entry<UUID, State> entry : STATES.entrySet()) {
            if (entry.getValue().bodyId != null && entry.getValue().bodyId.equals(body.getUUID())
                    && body.level() instanceof ServerLevel level) {
                return level.getServer().getPlayerList().getPlayer(entry.getKey());
            }
        }
        return null;
    }

    public static void handleInput(ServerPlayer player, SlimeFormPayloads.MorphInputPayload input) {
        State state = STATES.computeIfAbsent(player.getUUID(), ignored -> new State());
        state.input = input;
        state.lastInputTick = player.level().getGameTime();
    }

    public static void tick(MinecraftServer server) {
        SlimeFormConfig config = SlimeFormConfig.get();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            State state = STATES.get(player.getUUID());
            if (!config.slimeMorphEnabled || !eligible(player)) {
                if (state != null) {
                    finish(player, state, true);
                }
                continue;
            }
            if (state == null) {
                continue;
            }
            if (player.level().getGameTime() - state.lastInputTick > 5L) {
                state.input = new SlimeFormPayloads.MorphInputPayload(
                        false, false, false, false, false, false, player.getYRot(), player.getXRot());
            }
            if (state.phase == Phase.MORPHED || state.phase == Phase.EXITING) {
                Slime body = body(player, state);
                if (body == null || !body.isAlive() || !player.isAlive()) {
                    finish(player, state, false);
                    continue;
                }
                control(player, state, body, config);
                if (state.phase == Phase.MORPHED && state.input.crouch()) {
                    state.phase = Phase.EXITING;
                    state.holdTicks = 0;
                }
                if (state.phase == Phase.EXITING) {
                    if (!state.input.crouch()) {
                        state.phase = Phase.MORPHED;
                        state.holdTicks = 0;
                        sendState(player, state, 0);
                    } else {
                        state.holdTicks++;
                        int duration = config.effectiveSlimeMorphExitTicks();
                        sendState(player, state, duration);
                        if (state.holdTicks >= duration) {
                            finish(player, state, true);
                        }
                    }
                }
            } else if (state.phase == Phase.TRANSFORMING || state.phase == Phase.EXITING) {
                if (!state.input.crouch()) {
                    state.phase = state.phase == Phase.EXITING ? Phase.MORPHED : Phase.IDLE;
                    state.holdTicks = 0;
                    sendState(player, state, 0);
                    continue;
                }
                state.holdTicks++;
                int duration = state.phase == Phase.TRANSFORMING
                        ? config.effectiveSlimeMorphTransformTicks()
                        : config.effectiveSlimeMorphExitTicks();
                sendState(player, state, duration);
                if (state.holdTicks >= duration) {
                    if (state.phase == Phase.TRANSFORMING) {
                        begin(player, state);
                    } else {
                        finish(player, state, true);
                    }
                }
            } else if (state.input.crouch()) {
                state.phase = Phase.TRANSFORMING;
                state.holdTicks = 0;
                sendState(player, state, config.effectiveSlimeMorphTransformTicks());
            }
        }
        STATES.entrySet().removeIf(entry -> server.getPlayerList().getPlayer(entry.getKey()) == null);
    }

    public static void stop(ServerPlayer player) {
        State state = STATES.remove(player.getUUID());
        if (state != null) {
            finish(player, state, true);
        }
    }

    private static boolean eligible(ServerPlayer player) {
        return SlimeFormState.isActive(player) && !SlimeFormMod.isDormant(player)
                && !player.isSpectator() && !player.isSleeping() && !player.isPassenger();
    }

    private static void begin(ServerPlayer player, State state) {
        ServerLevel level = (ServerLevel) player.level();
        Slime body = EntityType.SLIME.create(level, EntitySpawnReason.TRIGGERED);
        if (body == null) {
            state.phase = Phase.IDLE;
            state.holdTicks = 0;
            return;
        }
        body.setSize(SlimeFormState.getSize(player), true);
        body.addTag(BODY_TAG);
        body.setPersistenceRequired();
        body.setNoAi(false);
        body.setNoGravity(false);
        MobGoalSelectorAccessor goals = (MobGoalSelectorAccessor) (Object) body;
        goals.slimeform$getGoalSelector().removeAllGoals(goal -> true);
        goals.slimeform$getTargetSelector().removeAllGoals(goal -> true);
        body.setPos(player.position());
        body.setYRot(player.getYRot());
        level.addFreshEntity(body);
        state.bodyId = body.getUUID();
        state.phase = Phase.MORPHED;
        state.holdTicks = 0;
        state.wasInvisible = player.isInvisible();
        player.addTag(MORPH_TAG);
        player.setInvisible(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.setCamera(body);
        player.connection.send(new ClientboundSetCameraPacket(body));
        player.displayClientMessage(Component.literal("You became a slime. Hold crouch to return."), true);
        sendState(player, state, 0);
    }

    private static void finish(ServerPlayer player, State state, boolean teleport) {
        Slime body = body(player, state);
        if (teleport && body != null && body.isAlive()) {
            player.teleportTo(body.getX(), body.getY(), body.getZ());
        }
        if (body != null) {
            body.remove(Entity.RemovalReason.DISCARDED);
        }
        player.removeTag(MORPH_TAG);
        player.setInvisible(state.wasInvisible);
        player.setCamera(player);
        player.connection.send(new ClientboundSetCameraPacket(player));
        player.setDeltaMovement(Vec3.ZERO);
        sendState(player, state, 0);
        STATES.remove(player.getUUID());
    }

    private static Slime body(ServerPlayer player, State state) {
        return state.bodyId != null && player.level().getEntity(state.bodyId) instanceof Slime slime
                && isMorphBody(slime) ? slime : null;
    }

    private static void control(ServerPlayer player, State state, Slime body, SlimeFormConfig config) {
        SlimeFormPayloads.MorphInputPayload input = state.input;
        player.setInvisible(true);
        player.setPos(body.position());
        double strafe = (input.left() ? 1.0D : 0.0D) - (input.right() ? 1.0D : 0.0D);
        double forward = (input.forward() ? 1.0D : 0.0D) - (input.back() ? 1.0D : 0.0D);
        double length = Math.sqrt(strafe * strafe + forward * forward);

        if (length > 0.0D) {
            float movementYaw = input.yaw()
                    + (float) Math.toDegrees(Math.atan2(strafe, forward));
            ((SlimeMoveControlAccess) body.getMoveControl()).slimeform$setDirection(movementYaw, false);
        } else if (input.jump()) {
            ((SlimeMoveControlAccess) body.getMoveControl()).slimeform$setDirection(input.yaw(), false);
        }

        boolean movementRequested = length > 0.0D
                || input.jump()
                || (config.slimeMorphAutoJump && length > 0.0D);
        SlimeMoveControlAccess moveControl = (SlimeMoveControlAccess) body.getMoveControl();
        moveControl.slimeform$setWantedMovement(movementRequested ? 1.0D : 0.0D);
    }

    private static void sendState(ServerPlayer player, State state, int total) {
        int remaining = Math.max(0, total - state.holdTicks);
        ServerPlayNetworking.send(player, new SlimeFormPayloads.MorphStatePayload(
                state.phase.ordinal(), remaining, total, state.bodyId == null ? -1 : player.level().getEntity(state.bodyId) == null
                        ? -1 : player.level().getEntity(state.bodyId).getId()));
    }

    enum Phase { IDLE, TRANSFORMING, MORPHED, EXITING }

    private static final class State {
        private Phase phase = Phase.IDLE;
        private UUID bodyId;
        private int holdTicks;
        private boolean wasInvisible;
        private long lastInputTick;
        private SlimeFormPayloads.MorphInputPayload input = new SlimeFormPayloads.MorphInputPayload(
                false, false, false, false, false, false, 0.0F, 0.0F);
    }
}
