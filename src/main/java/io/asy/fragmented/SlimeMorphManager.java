package io.asy.fragmented;

import io.asy.fragmented.mixin.MobGoalSelectorAccessor;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative state and physical body for the temporary slime morph. */
public final class SlimeMorphManager {
    public static final String BODY_TAG = "slimeform.morph_body";
    private static final Map<UUID, State> STATES = new HashMap<>();

    private SlimeMorphManager() {
    }

    public static boolean isMorphBody(Entity entity) {
        return entity.entityTags().contains(BODY_TAG);
    }

    public static ServerPlayer ownerOf(Slime body) {
        for (Map.Entry<UUID, State> entry : STATES.entrySet()) {
            if (entry.getValue().bodyId != null
                    && entry.getValue().bodyId.equals(body.getUUID())
                    && body.level() instanceof ServerLevel level) {
                return level.getServer().getPlayerList().getPlayer(entry.getKey());
            }
        }
        return null;
    }

    public static boolean isMorphed(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        return state != null && (state.phase == Phase.MORPHED || state.phase == Phase.RETURNING);
    }

    /** Returns the size of the active morph, or the player's configured next-morph size. */
    public static int getCurrentMorphSize(net.minecraft.world.entity.player.Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            State state = STATES.get(serverPlayer.getUUID());
            if (state != null && (state.phase == Phase.MORPHED || state.phase == Phase.RETURNING)
                    && state.morphSize >= SlimeFormState.MIN_SIZE) {
                return state.morphSize;
            }
        }
        return SlimeFormState.getMorphSize(player);
    }

    public static boolean isMorphedClient(net.minecraft.world.entity.player.Player player) {
        return player.entityTags().contains(SlimeFormMod.MORPH_TAG);
    }

    public static void handleInput(ServerPlayer player, SlimeFormPayloads.MorphInputPayload input) {
        State state = STATES.get(player.getUUID());
        if (state != null) {
            state.input = input;
            state.lastInputTick = player.level().getGameTime();
        }
    }

    public static void handleToggle(ServerPlayer player) {
        if (!SlimeFormConfig.get().slimeMorphEnabled || !eligible(player)) {
            return;
        }
        State state = STATES.computeIfAbsent(player.getUUID(), ignored -> new State());
        if (state.phase == Phase.IDLE) {
            state.phase = Phase.ENTERING;
            state.progress = 0;
        } else if (state.phase == Phase.ENTERING) {
            int enter = SlimeFormConfig.get().effectiveSlimeMorphTransformTicks();
            int exit = SlimeFormConfig.get().effectiveSlimeMorphExitTicks();
            state.phase = Phase.RETURNING;
            state.progress = Math.max(0, exit - Math.round((float) state.progress * exit / enter));
        } else if (state.phase == Phase.MORPHED) {
            state.phase = Phase.RETURNING;
            state.progress = 0;
        } else if (state.phase == Phase.RETURNING) {
            // The controlled body remains authoritative while the exit countdown
            // is active. Cancelling that countdown must return to MORPHED directly;
            // routing through ENTERING would call begin() and create a second body.
            state.phase = Phase.MORPHED;
            state.progress = 0;
        }
        if (state.phase == Phase.ENTERING) {
            player.addTag(SlimeFormMod.MORPH_TAG);
            SlimeFormState.refreshMorphDimensions(player);
        }
        sendState(player, state);
    }

    public static void tick(MinecraftServer server) {
        SlimeFormConfig config = SlimeFormConfig.get();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            State state = STATES.get(player.getUUID());
            if (state == null) {
                continue;
            }
            if (!config.slimeMorphEnabled || !eligible(player)) {
                finish(player, state, false);
                continue;
            }

            // Keep the player out of the crouching pose for every morph phase.
            player.setShiftKeyDown(false);

            if (state.phase == Phase.MORPHED || state.phase == Phase.RETURNING) {
                Slime body = body(player, state);
                if (body == null || !body.isAlive()) {
                    finish(player, state, false);
                    continue;
                }
                control(player, state, body);
            }

            if (state.phase == Phase.ENTERING || state.phase == Phase.RETURNING) {
                int duration = state.phase == Phase.ENTERING
                        ? config.effectiveSlimeMorphTransformTicks()
                        : config.effectiveSlimeMorphExitTicks();
                state.progress++;
                if (state.progress >= duration) {
                    if (state.phase == Phase.ENTERING) {
                        begin(player, state);
                    } else {
                        finish(player, state, true);
                        continue;
                    }
                }
            }
            sendState(player, state);
        }
        STATES.entrySet().removeIf(entry -> server.getPlayerList().getPlayer(entry.getKey()) == null);
    }

    public static void stop(ServerPlayer player) {
        State state = STATES.remove(player.getUUID());
        if (state != null) {
            finish(player, state, false);
        } else {
            player.removeTag(SlimeFormMod.MORPH_TAG);
            SlimeFormState.refreshMorphDimensions(player);
        }
    }

    private static boolean eligible(ServerPlayer player) {
        return SlimeFormState.isActive(player) && !SlimeFormMod.isDormant(player)
                && !player.isSpectator() && !player.isSleeping() && !player.isPassenger()
                && player.isAlive();
    }

    private static void begin(ServerPlayer player, State state) {
        ServerLevel level = (ServerLevel) player.level();
        state.wasInvisible = player.isInvisible();
        state.wasNoPhysics = player.noPhysics;
        Slime body = EntityTypes.SLIME.create(level, EntitySpawnReason.TRIGGERED);
        if (body == null) {
            finish(player, state, false);
            return;
        }
        state.morphSize = SlimeFormState.getMorphSize(player);
        body.setSize(state.morphSize, true);
        body.addTag(BODY_TAG);
        body.setPersistenceRequired();
        body.setNoAi(false);
        body.setNoGravity(false);
        MobGoalSelectorAccessor goals = (MobGoalSelectorAccessor) (Object) body;
        goals.slimeform$getGoalSelector().removeAllGoals(goal -> true);
        goals.slimeform$getTargetSelector().removeAllGoals(goal -> true);
        body.setPos(player.position());
        body.setYRot(player.getYRot());
        body.setYHeadRot(player.getYRot());
        level.addFreshEntity(body);

        state.bodyId = body.getUUID();
        state.phase = Phase.MORPHED;
        state.progress = 0;
        state.lastInputTick = player.level().getGameTime();
        player.addTag(SlimeFormMod.MORPH_TAG);
        player.setInvisible(true);
        player.noPhysics = true;
        player.setCamera(body);
        player.connection.send(new ClientboundSetCameraPacket(body));
        player.sendOverlayMessage(Component.literal("You became a slime. Press the morph key to return."));
    }

    private static void control(ServerPlayer player, State state, Slime body) {
        SlimeFormPayloads.MorphInputPayload input = state.input;
        if (player.level().getGameTime() - state.lastInputTick > 5L) {
            input = new SlimeFormPayloads.MorphInputPayload(
                    false, false, false, false, false, player.getYRot(), player.getXRot());
        }

        player.setInvisible(true);
        player.setPos(body.position());
        player.noPhysics = true;

        double strafe = (input.right() ? 1.0D : 0.0D) - (input.left() ? 1.0D : 0.0D);
        double forward = (input.forward() ? 1.0D : 0.0D) - (input.back() ? 1.0D : 0.0D);
        double length = Math.sqrt(strafe * strafe + forward * forward);
        float movementYaw = input.yaw();
        if (length > 0.0D) {
            movementYaw += (float) Math.toDegrees(Math.atan2(strafe, forward));
        }
        boolean movementRequested = length > 0.0D || input.jump();

        // Player input supplies the desired direction; vanilla SlimeMoveControl
        // performs the actual speed, jumping, gravity, friction, and collision.
        SlimeMoveControlAccess moveControl = (SlimeMoveControlAccess) body.getMoveControl();
        moveControl.slimeform$setDirection(movementYaw, false);
        moveControl.slimeform$setWantedMovement(
                movementRequested ? 1.0D : 0.0D);
        Vec3 velocity = body.getDeltaMovement();
        if (!movementRequested || (body.onGround() && velocity.y <= 0.0D)) {
            body.setDeltaMovement(0.0D, velocity.y, 0.0D);
        }

        // Do not reset the body's yaw to the player's look direction here.
        // SlimeMoveControl rotates toward movementYaw, and resetting it every
        // tick prevents backward movement from reaching its 180-degree target.
    }

    private static void finish(ServerPlayer player, State state, boolean successfulExit) {
        Slime body = body(player, state);
        if (body != null) {
            body.remove(Entity.RemovalReason.DISCARDED);
        }
        player.removeTag(SlimeFormMod.MORPH_TAG);
        player.setInvisible(state.wasInvisible);
        player.noPhysics = state.wasNoPhysics;
        SlimeFormState.refreshMorphDimensions(player);
        player.setCamera(player);
        player.connection.send(new ClientboundSetCameraPacket(player));
        state.phase = Phase.IDLE;
        state.progress = 0;
        state.bodyId = null;
        sendState(player, state);
        STATES.remove(player.getUUID());
        if (successfulExit) {
            playExitFeedback(player);
        }
    }

    private static void playExitFeedback(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(
                ParticleTypes.ITEM_SLIME,
                player.getX(), player.getY() + player.getBbHeight() * 0.5D, player.getZ(),
                8, 0.25D, 0.35D, 0.25D, 0.03D);
        player.playSound(SoundEvents.SLIME_SQUISH, 0.7F, 1.1F);
    }

    private static Slime body(ServerPlayer player, State state) {
        return state.bodyId != null && player.level().getEntity(state.bodyId) instanceof Slime slime
                && isMorphBody(slime) ? slime : null;
    }

    private static void sendState(ServerPlayer player, State state) {
        int total = state.phase == Phase.ENTERING
                ? SlimeFormConfig.get().effectiveSlimeMorphTransformTicks()
                : state.phase == Phase.RETURNING
                ? SlimeFormConfig.get().effectiveSlimeMorphExitTicks() : 0;
        int remaining = total == 0 ? 0 : Math.max(0, total - state.progress);
        ServerPlayNetworking.send(player, new SlimeFormPayloads.MorphStatePayload(
                state.phase.ordinal(), remaining, total, getCurrentMorphSize(player)));
    }

    public enum Phase { IDLE, ENTERING, MORPHED, RETURNING }

    private static final class State {
        private Phase phase = Phase.IDLE;
        private UUID bodyId;
        private int progress;
        private long lastInputTick;
        private boolean wasInvisible;
        private boolean wasNoPhysics;
        private int morphSize;
        private SlimeFormPayloads.MorphInputPayload input = new SlimeFormPayloads.MorphInputPayload(
                false, false, false, false, false, 0.0F, 0.0F);
    }
}
