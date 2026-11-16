package io.asy.fragmented;

import io.asy.fragmented.mixin.MobGoalSelectorAccessor;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-authoritative state and physical body for the temporary slime morph.
 *
 * <p>The real slime body exists for the whole morph, including both transitions.
 * ENTERING and RETURNING only animate the body's scale (a small jelly wobble on
 * top of an eased resize) while the player already controls it, so the player
 * is a real slime from the first tick instead of a static stand-in model.
 */
public final class SlimeMorphManager {
    public static final String BODY_TAG = "slimeform.morph_body";
    /** Charged leap: hold jump to charge, release to launch. A short tap is a normal hop. */
    private static final int LEAP_MIN_CHARGE_TICKS = 6;
    private static final int LEAP_MAX_CHARGE_TICKS = 24;
    private static final int LEAP_COOLDOWN_TICKS = 30;
    private static final int LEAP_FEEDBACK_INTERVAL_TICKS = 4;
    private static final int LEAP_LANDING_GRACE_TICKS = 4;
    private static final int LEAP_MAX_AIR_TICKS = 100;
    /** A leap that drops at least this far ends in a shockwave that shoves nearby mobs away. */
    private static final double LEAP_SLAM_MIN_DROP = 2.0D;
    private static final Map<UUID, State> STATES = new HashMap<>();
    /** Bodies this class is removing on purpose, so the peaceful guard lets that removal through. */
    private static final Set<UUID> REMOVING_BODIES = new HashSet<>();

    private static final Identifier BODY_SCALE_MODIFIER_ID =
            Identifier.fromNamespaceAndPath(SlimeFormMod.MOD_ID, "slime_morph_body_scale");
    /** The body starts (and ends) no wider than a player, then resizes to its real size. */
    private static final double PLAYER_WIDTH = 0.6D;
    private static final double MIN_START_SCALE = 0.3D;
    /** Jelly wobble layered on the resize; fades out toward the fully-morphed end. */
    private static final double WOBBLE_AMPLITUDE = 0.12D;
    private static final double WOBBLE_CYCLES = 2.5D;
    private static final int GOO_PARTICLE_INTERVAL_TICKS = 3;
    /** The camera packet is repeated briefly after the body spawns, like the recovery camera. */
    private static final int CAMERA_RESYNC_TICKS = 5;

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

    /**
     * True when a discard of this body must be refused: peaceful difficulty makes vanilla discard
     * hostile mobs, which would end the morph. Orphaned bodies (no active morph) and removals made
     * by this class are not blocked, so cleanup still works.
     */
    public static boolean shouldBlockPeacefulRemoval(Slime body) {
        return isMorphBody(body)
                && !body.level().isClientSide()
                && body.level().getDifficulty() == Difficulty.PEACEFUL
                && !REMOVING_BODIES.contains(body.getUUID())
                && ownerOf(body) != null;
    }

    public static boolean isMorphed(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        return state != null && state.phase != Phase.IDLE;
    }

    /** Returns the size of the active morph, or the player's configured next-morph size. */
    public static int getCurrentMorphSize(net.minecraft.world.entity.player.Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            State state = STATES.get(serverPlayer.getUUID());
            if (state != null && state.phase != Phase.IDLE
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
        int enter = SlimeFormConfig.get().effectiveSlimeMorphTransformTicks();
        int exit = SlimeFormConfig.get().effectiveSlimeMorphExitTicks();
        if (state.phase == Phase.IDLE) {
            // The real body is created right away; ENTERING only animates it.
            if (!begin(player, state)) {
                return;
            }
        } else if (state.phase == Phase.ENTERING) {
            state.phase = Phase.RETURNING;
            state.progress = Math.max(0, exit - Math.round((float) state.progress * exit / enter));
        } else if (state.phase == Phase.MORPHED) {
            state.phase = Phase.RETURNING;
            state.progress = 0;
        } else if (state.phase == Phase.RETURNING) {
            // Cancelling the exit resumes the grow animation from the same visual
            // point. The body already exists, so this never creates a second one.
            state.phase = Phase.ENTERING;
            state.progress = Math.max(0, enter - Math.round((float) state.progress * enter / exit));
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

            if (state.phase != Phase.IDLE) {
                Slime body = body(player, state);
                if (body == null || !body.isAlive()) {
                    finish(player, state, false);
                    continue;
                }
                control(player, state, body);
                resyncCamera(player, state, body);

                if (state.phase == Phase.ENTERING || state.phase == Phase.RETURNING) {
                    boolean entering = state.phase == Phase.ENTERING;
                    int duration = Math.max(1, entering
                            ? config.effectiveSlimeMorphTransformTicks()
                            : config.effectiveSlimeMorphExitTicks());
                    state.progress++;
                    if (state.progress >= duration) {
                        if (entering) {
                            setBodyScale(body, state, 1.0D);
                            state.phase = Phase.MORPHED;
                            state.progress = 0;
                            playFeedback(player, 8, 1.2F);
                            player.sendOverlayMessage(Component.literal(
                                    "You became a slime. Press the morph key to return."));
                        } else {
                            finish(player, state, true);
                            continue;
                        }
                    } else {
                        // t runs 0 (player-sized) to 1 (full slime) in both directions.
                        double t = (double) state.progress / duration;
                        setBodyScale(body, state, scaleFor(state, entering ? t : 1.0D - t));
                        if (state.progress % GOO_PARTICLE_INTERVAL_TICKS == 0) {
                            playGoo(body);
                        }
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

    /** Spawns the controlled body and starts ENTERING. Returns false if nothing was started. */
    private static boolean begin(ServerPlayer player, State state) {
        ServerLevel level = (ServerLevel) player.level();
        Slime body = SlimeFormEntities.createSlime(level);
        if (body == null) {
            STATES.remove(player.getUUID());
            return false;
        }
        state.wasInvisible = player.isInvisible();
        state.wasNoPhysics = player.noPhysics;
        state.morphSize = SlimeFormState.getMorphSize(player);
        body.setSize(state.morphSize, true);
        body.addTag(BODY_TAG);
        body.setPersistenceRequired();
        body.setNoAi(false);
        body.setNoGravity(false);
        MobGoalSelectorAccessor goals = (MobGoalSelectorAccessor) (Object) body;
        goals.slimeform$getGoalSelector().removeAllGoals(goal -> true);
        goals.slimeform$getTargetSelector().removeAllGoals(goal -> true);

        // Start no wider than the player so the body never spawns inside walls
        // the player was standing next to, then grow to the real size.
        state.startScale = Math.max(MIN_START_SCALE,
                Math.min(1.0D, PLAYER_WIDTH / Math.max(0.01D, body.getBbHeight())));
        state.appliedScale = 1.0D;
        setBodyScale(body, state, scaleFor(state, 0.0D));

        body.setPos(player.position());
        body.setYRot(player.getYRot());
        body.setYHeadRot(player.getYRot());
        level.addFreshEntity(body);

        state.bodyId = body.getUUID();
        state.phase = Phase.ENTERING;
        state.progress = 0;
        state.lastInputTick = player.level().getGameTime();
        state.cameraResync = CAMERA_RESYNC_TICKS;
        player.addTag(SlimeFormMod.MORPH_TAG);
        SlimeFormState.refreshMorphDimensions(player);
        player.setInvisible(true);
        player.noPhysics = true;
        player.setCamera(body);
        player.connection.send(new ClientboundSetCameraPacket(body));
        playFeedback(player, 12, 0.8F);
        return true;
    }

    /** Scale for transition progress t (0 = player-sized, 1 = full slime), with jelly wobble. */
    private static double scaleFor(State state, double t) {
        double clamped = Math.max(0.0D, Math.min(1.0D, t));
        double eased = clamped * clamped * (3.0D - 2.0D * clamped);
        double base = state.startScale + (1.0D - state.startScale) * eased;
        double wobble = 1.0D + WOBBLE_AMPLITUDE
                * Math.sin(clamped * Math.PI * 2.0D * WOBBLE_CYCLES) * (1.0D - clamped);
        return base * wobble;
    }

    private static void setBodyScale(Slime body, State state, double scale) {
        AttributeInstance attribute = body.getAttribute(Attributes.SCALE);
        if (attribute == null || Math.abs(scale - state.appliedScale) < 1.0E-4D) {
            return;
        }
        if (Math.abs(scale - 1.0D) < 1.0E-4D) {
            attribute.removeModifier(BODY_SCALE_MODIFIER_ID);
        } else {
            attribute.addOrUpdateTransientModifier(new AttributeModifier(
                    BODY_SCALE_MODIFIER_ID,
                    scale - 1.0D,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        body.refreshDimensions();
        state.appliedScale = scale;
    }

    private static void resyncCamera(ServerPlayer player, State state, Slime body) {
        if (state.cameraResync > 0) {
            state.cameraResync--;
            player.connection.send(new ClientboundSetCameraPacket(body));
        }
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
        // Keep the server-side player looking where the real player looks, so directional
        // placement and item use (stairs, buckets, pearls) follow the mouse and not a stale
        // rotation, and keep the chunk tracking centre on the body.
        player.setYRot(input.yaw());
        player.setXRot(input.pitch());
        player.setYHeadRot(input.yaw());
        ((ServerLevel) player.level()).getChunkSource().move(player);

        // The morph body has its vanilla goals cleared, including
        // AbstractCubeMob.CubeMobFloatGoal. Preserve that goal's buoyancy
        // behavior so a controlled slime keeps jumping toward the surface.
        if ((body.isInWater() || body.isInLava()) && body.getRandom().nextFloat() < 0.8F) {
            body.getJumpControl().jump();
        }

        double strafe = (input.right() ? 1.0D : 0.0D) - (input.left() ? 1.0D : 0.0D);
        double forward = (input.forward() ? 1.0D : 0.0D) - (input.back() ? 1.0D : 0.0D);
        double length = Math.sqrt(strafe * strafe + forward * forward);
        float movementYaw = input.yaw();
        if (length > 0.0D) {
            movementYaw += (float) Math.toDegrees(Math.atan2(strafe, forward));
        }
        boolean movementRequested = length > 0.0D || input.jump();

        // Charged leap (hold jump, release to launch) and the landing shockwave.
        float aimYaw = length > 0.0D ? movementYaw : input.yaw();
        boolean charging = state.phase == Phase.MORPHED
                && updateLeap(player, state, body, input, aimYaw, body.onGround());

        SlimeMoveControlAccess moveControl = (SlimeMoveControlAccess) body.getMoveControl();
        if (state.leaping) {
            // In the air after a leap: keep the launch velocity and give no steering commands.
            return;
        }
        if (charging) {
            // Crouch and face the aim direction while charging; no hopping.
            moveControl.slimeform$setDirection(aimYaw, false);
            return;
        }

        // Player input supplies the desired direction; vanilla SlimeMoveControl
        // performs the actual speed, jumping, gravity, friction, and collision.
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
            REMOVING_BODIES.add(body.getUUID());
            try {
                body.remove(Entity.RemovalReason.DISCARDED);
            } finally {
                REMOVING_BODIES.remove(body.getUUID());
            }
        }
        player.removeTag(SlimeFormMod.MORPH_TAG);
        player.setInvisible(state.wasInvisible);
        player.noPhysics = state.wasNoPhysics;
        SlimeFormState.refreshMorphDimensions(player);
        if (body != null && player.isAlive()) {
            moveToSafeExit(player);
        }
        player.setCamera(player);
        player.connection.send(new ClientboundSetCameraPacket(player));
        state.phase = Phase.IDLE;
        state.progress = 0;
        state.bodyId = null;
        sendState(player, state);
        STATES.remove(player.getUUID());
        if (successfulExit) {
            playFeedback(player, 8, 1.1F);
        }
    }

    /**
     * The player model is taller than a small slime body, so returning inside a low gap could
     * place the player inside blocks. Instead of refusing to return, move the player to the
     * nearest spot where they fit.
     */
    private static void moveToSafeExit(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 spot = SlimeSpaceFinder.findFittingPosition(level, player);
        if (spot != null && spot.distanceToSqr(player.position()) > 1.0E-6D) {
            player.setPos(spot.x, spot.y, spot.z);
        }
    }

    /**
     * Charged leap state machine. Returns true while the player is charging (normal hopping is
     * paused). Holding jump on the ground charges; releasing launches, scaled by the charge, and a
     * quick tap is just a normal hop. After a leap lands there is a short cooldown, and a landing
     * from a real height shoves nearby mobs away.
     */
    private static boolean updateLeap(
            ServerPlayer player,
            State state,
            Slime body,
            SlimeFormPayloads.MorphInputPayload input,
            float aimYaw,
            boolean grounded) {
        if (state.leapCooldown > 0) {
            state.leapCooldown--;
        }
        if (state.leaping) {
            state.leapAge++;
            if (!grounded) {
                state.leapAirTicks++;
                state.leapPeakY = Math.max(state.leapPeakY, body.getY());
            } else if (state.leapAirTicks >= LEAP_LANDING_GRACE_TICKS) {
                landLeap(player, state, body);
                state.leaping = false;
            }
            if (state.leaping && state.leapAge > LEAP_MAX_AIR_TICKS) {
                state.leaping = false;
            }
            return false;
        }
        if (grounded && state.leapCooldown <= 0 && input.jump()) {
            state.leapCharge = Math.min(LEAP_MAX_CHARGE_TICKS, state.leapCharge + 1);
            if (state.leapCharge % LEAP_FEEDBACK_INTERVAL_TICKS == 0) {
                chargeFeedback(player, body, state.leapCharge);
            }
            return true;
        }
        if (state.leapCharge > 0) {
            int charge = state.leapCharge;
            state.leapCharge = 0;
            if (grounded && charge >= LEAP_MIN_CHARGE_TICKS) {
                launchLeap(player, state, body, aimYaw, charge);
            } else if (grounded) {
                body.getJumpControl().jump();
            }
        }
        return false;
    }

    private static void chargeFeedback(ServerPlayer player, Slime body, int charge) {
        ServerLevel level = (ServerLevel) body.level();
        double spread = body.getBbWidth() * 0.3D;
        level.sendParticles(
                ParticleTypes.ITEM_SLIME,
                body.getX(), body.getY() + 0.1D, body.getZ(),
                3, spread, 0.05D, spread, 0.02D);
        player.playSound(SoundEvents.SLIME_SQUISH, 0.5F, 0.6F + 0.6F * charge / LEAP_MAX_CHARGE_TICKS);
        int bars = Math.round(10.0F * charge / LEAP_MAX_CHARGE_TICKS);
        player.sendOverlayMessage(Component.literal(
                "Leap " + "|".repeat(bars) + ".".repeat(10 - bars)));
    }

    private static void launchLeap(
            ServerPlayer player, State state, Slime body, float aimYaw, int charge) {
        double ratio = Math.min(1.0D, (double) charge / LEAP_MAX_CHARGE_TICKS);
        // Bigger slimes leap a little shorter.
        double sizeFactor = Math.max(0.75D, 1.0D - 0.05D * (state.morphSize - 1));
        double horizontal = (0.30D + 0.38D * ratio) * sizeFactor;
        double vertical = (0.50D + 0.34D * ratio) * sizeFactor;
        double yaw = Math.toRadians(aimYaw);
        body.setDeltaMovement(-Math.sin(yaw) * horizontal, vertical, Math.cos(yaw) * horizontal);
//        body.hasImpulse = true;
        state.leaping = true;
        state.leapAge = 0;
        state.leapAirTicks = 0;
        state.leapPeakY = body.getY();
        ServerLevel level = (ServerLevel) body.level();
        double spread = body.getBbWidth() * 0.4D;
        level.sendParticles(
                ParticleTypes.ITEM_SLIME,
                body.getX(), body.getY() + 0.1D, body.getZ(),
                10, spread, 0.1D, spread, 0.05D);
        player.playSound(SoundEvents.SLIME_SQUISH, 0.9F, 1.2F + 0.3F * (float) ratio);
        player.sendOverlayMessage(Component.empty());
    }

    private static void landLeap(ServerPlayer player, State state, Slime body) {
        state.leapCooldown = LEAP_COOLDOWN_TICKS;
        double drop = state.leapPeakY - body.getY();
        ServerLevel level = (ServerLevel) body.level();
        double spread = body.getBbWidth() * 0.5D;
        level.sendParticles(
                ParticleTypes.ITEM_SLIME,
                body.getX(), body.getY() + 0.1D, body.getZ(),
                20, spread, 0.1D, spread, 0.06D);
        player.playSound(SoundEvents.SLIME_SQUISH, 1.0F, 0.6F);
        if (drop < LEAP_SLAM_MIN_DROP) {
            return;
        }
        double radius = Math.min(6.5D, 1.5D + 0.35D * state.morphSize + 0.15D * drop);
        level.sendParticles(
                ParticleTypes.ITEM_SLIME,
                body.getX(), body.getY() + 0.2D, body.getZ(),
                40, radius * 0.45D, 0.15D, radius * 0.45D, 0.1D);
        for (Mob target : level.getEntitiesOfClass(
                Mob.class,
                body.getBoundingBox().inflate(radius),
                mob -> mob.isAlive() && !(mob instanceof Slime))) {
            double dx = target.getX() - body.getX();
            double dz = target.getZ() - body.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > radius) {
                continue;
            }
            double closeness = 1.0D - distance / radius;
            double push = 0.45D + 0.65D * closeness;
            double nx = distance < 0.05D ? 0.0D : dx / distance;
            double nz = distance < 0.05D ? 0.0D : dz / distance;
            target.setDeltaMovement(target.getDeltaMovement().add(
                    nx * push, 0.30D + 0.25D * closeness, nz * push));
//            target.hasImpulse = true;
        }
    }

    private static void playFeedback(ServerPlayer player, int particles, float pitch) {
        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(
                ParticleTypes.ITEM_SLIME,
                player.getX(), player.getY() + player.getBbHeight() * 0.5D, player.getZ(),
                particles, 0.25D, 0.35D, 0.25D, 0.03D);
        player.playSound(SoundEvents.SLIME_SQUISH, 0.7F, pitch);
    }

    private static void playGoo(Slime body) {
        ServerLevel level = (ServerLevel) body.level();
        double spread = body.getBbHeight() * 0.25D;
        level.sendParticles(
                ParticleTypes.ITEM_SLIME,
                body.getX(), body.getY() + body.getBbHeight() * 0.5D, body.getZ(),
                2, spread, spread, spread, 0.02D);
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
        private double startScale = 1.0D;
        private double appliedScale = 1.0D;
        private int cameraResync;
        private int leapCharge;
        private int leapCooldown;
        private boolean leaping;
        private int leapAge;
        private int leapAirTicks;
        private double leapPeakY;
        private SlimeFormPayloads.MorphInputPayload input = new SlimeFormPayloads.MorphInputPayload(
                false, false, false, false, false, 0.0F, 0.0F);
    }
}
