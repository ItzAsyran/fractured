package io.asy.fragmented;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative lifecycle and input state for the experimental Flow State mode. */
public final class FlowStateManager {
    public static final String FLOW_TAG = "slimeform.flow_state";
    public static final String CONTROLLED_SLIME_TAG = "slimeform.flow_state.controlled";
    private static final Map<UUID, FlowState> STATES = new HashMap<>();

    private FlowStateManager() {
    }

    public static boolean isPossessed(ServerPlayer player) {
        FlowState state = STATES.get(player.getUUID());
        return state != null && state.possessed;
    }

    public static boolean isPossessed(Entity entity) {
        return entity.getTags().contains(CONTROLLED_SLIME_TAG)
                || entity.getTags().contains(FLOW_TAG);
    }

    public static ServerPlayer ownerOf(Slime slime) {
        for (Map.Entry<UUID, FlowState> entry : STATES.entrySet()) {
            FlowState state = entry.getValue();
            if (state.slimeId != null && state.slimeId.equals(slime.getUUID())
                    && slime.level() instanceof ServerLevel serverLevel) {
                return serverLevel.getServer().getPlayerList().getPlayer(entry.getKey());
            }
        }
        return null;
    }

    public static void handleInput(ServerPlayer player, SlimeFormPayloads.FlowStateInputPayload input) {
        SlimeFormConfig config = SlimeFormConfig.get();
        FlowState state = STATES.get(player.getUUID());
        if (config.flowStateDebug) {
            if (state == null) {
                state = new FlowState();
                STATES.put(player.getUUID(), state);
            }
            String signature = input.crouch() + ":" + input.shift() + ":" + input.jump()
                    + ":" + input.forward() + ":" + input.back() + ":" + input.left() + ":" + input.right();
            if (!signature.equals(state.lastInputSignature)) {
                debug(player, "input received: active=%s enabled=%s crouch=%s shift=%s jump=%s movement=%s",
                        SlimeFormState.isActive(player), config.flowStateEnabled,
                        input.crouch(), input.shift(), input.jump(), signature);
                state.lastInputSignature = signature;
            }
        }
        if (!config.flowStateEnabled) {
            debugRejected(player, state, "Flow State disabled");
            return;
        }
        if (!SlimeFormState.isActive(player)) {
            debugRejected(player, state, "player is not active SlimeForm");
            return;
        }

        if (state == null) {
            state = new FlowState();
            STATES.put(player.getUUID(), state);
        }
        state.input = input;

        if (!state.possessed) {
            if (input.crouch() && !state.previousCrouch) {
                if (state.armed) {
                    state.transforming = true;
                    debug(player, "crouch rising edge: second press -> transforming");
                } else {
                    state.armed = true;
                    debug(player, "crouch rising edge: first tap -> armed");
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "Flow State armed. Hold crouch to transform."), true);
                }
            }
        }
        state.previousCrouch = input.crouch();
    }

    public static void tick(MinecraftServer server) {
        if (!SlimeFormConfig.get().flowStateEnabled) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (isPossessed(player)) {
                    stop(player, true);
                }
            }
            STATES.clear();
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            FlowState state = STATES.get(player.getUUID());
            if (state == null) {
                continue;
            }
            if (!state.possessed) {
                if (state.transforming && state.input.crouch()) {
                    state.transformTicks++;
                    if (state.transformTicks == 1 || state.transformTicks % 10 == 0) {
                        debug(player, "transformation hold progress: %d/%d ticks",
                                state.transformTicks, SlimeFormConfig.get().flowStateTransformTicks());
                    }
                    if (state.transformTicks >= SlimeFormConfig.get().flowStateTransformTicks()) {
                        debug(player, "transformation hold complete: attempting slime creation");
                        begin(player, state);
                    }
                } else if (!state.input.crouch()) {
                    if (state.transforming) {
                        debug(player, "transformation cancelled: crouch released at %d/%d ticks",
                                state.transformTicks, SlimeFormConfig.get().flowStateTransformTicks());
                    }
                    state.transformTicks = 0;
                    state.transforming = false;
                }
                continue;
            }

            Slime slime = controlledSlime(player, state);
            if (slime == null || !slime.isAlive() || !player.isAlive()) {
                stop(player, false);
                continue;
            }
            control(player, state, slime);
            if (slime.tickCount % 10 == 0) {
                player.setCamera(slime);
                player.connection.send(new ClientboundSetCameraPacket(slime));
                ServerPlayNetworking.send(player,
                        new SlimeFormPayloads.FlowStateCameraPayload(slime.getId(), true));
            }
            if (state.input.jump()) {
                state.exitTicks++;
                if (state.exitTicks == 1 || state.exitTicks % 20 == 0) {
                    debug(player, "exit hold progress: %d/%d ticks",
                            state.exitTicks, SlimeFormConfig.get().flowStateExitTicks());
                }
                if (state.exitTicks >= SlimeFormConfig.get().flowStateExitTicks()) {
                    debug(player, "exit hold complete: ending possession");
                    stop(player, true);
                }
            } else {
                if (state.exitTicks > 0) {
                    debug(player, "exit hold cancelled: jump released at %d/%d ticks",
                            state.exitTicks, SlimeFormConfig.get().flowStateExitTicks());
                }
                state.exitTicks = 0;
            }
        }
    }

    private static void begin(ServerPlayer player, FlowState state) {
        if (isPossessed(player) || player.isSleeping() || player.isPassenger()) {
            debug(player, "possession rejected: already possessed=%s sleeping=%s passenger=%s",
                    isPossessed(player), player.isSleeping(), player.isPassenger());
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        Slime slime = EntityType.SLIME.create(level, EntitySpawnReason.TRIGGERED);
        if (slime == null) {
            debug(player, "possession rejected: EntityType.SLIME.create returned null");
            return;
        }
        slime.setSize(SlimeFormState.getSize(player), true);
        slime.addTag(CONTROLLED_SLIME_TAG);
        slime.setPersistenceRequired();
        slime.setNoAi(true);
        // Flow State applies gravity and ground probing in control(). Letting the
        // normal entity tick apply gravity as well makes the controlled slime
        // alternate between grounded and airborne while it is standing still.
        slime.setNoGravity(true);
        slime.setSilent(false);
        slime.setPos(player.position());
        slime.setYRot(player.getYRot());
        slime.setXRot(player.getXRot());
        level.addFreshEntity(slime);

        state.slimeId = slime.getUUID();
        state.possessed = true;
        state.armed = false;
        state.transforming = false;
        state.transformTicks = 0;
        state.grounded = player.onGround();
        state.previousInvisible = player.isInvisible();
        player.addTag(FLOW_TAG);
        player.setInvisible(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.setCamera(slime);
        player.connection.send(new ClientboundSetCameraPacket(slime));
        ServerPlayNetworking.send(player,
                new SlimeFormPayloads.FlowStateCameraPayload(slime.getId(), true));
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "Flow State active. Hold Jump to return."), true);
        debug(player, "possession started: slime=%s size=%d", slime.getUUID(), slime.getSize());
    }

    public static void stop(ServerPlayer player, boolean restore) {
        FlowState state = STATES.remove(player.getUUID());
        if (state == null) {
            player.removeTag(FLOW_TAG);
            return;
        }
        Slime slime = controlledSlime(player, state);
        if (restore && slime != null && slime.isAlive()) {
            player.teleportTo(slime.getX(), slime.getY(), slime.getZ());
        }
        if (slime != null) {
            slime.remove(Entity.RemovalReason.DISCARDED);
        }
        player.removeTag(FLOW_TAG);
        player.setInvisible(state.previousInvisible);
        player.setCamera(player);
        player.connection.send(new ClientboundSetCameraPacket(player));
        ServerPlayNetworking.send(player,
                new SlimeFormPayloads.FlowStateCameraPayload(-1, false));
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static Slime controlledSlime(ServerPlayer player, FlowState state) {
        if (state.slimeId == null || !(player.level().getEntity(state.slimeId) instanceof Slime slime)) {
            return null;
        }
        return slime.getTags().contains(CONTROLLED_SLIME_TAG) ? slime : null;
    }

    private static void control(ServerPlayer player, FlowState state, Slime slime) {
        SlimeFormPayloads.FlowStateInputPayload input = state.input;
        player.setInvisible(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(slime.getX(), slime.getY(), slime.getZ());
        slime.setYRot(input.yaw());
        slime.setYHeadRot(input.yaw());
        slime.setXRot(input.pitch());
        // The possession camera's lateral input is reversed relative to the
        // slime movement frame, so compensate before applying the yaw rotation.
        double x = (input.left() ? 1.0D : 0.0D) + (input.right() ? -1.0D : 0.0D);
        double z = (input.back() ? -1.0D : 0.0D) + (input.forward() ? 1.0D : 0.0D);
        boolean jumpPressed = input.jump() && !state.previousJump;
        boolean hasMovement = x != 0.0D || z != 0.0D;
        boolean grounded = state.grounded || slime.onGround();
        // Let vanilla SlimeMoveControl own its private jumpDelay. A manual
        // jump press gets a one-tick movement request so it uses that same
        // scheduler instead of bypassing it.
        boolean requestVanillaMovement = (hasMovement
                && (SlimeFormConfig.get().flowStateAutoJump || !grounded))
                || (!SlimeFormConfig.get().flowStateAutoJump && jumpPressed);
        SlimeMoveControlAccess moveControl = (SlimeMoveControlAccess) slime.getMoveControl();
        if (requestVanillaMovement) {
            moveControl.slimeform$setDirection(input.yaw(), false);
            moveControl.slimeform$setWantedMovement(1.0D);
        }
        slime.getMoveControl().tick();
        slime.getJumpControl().tick();
        boolean jumping = grounded && slime.isJumping();
        double dx = 0.0D;
        double dz = 0.0D;
        // A slime can only translate horizontally while airborne or while a
        // jump is being launched. This prevents grounded WASD sliding when
        // automatic jumping is disabled.
        if (hasMovement && (!grounded || jumping)) {
            double length = Math.sqrt(x * x + z * z);
            x /= length;
            z /= length;
            double angle = Math.toRadians(input.yaw());
            double movementSpeed = slime.getAttributeValue(Attributes.MOVEMENT_SPEED);
            dx = (z * -Math.sin(angle) + x * Math.cos(angle)) * movementSpeed;
            dz = (z * Math.cos(angle) + x * Math.sin(angle)) * movementSpeed;
        }
        Vec3 current = slime.getDeltaMovement();
        // A small downward probe keeps the entity's collision state grounded
        // without moving it vertically. It also detects landing immediately.
        double dy;
        if (jumping) {
            // Execute only the jump that vanilla MoveControl scheduled.
            slime.jumpFromGround();
            dy = slime.getDeltaMovement().y;
        } else {
            dy = grounded ? -0.08D : current.y;
        }
        Vec3 before = slime.position();
        slime.move(MoverType.SELF, new Vec3(dx, dy, dz));
        double nextY = slime.onGround() ? 0.0D : (dy * 0.98D - 0.08D);
        slime.setDeltaMovement(0.0D, nextY, 0.0D);
        state.grounded = slime.onGround();
        slime.setJumping(false);
        if (SlimeFormConfig.get().flowStateDebug
                && (x != 0.0D || z != 0.0D || slime.tickCount % 20 == 0)) {
            debug(player, "control: input=%s yaw=%.2f pitch=%.2f delta=(%.3f,%.3f,%.3f) pos=(%.2f,%.2f,%.2f)->(%.2f,%.2f,%.2f) onGround=%s",
                    input.forward() + ":" + input.back() + ":" + input.left() + ":" + input.right(),
                    input.yaw(), input.pitch(), dx, dy, dz,
                    before.x, before.y, before.z,
                    slime.getX(), slime.getY(), slime.getZ(), slime.onGround());
        }
        state.previousJump = input.jump();
        if (jumping) {
            debug(player, "jump applied: automatic=%s movement=%s", SlimeFormConfig.get().flowStateAutoJump,
                    x != 0.0D || z != 0.0D);
        }
    }

    private static final class FlowState {
        private UUID slimeId;
        private boolean armed;
        private boolean possessed;
        private boolean transforming;
        private boolean previousCrouch;
        private boolean previousInvisible;
        private int transformTicks;
        private int exitTicks;
        private boolean previousJump;
        private boolean grounded;
        private String lastInputSignature;
        private long lastRejectedDebugTick = Long.MIN_VALUE;
        private SlimeFormPayloads.FlowStateInputPayload input =
                new SlimeFormPayloads.FlowStateInputPayload(false, false, false, false, false, false, false, 0, 0);
    }

    private static void debug(ServerPlayer player, String message, Object... args) {
        if (SlimeFormConfig.get().flowStateDebug) {
            SlimeFormMod.LOGGER.info("[flow-debug] {} (player={} uuid={})",
                    String.format(java.util.Locale.ROOT, message, args),
                    player.getName().getString(), player.getUUID());
        }
    }

    private static void debugRejected(ServerPlayer player, FlowState state, String reason) {
        if (!SlimeFormConfig.get().flowStateDebug) {
            return;
        }
        long now = player.level().getGameTime();
        if (state == null || now - state.lastRejectedDebugTick >= 40L) {
            if (state != null) {
                state.lastRejectedDebugTick = now;
            }
            debug(player, "input rejected: %s", reason);
        }
    }
}
