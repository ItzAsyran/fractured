package io.asy.fragmented;

import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative manager for the absorb mechanic.
 * Handles contact detection, capacity verification, entity shrinking, pinning,
 * hostile mob eating, loot/XP attribution, and safe ejection.
 */
public final class SlimeAbsorbManager {
    public static final String ABSORBED_TAG = "slimeform.absorbed";
    public static final String ABSORBED_BY_PREFIX = "slimeform.absorbed_by=";

    private static final Map<UUID, HolderState> HOLDERS = new ConcurrentHashMap<>();
    private static final Map<UUID, AbsorbedEntityState> ABSORBED_ENTITIES = new ConcurrentHashMap<>();
    private static final Set<UUID> DISSOLVE_DEATHS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final ThreadLocal<Boolean> APPLYING_ABSORB_DAMAGE = ThreadLocal.withInitial(() -> false);

    private SlimeAbsorbManager() {
    }

    public static boolean isApplyingAbsorbDamage() {
        return APPLYING_ABSORB_DAMAGE.get();
    }

    public static boolean isDissolveDeath(ServerPlayer player) {
        return player != null && DISSOLVE_DEATHS.contains(player.getUUID());
    }

    public static void markDissolveDeath(ServerPlayer player) {
        if (player != null) {
            DISSOLVE_DEATHS.add(player.getUUID());
        }
    }

    public static void clearDissolveDeath(ServerPlayer player) {
        if (player != null) {
            DISSOLVE_DEATHS.remove(player.getUUID());
        }
    }

    public static boolean isAbsorbed(Entity entity) {
        return entity != null && entity.entityTags().contains(ABSORBED_TAG);
    }

    public static boolean hasAbsorbedEntities(Slime slime) {
        HolderState holder = HOLDERS.get(slime.getUUID());
        return holder != null && !holder.absorbedIds.isEmpty();
    }

    public static List<UUID> getAbsorbedIds(Slime slime) {
        HolderState holder = HOLDERS.get(slime.getUUID());
        return holder != null ? Collections.unmodifiableList(holder.absorbedIds) : List.of();
    }

    public static double getHeldVolume(Slime slime) {
        HolderState holder = HOLDERS.get(slime.getUUID());
        return holder != null ? holder.getHeldVolume() : 0.0D;
    }

    public static ServerPlayer resolveOwner(Slime slime) {
        if (SlimeMorphManager.isMorphBody(slime)) {
            return SlimeMorphManager.ownerOf(slime);
        }
        String lineage = SlimeRecoveryManager.getRecoveryLineage(slime);
        if (lineage != null && slime.level() instanceof ServerLevel level) {
            int sep = lineage.indexOf("_recovery_");
            if (sep > 0) {
                try {
                    UUID playerId = UUID.fromString(lineage.substring(0, sep));
                    return level.getServer().getPlayerList().getPlayer(playerId);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return null;
    }

    public static boolean isEligibleHolder(Slime slime) {
        if (slime == null || !slime.isAlive() || slime.isRemoved() || slime.level().isClientSide()) {
            return false;
        }
        if (slime.getSize() <= 1) {
            return false;
        }
        if (SlimeMorphManager.isMorphBody(slime) || SlimeRecoveryManager.hasRecoveryLineage(slime)) {
            return true;
        }
        // Wild slimes: eligible if size >= 2, absorb is enabled, and a slime-form player is within range
        // Or if it already holds entities (must continue holding/eating them)
        if (hasAbsorbedEntities(slime)) {
            return true;
        }
        SlimeFormConfig config = SlimeFormConfig.get();
        return config.absorbEnabled && hasNearbySlimeFormPlayer(slime, config.effectiveAbsorbWildSlimeRange());
    }

    public static boolean isWildSlime(Slime slime) {
        return slime != null
                && !SlimeMorphManager.isMorphBody(slime)
                && !SlimeRecoveryManager.hasRecoveryLineage(slime)
                && !SlimeRecoveryManager.isPlayerOriginSlime(slime);
    }

    private static boolean hasNearbySlimeFormPlayer(Slime slime, int range) {
        if (!(slime.level() instanceof ServerLevel level)) {
            return false;
        }
        double rangeSq = (double) range * range;
        for (ServerPlayer player : level.players()) {
            if (SlimeFormState.isActive(player) && player.distanceToSqr(slime) <= rangeSq) {
                return true;
            }
        }
        return false;
    }

    public static void onServerStopping(MinecraftServer server) {
        ejectAllEverywhere(server);
    }

    public static SlimeFormPayloads.AbsorbSyncPayload createSyncPayload(Slime slime, HolderState holder, ServerPlayer owner) {
        int holderEntityId = slime != null ? slime.getId() : -1;
        int ownerEntityId = owner != null ? owner.getId() : -1;
        List<Integer> trappedEntityIds = new ArrayList<>();
        List<Integer> digestingEntityIds = new ArrayList<>();
        if (slime != null && holder != null && slime.level() instanceof ServerLevel serverLevel) {
            for (UUID uuid : holder.absorbedIds) {
                Entity entity = serverLevel.getEntity(uuid);
                if (entity != null) {
                    trappedEntityIds.add(entity.getId());
                    AbsorbedEntityState state = ABSORBED_ENTITIES.get(uuid);
                    if (state != null && state.digesting) {
                        digestingEntityIds.add(entity.getId());
                    }
                }
            }
        }
        return new SlimeFormPayloads.AbsorbSyncPayload(holderEntityId, ownerEntityId, trappedEntityIds, digestingEntityIds);
    }

    public static void broadcastSync(Slime slime, HolderState holder, ServerPlayer owner) {
        if (slime == null || !(slime.level() instanceof ServerLevel)) {
            return;
        }
        SlimeFormPayloads.AbsorbSyncPayload payload = createSyncPayload(slime, holder, owner);
        for (ServerPlayer viewer : PlayerLookup.tracking(slime)) {
            ServerPlayNetworking.send(viewer, payload);
        }
        if (owner != null && !PlayerLookup.tracking(slime).contains(owner)) {
            ServerPlayNetworking.send(owner, payload);
        }
        if (owner != null) {
            for (ServerPlayer viewer : PlayerLookup.tracking(owner)) {
                if (!PlayerLookup.tracking(slime).contains(viewer) && viewer != owner) {
                    ServerPlayNetworking.send(viewer, payload);
                }
            }
        }
    }

    public static void registerTrackingEvents() {
        EntityTrackingEvents.START_TRACKING.register((entity, viewer) -> {
            if (entity instanceof Slime slime && isEligibleHolder(slime) && hasAbsorbedEntities(slime)) {
                HolderState holder = HOLDERS.get(slime.getUUID());
                ServerPlayer owner = resolveOwner(slime);
                ServerPlayNetworking.send(viewer, createSyncPayload(slime, holder, owner));
            } else if (entity instanceof ServerPlayer player && SlimeMorphManager.isMorphed(player)) {
                Slime body = SlimeMorphManager.getMorphBody(player);
                if (body != null && hasAbsorbedEntities(body)) {
                    HolderState holder = HOLDERS.get(body.getUUID());
                    ServerPlayNetworking.send(viewer, createSyncPayload(body, holder, player));
                }
            }
        });
    }

    public static void tick(MinecraftServer server) {
        SlimeFormConfig config = SlimeFormConfig.get();
        if (!config.absorbEnabled) {
            if (!HOLDERS.isEmpty()) {
                ejectAllEverywhere(server);
            }
            return;
        }

        // 1. Process active holders
        List<Slime> activeHolders = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Slime slime && isEligibleHolder(slime)) {
                    activeHolders.add(slime);
                }
            }
        }

        // Clean up or eject from vanished holders
        Iterator<Map.Entry<UUID, HolderState>> holderIterator = HOLDERS.entrySet().iterator();
        while (holderIterator.hasNext()) {
            Map.Entry<UUID, HolderState> entry = holderIterator.next();
            UUID slimeId = entry.getKey();
            boolean stillActive = false;
            for (Slime slime : activeHolders) {
                if (slime.getUUID().equals(slimeId)) {
                    stillActive = true;
                    break;
                }
            }
            if (!stillActive) {
                ejectAllForMissingHolder(server, entry.getValue());
                holderIterator.remove();
            }
        }

        // 2. Perform contact detection and absorption for all eligible holders
        for (Slime slime : activeHolders) {
            HolderState holder = HOLDERS.computeIfAbsent(slime.getUUID(), id -> new HolderState(id));
            ServerPlayer owner = resolveOwner(slime);

            // Clean expired cooldowns
            long gameTime = slime.level().getGameTime();
            holder.reabsorbCooldowns.entrySet().removeIf(cd -> gameTime >= cd.getValue());

            // Contact bounding-box scan
            AABB bounds = slime.getBoundingBox();
            List<LivingEntity> touching = slime.level().getEntitiesOfClass(
                    LivingEntity.class, bounds, candidate -> candidate != slime && canAbsorb(slime, candidate, holder, owner, config));

            for (LivingEntity candidate : touching) {
                if (!bounds.intersects(candidate.getBoundingBox())) {
                    continue;
                }

                LivingEntity targetToAbsorb = candidate;
                if (candidate instanceof Slime candidateSlime && SlimeMorphManager.isMorphBody(candidateSlime)) {
                    ServerPlayer morphOwner = SlimeMorphManager.ownerOf(candidateSlime);
                    if (morphOwner != null) {
                        targetToAbsorb = morphOwner;
                        SlimeMorphManager.stop(morphOwner);
                    }
                }

                // Check capacity
                SlimeAbsorbScale.prepareForAbsorb(targetToAbsorb);
                double candidateVolume = SlimeAbsorbHelper.getEntityVolume(targetToAbsorb);
                double currentVolume = holder.getHeldVolume();
                double capacity = SlimeAbsorbHelper.getSlimeCapacity(slime.getSize(), config.effectiveAbsorbCapacityFactor());

                if (currentVolume + candidateVolume > capacity) {
                    playRefuseFeedback(slime, holder, gameTime);
                    break;
                }

                absorbEntity(slime, targetToAbsorb, holder, candidateVolume, config);
            }

            // Maintain pinned positions, layout and eating loop
            updateHeldEntities(slime, holder, owner, config);
        }

        // 3. Clean up any orphaned absorbed entities whose holders do not exist
        cleanupOrphanedAbsorbedEntities(server);
    }

    private static boolean canAbsorb(
            Slime slime, LivingEntity candidate, HolderState holder, ServerPlayer owner, SlimeFormConfig config) {
        if (candidate == null || !candidate.isAlive() || candidate.isRemoved()) {
            return false;
        }

        if (isWildSlime(slime)) {
            // Wild slimes never absorb players or player slimes; they only absorb mobs
            if (candidate instanceof Player || SlimeAbsorbHelper.isPlayerOriginOrSpecialSlime(candidate)) {
                return false;
            }
        }

        LivingEntity effectiveTarget = candidate;
        if (candidate instanceof Slime candidateSlime && SlimeMorphManager.isMorphBody(candidateSlime)) {
            // A morph body of another player counts as that player (absorb the player)
            ServerPlayer morphOwner = SlimeMorphManager.ownerOf(candidateSlime);
            if (morphOwner == null || (owner != null && morphOwner.getUUID().equals(owner.getUUID()))) {
                return false;
            }
            if (!config.absorbPlayersEnabled || morphOwner.isSpectator() || morphOwner.isCreative()) {
                return false;
            }
            effectiveTarget = morphOwner;
        } else if (candidate instanceof Player player) {
            if (!config.absorbPlayersEnabled || player.isSpectator() || player.isCreative()) {
                return false;
            }
        } else if (SlimeAbsorbHelper.isBlockedEntityType(candidate, config)) {
            return false;
        }

        if (effectiveTarget == null || !effectiveTarget.isAlive() || effectiveTarget.isRemoved()) {
            return false;
        }
        if (owner != null && effectiveTarget.getUUID().equals(owner.getUUID())) {
            return false;
        }
        if (effectiveTarget.getVehicle() == slime) {
            return false;
        }
        if (effectiveTarget.entityTags().contains(ABSORBED_TAG)) {
            return false;
        }
        if (holder.reabsorbCooldowns.containsKey(effectiveTarget.getUUID())) {
            return false;
        }
        return true;
    }

    private static void absorbEntity(
            Slime slime, LivingEntity candidate, HolderState holder, double unscaledVolume, SlimeFormConfig config) {
        candidate.ejectPassengers();
        if (candidate instanceof Mob mob && mob.isLeashed()) {
            mob.dropLeash();
        }

        boolean originalNoAi = candidate instanceof Mob mob && mob.isNoAi();
        boolean isHostile = SlimeAbsorbHelper.isHostile(candidate, config);
        boolean isBoss = SlimeAbsorbHelper.isBoss(candidate);
        boolean digesting = isHostile || isWildSlime(slime);
        AbsorbedEntityState state = new AbsorbedEntityState(
                candidate.getUUID(),
                slime.getUUID(),
                originalNoAi,
                candidate.isNoGravity(),
                candidate.isInvulnerable(),
                candidate.isSilent(),
                candidate.noPhysics,
                unscaledVolume,
                digesting,
                isBoss);

        if (candidate instanceof Mob mob) {
            mob.setNoAi(true);
        }
        candidate.setNoGravity(true);
        candidate.noPhysics = true;
        candidate.setDeltaMovement(Vec3.ZERO);

        if (candidate instanceof Creeper creeper) {
            creeper.setSwellDir(-1);
        }

        SlimeAbsorbScale.applyAbsorbScale(candidate, slime, config);

        candidate.addTag(ABSORBED_TAG);
        candidate.addTag(ABSORBED_BY_PREFIX + slime.getUUID());

        if (candidate instanceof ServerPlayer player) {
            player.setCamera(slime);
            player.connection.send(new ClientboundSetCameraPacket(slime));
        }

        holder.absorbedIds.add(candidate.getUUID());
        ABSORBED_ENTITIES.put(candidate.getUUID(), state);
        broadcastSync(slime, holder, resolveOwner(slime));

        ServerLevel level = (ServerLevel) slime.level();
        level.playSound(null, slime.getX(), slime.getY(), slime.getZ(),
                SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.8F, 1.2F);
        level.sendParticles(ParticleTypes.ITEM_SLIME,
                candidate.getX(), candidate.getY() + candidate.getBbHeight() * 0.5D, candidate.getZ(),
                12, 0.25D, 0.25D, 0.25D, 0.05D);
    }

    private static void updateHeldEntities(
            Slime slime, HolderState holder, ServerPlayer owner, SlimeFormConfig config) {
        ServerLevel level = (ServerLevel) slime.level();
        List<UUID> toRemove = new ArrayList<>();

        holder.recentDamageDecayTimer++;
        if (holder.recentDamageDecayTimer >= 40) {
            holder.recentDamageDecayTimer = 0;
            holder.recentInternalDamage = Math.max(0.0F, holder.recentInternalDamage - 5.0F);
        }

        int count = holder.absorbedIds.size();
        double innerWidth = slime.getBbWidth() * 0.58D;
        double halfWidth = innerWidth * 0.5D;
        double innerHeight = slime.getBbHeight() * 0.58D;
        double centerY = slime.getY() + slime.getBbHeight() * 0.35D;
        long gameTime = level.getGameTime();
        double churn = (gameTime * 0.035D);

        for (int i = 0; i < count; i++) {
            UUID id = holder.absorbedIds.get(i);
            AbsorbedEntityState state = ABSORBED_ENTITIES.get(id);
            Entity entity = level.getEntity(id);

            if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isRemoved()) {
                toRemove.add(id);
                continue;
            }

            // 3D Stratified Double-Helix Vortex layout
            double posX;
            double posY;
            double posZ;
            if (count == 1) {
                posX = slime.getX();
                posZ = slime.getZ();
                posY = centerY;
            } else {
                double u_i = ((double) i / (count - 1.0D)) - 0.5D; // -0.5 to +0.5
                double tierY = centerY + (u_i * innerHeight * 0.45D);
                double angle = churn + (2.0D * Math.PI * i / count);
                double r = halfWidth * ((i % 2 == 0) ? 0.60D : 0.38D);
                posX = slime.getX() + r * Math.cos(angle);
                posZ = slime.getZ() + r * Math.sin(angle);
                posY = tierY + Math.sin(churn * 0.8D + i) * (innerHeight * 0.08D);
            }

            living.setPos(posX, posY, posZ);
            living.setDeltaMovement(Vec3.ZERO);
            living.fallDistance = 0.0F;
            if (living instanceof ServerPlayer serverPlayer) {
                ((ServerLevel) serverPlayer.level()).getChunkSource().move(serverPlayer);
            }

            // Restrain creeper fuse
            if (living instanceof Creeper creeper) {
                creeper.setSwellDir(-1);
            }

            // Boss internal hazards & Gastric Spasm Regurgitation
            if (state.isBoss) {
                if (holder.recentInternalDamage >= 20.0F) {
                    holder.recentInternalDamage = 0.0F;
                    holder.isBraced = false;
                    level.playSound(null, posX, centerY, posZ, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 1.5F, 0.7F);
                    level.playSound(null, posX, centerY, posZ, SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 1.0F, 0.8F);
                    level.sendParticles(ParticleTypes.ITEM_SLIME, posX, centerY, posZ, 25, 0.6D, 0.6D, 0.6D, 0.1D);
                    if (owner != null) {
                        owner.sendSystemMessage(Component.translatable("slimeform.absorb.gastric_spasm", living.getDisplayName()), true);
                        if (state.qteActive) {
                            ServerPlayNetworking.send(owner, new SlimeFormPayloads.BossQteSyncPayload(living.getId(), "", 0, false));
                        }
                    }
                    eject(slime, living, config);
                    holder.reabsorbCooldowns.put(living.getUUID(), level.getGameTime() + 200L);
                    continue;
                }

                if (SlimeAbsorbHelper.isWither(living)) {
                    if (living.getHealth() <= living.getMaxHealth() * 0.5F && !state.witherExplodedAtHalfHp) {
                        state.witherExplodedAtHalfHp = true;
                        level.explode(null, slime.getX(), slime.getY(), slime.getZ(), 2.0F, Level.ExplosionInteraction.NONE);
                        slime.hurtServer(level, level.damageSources().explosion(null, living), 15.0F);
                        holder.recentInternalDamage += 15.0F;
                        if (owner != null) {
                            owner.sendSystemMessage(Component.translatable("slimeform.absorb.wither_enrage_rupture"), true);
                            if (state.qteActive) {
                                ServerPlayNetworking.send(owner, new SlimeFormPayloads.BossQteSyncPayload(living.getId(), "", 0, false));
                            }
                        }
                        eject(slime, living, config);
                        holder.reabsorbCooldowns.put(living.getUUID(), level.getGameTime() + 160L);
                        continue;
                    }

                    state.hazardTimer++;
                    if (state.hazardTimer == 56) {
                        state.qteActive = true;
                        state.qteTicksRemaining = 24;
                        state.hazardName = "wither_detonation";
                        holder.activeQteBossId = living.getId();
                        level.playSound(null, posX, centerY, posZ, SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 1.0F, 1.8F);
                        if (owner != null) {
                            ServerPlayNetworking.send(owner, new SlimeFormPayloads.BossQteSyncPayload(living.getId(), "wither_detonation", 24, true));
                        }
                    } else if (state.hazardTimer >= 80) {
                        state.hazardTimer = 0;
                        state.qteActive = false;
                        if (owner != null) {
                            ServerPlayNetworking.send(owner, new SlimeFormPayloads.BossQteSyncPayload(living.getId(), "wither_detonation", 0, false));
                        }
                        if (holder.isBraced) {
                            float dmg = 2.4F;
                            slime.hurtServer(level, level.damageSources().magic(), dmg);
                            holder.recentInternalDamage += dmg;
                            if (owner != null) {
                                owner.addEffect(new MobEffectInstance(MobEffects.WITHER, 20, 0));
                            }
                            state.addStruggleProgress(7.0F);
                            level.playSound(null, posX, centerY, posZ, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7F, 1.8F);
                        } else {
                            float dmg = 6.0F;
                            slime.hurtServer(level, level.damageSources().magic(), dmg);
                            holder.recentInternalDamage += dmg;
                            if (owner != null) {
                                owner.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
                            }
                            state.addStruggleProgress(15.0F);
                            level.playSound(null, posX, centerY, posZ, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2F, 1.2F);
                        }
                        holder.isBraced = false;
                    }
                } else if (SlimeAbsorbHelper.isElderGuardian(living)) {
                    boolean moving = slime.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D || !slime.onGround();
                    if (moving && slime.tickCount % 20 == 0) {
                        slime.hurtServer(level, level.damageSources().thorns(living), 2.0F);
                        holder.recentInternalDamage += 2.0F;
                        level.playSound(null, posX, centerY, posZ, SoundEvents.THORNS_HIT, SoundSource.NEUTRAL, 0.8F, 1.2F);
                    }

                    state.hazardTimer++;
                    if (state.hazardTimer == 90) {
                        state.qteActive = true;
                        state.qteTicksRemaining = 30;
                        state.hazardName = "guardian_laser";
                        holder.activeQteBossId = living.getId();
                        level.playSound(null, posX, centerY, posZ, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.9F, 1.5F);
                        if (owner != null) {
                            ServerPlayNetworking.send(owner, new SlimeFormPayloads.BossQteSyncPayload(living.getId(), "guardian_laser", 30, true));
                        }
                    } else if (state.hazardTimer >= 120) {
                        state.hazardTimer = 0;
                        state.qteActive = false;
                        if (owner != null) {
                            ServerPlayNetworking.send(owner, new SlimeFormPayloads.BossQteSyncPayload(living.getId(), "guardian_laser", 0, false));
                        }
                        if (holder.isBraced) {
                            float dmg = 4.0F;
                            slime.hurtServer(level, level.damageSources().magic(), dmg);
                            holder.recentInternalDamage += dmg;
                            state.addStruggleProgress(8.0F);
                            level.playSound(null, posX, centerY, posZ, SoundEvents.GUARDIAN_ATTACK, SoundSource.PLAYERS, 0.8F, 1.4F);
                        } else {
                            float dmg = 10.0F;
                            slime.hurtServer(level, level.damageSources().magic(), dmg);
                            holder.recentInternalDamage += dmg;
                            state.addStruggleProgress(20.0F);
                            level.playSound(null, posX, centerY, posZ, SoundEvents.ELDER_GUARDIAN_HURT, SoundSource.HOSTILE, 1.2F, 0.8F);
                        }
                        holder.isBraced = false;
                    }
                } else if (SlimeAbsorbHelper.isWarden(living)) {
                    state.hazardTimer++;
                    if (state.hazardTimer == 70) {
                        state.qteActive = true;
                        state.qteTicksRemaining = 30;
                        state.hazardName = "warden_sonic";
                        holder.activeQteBossId = living.getId();
                        level.playSound(null, posX, centerY, posZ, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.0F, 1.0F);
                        if (owner != null) {
                            ServerPlayNetworking.send(owner, new SlimeFormPayloads.BossQteSyncPayload(living.getId(), "warden_sonic", 30, true));
                        }
                    } else if (state.hazardTimer >= 100) {
                        state.hazardTimer = 0;
                        state.qteActive = false;
                        if (owner != null) {
                            ServerPlayNetworking.send(owner, new SlimeFormPayloads.BossQteSyncPayload(living.getId(), "warden_sonic", 0, false));
                        }
                        if (holder.isBraced) {
                            float dmg = 7.0F;
                            slime.hurtServer(level, level.damageSources().sonicBoom(living), dmg);
                            holder.recentInternalDamage += dmg;
                            state.addStruggleProgress(15.0F);
                            level.playSound(null, posX, centerY, posZ, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.7F, 1.4F);
                        } else {
                            float dmg = 18.0F;
                            slime.hurtServer(level, level.damageSources().sonicBoom(living), dmg);
                            holder.recentInternalDamage += dmg;
                            state.addStruggleProgress(35.0F);
                            level.playSound(null, posX, centerY, posZ, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.3F, 0.9F);
                            level.sendParticles(ParticleTypes.SONIC_BOOM, posX, centerY, posZ, 1, 0, 0, 0, 0);
                        }
                        holder.isBraced = false;
                    }
                }
            }

            // Struggle escape mechanic for trapped players (Stage B3)
            if (living instanceof ServerPlayer trappedPlayer) {
                Input input = trappedPlayer.getLastClientInput();
                boolean jumpDown = input != null && input.jump();
                boolean jumpedThisTick = false;
                if (jumpDown && !state.lastJumpInput) {
                    state.addStruggleProgress(config.effectiveAbsorbStruggleJumpGain());
                    jumpedThisTick = true;
                    level.playSound(null, posX, centerY, posZ,
                            SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.6F, 1.4F);
                }
                state.lastJumpInput = jumpDown;

                if (!jumpedThisTick && state.struggleProgress > 0.0F) {
                    float decayPerTick = config.effectiveAbsorbStruggleDecayPerSecond() / 20.0F;
                    state.decayStruggle(decayPerTick);
                }

                // Sync struggle progress to the trapped player
                ServerPlayNetworking.send(trappedPlayer, new SlimeFormPayloads.StruggleSyncPayload(state.struggleProgress));

                if (state.struggleProgress >= 100.0F) {
                    eject(slime, trappedPlayer, config);
                    holder.reabsorbCooldowns.put(trappedPlayer.getUUID(), level.getGameTime() + 60L);
                    level.playSound(null, posX, centerY, posZ,
                            SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
                    continue;
                }
            }

            if (state.isBoss && state.struggleProgress >= 100.0F) {
                if (owner != null && state.qteActive) {
                    ServerPlayNetworking.send(owner, new SlimeFormPayloads.BossQteSyncPayload(living.getId(), "", 0, false));
                }
                eject(slime, living, config);
                holder.reabsorbCooldowns.put(living.getUUID(), level.getGameTime() + 100L);
                level.playSound(null, posX, centerY, posZ,
                        SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
                continue;
            }

            // Unified acid digestion pulse loop
            if (state.digesting) {
                state.digestTimer++;
                int interval = config.effectiveAbsorbDigestIntervalTicks();
                if (state.digestTimer >= interval) {
                    state.digestTimer = 0;
                    float pulseDamage = SlimeAbsorbHelper.getDigestPulseDamage(
                            slime.getSize(),
                            config.effectiveAbsorbDigestBaseDps(),
                            config.effectiveAbsorbDigestDpsPerSizeAbove2(),
                            interval);

                    if (SlimeAbsorbHelper.isElderGuardian(living)) {
                        if (slime.isInWater()) {
                            pulseDamage *= 0.5F;
                        } else {
                            pulseDamage *= 1.5F;
                        }
                    }

                    float currentHp = living.getHealth();
                    float damageDealt = Math.min(pulseDamage, Math.max(0.0F, currentHp));
                    float maxHp = living.getMaxHealth();

                    DamageSource source = SlimeAbsorbHelper.createAcidDamageSource(level, slime, owner);

                    boolean willDie = (currentHp <= pulseDamage);
                    if (willDie && living instanceof ServerPlayer victimPlayer) {
                        victimPlayer.setPos(slime.position());
                        victimPlayer.getInventory().dropAll();
                        markDissolveDeath(victimPlayer);
                        victimPlayer.setCamera(victimPlayer);
                        victimPlayer.connection.send(new ClientboundSetCameraPacket(victimPlayer));
                    }

                    try {
                        APPLYING_ABSORB_DAMAGE.set(true);
                        living.hurtServer(level, source, pulseDamage);
                        if (!living.isAlive() && living.getHealth() <= 0.0F) {
                            living.die(source);
                        }
                    } finally {
                        APPLYING_ABSORB_DAMAGE.set(false);
                    }

                    // Proportional nourishment for owner based on actual damage dealt (Stage B2)
                    float healthGain = SlimeAbsorbHelper.getProportionalHealthGain(
                            state.originalVolume, maxHp, damageDealt, config.effectiveAbsorbDissolveHealthPerUnit());
                    float hungerGain = SlimeAbsorbHelper.getProportionalHungerGain(
                            state.originalVolume, maxHp, damageDealt, config.effectiveAbsorbDissolveHungerPerUnit());

                    if (owner != null && owner.isAlive()) {
                        if (healthGain > 0.0F) {
                            owner.heal(healthGain);
                        }
                        state.accumulatedHungerGain += hungerGain;
                        if (state.accumulatedHungerGain >= 1.0F) {
                            int toEat = (int) state.accumulatedHungerGain;
                            owner.getFoodData().eat(toEat, (float) toEat);
                            state.accumulatedHungerGain -= toEat;
                        }
                    } else if (slime.isAlive() && healthGain > 0.0F) {
                        slime.heal(healthGain);
                    }

                    level.playSound(null, posX, centerY, posZ,
                            SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.4F, 1.4F);
                    level.sendParticles(ParticleTypes.ITEM_SLIME,
                            posX, centerY, posZ, 4, 0.15D, 0.15D, 0.15D, 0.03D);

                    if (!living.isAlive() || living.isRemoved() || living.getHealth() <= 0.0F) {
                        toRemove.add(id);
                    }
                }
            }
        }

        // Stage B4: Update fullness slowdown for morphed player holders
        updateFullnessSlowdown(slime, holder, owner, config);

        for (UUID deadId : toRemove) {
            holder.absorbedIds.remove(deadId);
            AbsorbedEntityState deadState = ABSORBED_ENTITIES.remove(deadId);
            Entity deadEntity = level.getEntity(deadId);
            if (deadEntity instanceof LivingEntity deadLiving) {
                if (owner != null && deadState != null && deadState.isBoss) {
                    if (SlimeAbsorbHelper.isWither(deadLiving)) {
                        SlimeFormState.grantMutation(owner, SlimeFormState.MUTATION_WITHER);
                    } else if (SlimeAbsorbHelper.isElderGuardian(deadLiving)) {
                        SlimeFormState.grantMutation(owner, SlimeFormState.MUTATION_ELDER_GUARDIAN);
                    } else if (SlimeAbsorbHelper.isWarden(deadLiving)) {
                        SlimeFormState.grantMutation(owner, SlimeFormState.MUTATION_WARDEN);
                    }
                }
                restoreEntityFlags(deadLiving, deadState);
                SlimeAbsorbScale.removeAbsorbScale(deadLiving);
                deadLiving.removeTag(ABSORBED_TAG);
                deadLiving.entityTags().removeIf(tag -> tag.startsWith(ABSORBED_BY_PREFIX));
                if (deadLiving instanceof ServerPlayer deadPlayer) {
                    deadPlayer.setCamera(deadPlayer);
                    deadPlayer.connection.send(new ClientboundSetCameraPacket(deadPlayer));
                }
            }
        }
        if (!toRemove.isEmpty()) {
            broadcastSync(slime, holder, owner);
        }
    }

    private static void restoreEntityFlags(LivingEntity living, AbsorbedEntityState state) {
        if (living instanceof Mob mob) {
            mob.setNoAi(state != null && state.originalNoAi);
        }
        living.setNoGravity(state != null && state.originalNoGravity);
        if (state != null) {
            living.noPhysics = state.originalNoPhysics;
        }
    }

    public static void eject(Slime slime, LivingEntity entity, SlimeFormConfig config) {
        if (slime == null || entity == null) {
            return;
        }

        HolderState holder = HOLDERS.get(slime.getUUID());
        if (holder != null) {
            holder.absorbedIds.remove(entity.getUUID());
            holder.reabsorbCooldowns.put(
                    entity.getUUID(), slime.level().getGameTime() + config.effectiveAbsorbReabsorbCooldownTicks());
        }

        AbsorbedEntityState state = ABSORBED_ENTITIES.remove(entity.getUUID());
        restoreEntityFlags(entity, state);
        if (state != null && state.qteActive) {
            ServerPlayer owner = resolveOwner(slime);
            if (owner != null) {
                ServerPlayNetworking.send(owner, new SlimeFormPayloads.BossQteSyncPayload(entity.getId(), "", 0, false));
            }
        }
        if (holder != null) {
            holder.isBraced = false;
        }

        SlimeAbsorbScale.removeAbsorbScale(entity);
        entity.removeTag(ABSORBED_TAG);
        entity.entityTags().removeIf(tag -> tag.startsWith(ABSORBED_BY_PREFIX));

        if (entity instanceof ServerPlayer player) {
            player.setCamera(player);
            player.connection.send(new ClientboundSetCameraPacket(player));
            ServerPlayNetworking.send(player, new SlimeFormPayloads.StruggleSyncPayload(-1.0F));
            ServerPlayNetworking.send(player, createSyncPayload(slime, holder, resolveOwner(slime)));
        }

        updateFullnessSlowdown(slime, holder, resolveOwner(slime), config);
        broadcastSync(slime, holder, resolveOwner(slime));

        // Safely place the entity without suffocation or dropping into void
        if (entity.level() instanceof ServerLevel level) {
            Vec3 safePos = SlimeSpaceFinder.findSafeEjectPosition(level, slime, entity);
            entity.setPos(safePos.x, safePos.y, safePos.z);

            // Compute safe push vector away from slime center
            Vec3 delta = safePos.subtract(slime.position());
            Vec3 push;
            if (delta.horizontalDistanceSqr() > 1.0E-4D) {
                push = new Vec3(delta.x, 0.0D, delta.z).normalize().scale(0.35D).add(0.0D, 0.18D, 0.0D);
            } else {
                Vec3 look = slime.getLookAngle();
                push = new Vec3(look.x * 0.35D, 0.18D, look.z * 0.35D);
            }

            // Ensure push does not slam into blocks
            AABB currentBox = entity.getBoundingBox();
            if (!level.noCollision(entity, currentBox.move(push.scale(1.2D)))) {
                // Obstructed horizontally: keep gentle upward pop only
                push = new Vec3(0.0D, 0.15D, 0.0D);
            }

            // Ensure push does not launch over the void if current spot is supported
            if (SlimeSpaceFinder.hasGroundBeneath(level, safePos, 20)
                    && !SlimeSpaceFinder.hasGroundBeneath(level, safePos.add(push.scale(2.0D)), 20)) {
                push = new Vec3(0.0D, 0.15D, 0.0D);
            }

            entity.setDeltaMovement(push);
            entity.resetFallDistance();
            entity.setIgnoreFallDamageFromCurrentImpulse(true, push);

            if (entity instanceof ServerPlayer player) {
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
                player.resetFallDistance();
            }

            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.SLIME_BLOCK_FALL, SoundSource.PLAYERS, 0.8F, 1.1F);
        }
    }

    public static void ejectAll(Slime slime) {
        if (slime == null) {
            return;
        }
        HolderState holder = HOLDERS.remove(slime.getUUID());
        if (holder == null || holder.absorbedIds.isEmpty()) {
            return;
        }

        SlimeFormConfig config = SlimeFormConfig.get();
        if (slime.level() instanceof ServerLevel level) {
            for (UUID id : new ArrayList<>(holder.absorbedIds)) {
                Entity entity = level.getEntity(id);
                if (entity instanceof LivingEntity living) {
                    eject(slime, living, config);
                } else {
                    ABSORBED_ENTITIES.remove(id);
                }
            }
        }
        holder.absorbedIds.clear();
        removeFullnessSlowdown(slime);
        ServerPlayer owner = resolveOwner(slime);
        if (owner != null) {
            removeFullnessSlowdown(owner);
        }
    }

    public static final Identifier FULLNESS_SLOWDOWN_ID =
            Identifier.fromNamespaceAndPath(SlimeFormMod.MOD_ID, "absorb_fullness_slowdown");

    public static void onHolderHurt(Slime slime, DamageSource source, float amount) {
        if (slime == null || slime.level().isClientSide() || amount <= 0.0F) {
            return;
        }
        HolderState holder = HOLDERS.get(slime.getUUID());
        if (holder == null || holder.absorbedIds.isEmpty()) {
            return;
        }

        SlimeFormConfig config = SlimeFormConfig.get();
        float hitGain = config.effectiveAbsorbStruggleHitGain();
        if (hitGain <= 0.0F) {
            return;
        }

        if (!(slime.level() instanceof ServerLevel level)) {
            return;
        }

        List<ServerPlayer> toEject = new ArrayList<>();
        for (UUID id : new ArrayList<>(holder.absorbedIds)) {
            AbsorbedEntityState state = ABSORBED_ENTITIES.get(id);
            if (state != null) {
                Entity entity = level.getEntity(id);
                if (entity instanceof ServerPlayer player) {
                    state.addStruggleProgress(hitGain);
                    ServerPlayNetworking.send(player, new SlimeFormPayloads.StruggleSyncPayload(state.struggleProgress));
                    if (state.struggleProgress >= 100.0F) {
                        toEject.add(player);
                    }
                }
            }
        }

        for (ServerPlayer escaped : toEject) {
            eject(slime, escaped, config);
            holder.reabsorbCooldowns.put(escaped.getUUID(), level.getGameTime() + 60L);
            level.playSound(null, escaped.getX(), escaped.getY(), escaped.getZ(),
                    SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    private static void updateFullnessSlowdown(
            Slime slime, HolderState holder, ServerPlayer owner, SlimeFormConfig config) {
        if (slime == null || !SlimeMorphManager.isMorphBody(slime)) {
            return;
        }
        ServerPlayer effectiveOwner = owner != null ? owner : resolveOwner(slime);
        if (effectiveOwner == null) {
            return;
        }

        double heldVolume = holder != null ? holder.getHeldVolume() : 0.0D;
        double capacity = SlimeAbsorbHelper.getSlimeCapacity(slime.getSize(), config.effectiveAbsorbCapacityFactor());
        double penalty = SlimeAbsorbHelper.getFullnessSlowdown(
                heldVolume, capacity, config.effectiveAbsorbFullnessMaxSlowdown());

        var slimeSpeedAttr = slime.getAttribute(Attributes.MOVEMENT_SPEED);
        var ownerSpeedAttr = effectiveOwner.getAttribute(Attributes.MOVEMENT_SPEED);

        if (penalty < -1.0E-4D) {
            AttributeModifier modifier = new AttributeModifier(
                    FULLNESS_SLOWDOWN_ID, penalty, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            if (slimeSpeedAttr != null) {
                slimeSpeedAttr.addOrUpdateTransientModifier(modifier);
            }
            if (ownerSpeedAttr != null) {
                ownerSpeedAttr.addOrUpdateTransientModifier(modifier);
            }
        } else {
            if (slimeSpeedAttr != null) {
                slimeSpeedAttr.removeModifier(FULLNESS_SLOWDOWN_ID);
            }
            if (ownerSpeedAttr != null) {
                ownerSpeedAttr.removeModifier(FULLNESS_SLOWDOWN_ID);
            }
        }
    }

    public static void removeFullnessSlowdown(LivingEntity entity) {
        if (entity != null) {
            var attr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attr != null) {
                attr.removeModifier(FULLNESS_SLOWDOWN_ID);
            }
        }
    }

    public static void startDigestion(Slime slime, LivingEntity entity, ServerPlayer owner) {
        if (slime == null || entity == null || !(entity.level() instanceof ServerLevel level)) {
            return;
        }

        AbsorbedEntityState state = ABSORBED_ENTITIES.get(entity.getUUID());
        if (state == null) {
            return;
        }

        // If already digesting, do nothing
        if (state.digesting) {
            return;
        }

        state.digesting = true;
        state.digestTimer = 0;

        HolderState holder = HOLDERS.get(slime.getUUID());
        broadcastSync(slime, holder, owner != null ? owner : resolveOwner(slime));

        // Start digestion feedback: acid sizzle sound and particles
        level.playSound(null, slime.getX(), slime.getY(), slime.getZ(),
                SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 0.8F, 1.2F);
        level.playSound(null, slime.getX(), slime.getY(), slime.getZ(),
                SoundEvents.SLIME_ATTACK, SoundSource.PLAYERS, 0.7F, 1.3F);
        level.sendParticles(ParticleTypes.ITEM_SLIME,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.4D, entity.getZ(),
                16, 0.25D, 0.25D, 0.25D, 0.08D);
    }

    public static void handleAction(ServerPlayer player, SlimeFormPayloads.AbsorbActionPayload payload) {
        if (player == null || player.level().isClientSide()) {
            return;
        }

        SlimeFormConfig config = SlimeFormConfig.get();
        if (!config.absorbEnabled) {
            return;
        }

        // Resolve holder slime
        Slime body = SlimeMorphManager.getMorphBody(player);
        if (body == null) {
            return;
        }

        HolderState holder = HOLDERS.get(body.getUUID());
        if (holder == null || holder.absorbedIds.isEmpty()) {
            return;
        }

        ServerLevel level = (ServerLevel) body.level();

        if (payload.action() == SlimeFormPayloads.AbsorbActionPayload.ACTION_BRACE) {
            holder.isBraced = true;
            level.playSound(null, body.getX(), body.getY(), body.getZ(),
                    SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.9F, 0.8F);
            level.sendParticles(ParticleTypes.ITEM_SLIME,
                    body.getX(), body.getY() + body.getBbHeight() * 0.5D, body.getZ(),
                    15, 0.4D, 0.4D, 0.4D, 0.05D);
            return;
        }

        LivingEntity targetEntity = null;

        if (payload.targetEntityId() != -1) {
            Entity found = level.getEntity(payload.targetEntityId());
            if (found instanceof LivingEntity living && holder.absorbedIds.contains(found.getUUID())) {
                targetEntity = living;
            }
        }

        if (targetEntity == null) {
            // Default to most recently absorbed entity (last element in list)
            UUID lastId = holder.absorbedIds.get(holder.absorbedIds.size() - 1);
            Entity found = level.getEntity(lastId);
            if (found instanceof LivingEntity living) {
                targetEntity = living;
            }
        }

        if (targetEntity == null) {
            return;
        }

        if (payload.action() == SlimeFormPayloads.AbsorbActionPayload.ACTION_EJECT) {
            eject(body, targetEntity, config);
        } else if (payload.action() == SlimeFormPayloads.AbsorbActionPayload.ACTION_DISSOLVE) {
            startDigestion(body, targetEntity, player);
        }
    }

    public static void handleStruggleJump(ServerPlayer player) {
        if (player == null || player.level().isClientSide()) {
            return;
        }
        AbsorbedEntityState state = ABSORBED_ENTITIES.get(player.getUUID());
        if (state == null) {
            return;
        }
        SlimeFormConfig config = SlimeFormConfig.get();
        state.addStruggleProgress(config.effectiveAbsorbStruggleJumpGain());

        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.6F, 1.4F);

        ServerPlayNetworking.send(player, new SlimeFormPayloads.StruggleSyncPayload(state.struggleProgress));

        if (state.struggleProgress >= 100.0F) {
            Entity holderEntity = level.getEntity(state.holderId);
            if (holderEntity instanceof Slime slime) {
                HolderState holder = HOLDERS.get(slime.getUUID());
                if (holder != null) {
                    holder.reabsorbCooldowns.put(player.getUUID(), level.getGameTime() + 60L);
                }
                eject(slime, player, config);
                level.playSound(null, slime.getX(), slime.getY(), slime.getZ(),
                        SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
    }

    private static void ejectAllForMissingHolder(MinecraftServer server, HolderState holder) {
        for (UUID id : new ArrayList<>(holder.absorbedIds)) {
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(id);
                if (entity instanceof LivingEntity living) {
                    AbsorbedEntityState state = ABSORBED_ENTITIES.remove(id);
                    restoreEntityFlags(living, state);
                    SlimeAbsorbScale.removeAbsorbScale(living);
                    living.removeTag(ABSORBED_TAG);
                    living.entityTags().removeIf(tag -> tag.startsWith(ABSORBED_BY_PREFIX));
                    living.resetFallDistance();
                    living.setIgnoreFallDamageFromCurrentImpulse(true, Vec3.ZERO);
                    if (living instanceof ServerPlayer player) {
                        player.setCamera(player);
                        player.connection.send(new ClientboundSetCameraPacket(player));
                        player.resetFallDistance();
                    }
                    Vec3 safePos = SlimeSpaceFinder.findFittingPosition(level, living);
                    if (safePos != null) {
                        living.setPos(safePos.x, safePos.y, safePos.z);
                    }
                    break;
                }
            }
        }
        holder.absorbedIds.clear();
    }

    private static void ejectAllEverywhere(MinecraftServer server) {
        for (HolderState holder : new ArrayList<>(HOLDERS.values())) {
            ejectAllForMissingHolder(server, holder);
        }
        HOLDERS.clear();
    }

    private static void cleanupOrphanedAbsorbedEntities(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living && living.entityTags().contains(ABSORBED_TAG)) {
                    UUID holderId = getHolderIdFromTags(living);
                    if (holderId == null || !isHolderAliveInWorld(server, holderId)) {
                        AbsorbedEntityState state = ABSORBED_ENTITIES.remove(living.getUUID());
                        restoreEntityFlags(living, state);
                        SlimeAbsorbScale.removeAbsorbScale(living);
                        living.removeTag(ABSORBED_TAG);
                        living.entityTags().removeIf(tag -> tag.startsWith(ABSORBED_BY_PREFIX));
                    }
                }
            }
        }
    }

    private static UUID getHolderIdFromTags(LivingEntity living) {
        for (String tag : living.entityTags()) {
            if (tag.startsWith(ABSORBED_BY_PREFIX)) {
                try {
                    return UUID.fromString(tag.substring(ABSORBED_BY_PREFIX.length()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return null;
    }

    private static boolean isHolderAliveInWorld(MinecraftServer server, UUID holderId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity holder = level.getEntity(holderId);
            if (holder instanceof Slime slime && isEligibleHolder(slime)) {
                return true;
            }
        }
        return false;
    }

    public static void onPlayerDeath(ServerPlayer player) {
        if (player == null) {
            return;
        }
        // If morphed, find morph body and eject all
        if (SlimeMorphManager.isMorphed(player)) {
            for (ServerLevel level : player.level().getServer().getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof Slime slime && SlimeMorphManager.isMorphBody(slime)) {
                        ServerPlayer owner = SlimeMorphManager.ownerOf(slime);
                        if (owner != null && owner.getUUID().equals(player.getUUID())) {
                            ejectAll(slime);
                        }
                    }
                }
            }
        }
    }

    public static void handlePlayerDisconnect(MinecraftServer server, ServerPlayer player) {
        if (player == null) {
            return;
        }

        SlimeFormConfig config = SlimeFormConfig.get();

        // 1. If this player is an absorbed entity inside a slime, auto-eject them at the slime's position
        // BEFORE the logout save so no items or position are lost (inventory loss prevention).
        if (isAbsorbed(player)) {
            UUID holderId = getHolderIdFromTags(player);
            Slime holderSlime = null;
            if (holderId != null) {
                for (ServerLevel level : server.getAllLevels()) {
                    Entity found = level.getEntity(holderId);
                    if (found instanceof Slime s) {
                        holderSlime = s;
                        break;
                    }
                }
            }
            if (holderSlime != null) {
                eject(holderSlime, player, config);
            } else {
                // Holder slime not found or unloaded: safely restore player directly
                AbsorbedEntityState state = ABSORBED_ENTITIES.remove(player.getUUID());
                restoreEntityFlags(player, state);
                SlimeAbsorbScale.removeAbsorbScale(player);
                player.removeTag(ABSORBED_TAG);
                player.entityTags().removeIf(tag -> tag.startsWith(ABSORBED_BY_PREFIX));
                player.resetFallDistance();
                player.setIgnoreFallDamageFromCurrentImpulse(true, Vec3.ZERO);
                player.setCamera(player);
                player.connection.send(new ClientboundSetCameraPacket(player));
            }
        }

        // 2. Eject any entities held by this player's morph body
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Slime slime && SlimeMorphManager.isMorphBody(slime)) {
                    ServerPlayer owner = SlimeMorphManager.ownerOf(slime);
                    if (owner != null && owner.getUUID().equals(player.getUUID())) {
                        ejectAll(slime);
                    }
                }
            }
        }
    }

    public static void onPlayerJoin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        // If player has leftover absorbed tags from an unexpected crash/disconnect, cleanly reset
        if (player.entityTags().contains(ABSORBED_TAG)) {
            UUID holderId = getHolderIdFromTags(player);
            boolean holderValid = false;
            if (holderId != null && player.level().getServer() != null) {
                holderValid = isHolderAliveInWorld(player.level().getServer(), holderId);
            }
            if (!holderValid) {
                ABSORBED_ENTITIES.remove(player.getUUID());
                player.setNoGravity(false);
                SlimeAbsorbScale.removeAbsorbScale(player);
                player.removeTag(ABSORBED_TAG);
                player.entityTags().removeIf(tag -> tag.startsWith(ABSORBED_BY_PREFIX));
                player.resetFallDistance();
                player.setIgnoreFallDamageFromCurrentImpulse(true, Vec3.ZERO);
                player.setCamera(player);
            }
        }
    }

    private static void playRefuseFeedback(Slime slime, HolderState holder, long gameTime) {
        if (gameTime - holder.lastRefuseFeedbackTick >= 20L) {
            holder.lastRefuseFeedbackTick = gameTime;
            ServerLevel level = (ServerLevel) slime.level();
            level.playSound(null, slime.getX(), slime.getY(), slime.getZ(),
                    SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.6F, 0.7F);
            level.sendParticles(ParticleTypes.ITEM_SLIME,
                    slime.getX(), slime.getY() + slime.getBbHeight() * 0.5D, slime.getZ(),
                    4, 0.2D, 0.2D, 0.2D, 0.02D);
        }
    }

    private static final class HolderState {
        private final UUID slimeId;
        private final List<UUID> absorbedIds = new ArrayList<>();
        private final Map<UUID, Long> reabsorbCooldowns = new ConcurrentHashMap<>();
        private long lastRefuseFeedbackTick;
        private boolean isBraced;
        private float recentInternalDamage;
        private int recentDamageDecayTimer;
        private int activeQteBossId = -1;

        private HolderState(UUID slimeId) {
            this.slimeId = slimeId;
        }

        private double getHeldVolume() {
            double total = 0.0D;
            for (UUID id : absorbedIds) {
                AbsorbedEntityState state = ABSORBED_ENTITIES.get(id);
                if (state != null) {
                    total += state.originalVolume;
                }
            }
            return total;
        }
    }

    private static final class AbsorbedEntityState {
        private final UUID entityId;
        private final UUID holderId;
        private final boolean originalNoAi;
        private final boolean originalNoGravity;
        private final boolean originalInvulnerable;
        private final boolean originalSilent;
        private final boolean originalNoPhysics;
        private final double originalVolume;
        private boolean digesting;
        private int digestTimer;
        private float struggleProgress;
        private boolean lastJumpInput;
        private float accumulatedHungerGain;
        private final boolean isBoss;
        private int hazardTimer;
        private boolean qteActive;
        private int qteTicksRemaining;
        private String hazardName;
        private boolean witherExplodedAtHalfHp;

        private AbsorbedEntityState(
                UUID entityId, UUID holderId, boolean originalNoAi, boolean originalNoGravity,
                boolean originalInvulnerable, boolean originalSilent, boolean originalNoPhysics,
                double originalVolume, boolean digesting, boolean isBoss) {
            this.entityId = entityId;
            this.holderId = holderId;
            this.originalNoAi = originalNoAi;
            this.originalNoGravity = originalNoGravity;
            this.originalInvulnerable = originalInvulnerable;
            this.originalSilent = originalSilent;
            this.originalNoPhysics = originalNoPhysics;
            this.originalVolume = originalVolume;
            this.digesting = digesting;
            this.isBoss = isBoss;
            this.digestTimer = 0;
            this.struggleProgress = 0.0F;
            this.lastJumpInput = false;
            this.accumulatedHungerGain = 0.0F;
            this.hazardTimer = 0;
            this.qteActive = false;
            this.qteTicksRemaining = 0;
            this.hazardName = "";
            this.witherExplodedAtHalfHp = false;
        }

        private void addStruggleProgress(float amount) {
            this.struggleProgress = Math.min(100.0F, Math.max(0.0F, this.struggleProgress + amount));
        }

        private void decayStruggle(float amount) {
            this.struggleProgress = Math.max(0.0F, this.struggleProgress - amount);
        }
    }
}
