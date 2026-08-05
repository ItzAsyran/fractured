package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormMod;
import io.asy.fragmented.SlimeFormState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.GameType;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ServerPlayer.class)
public abstract class SlimePlayerLifecycleMixin {
    @Unique
    private boolean slimeform$deathHandled;

    @Inject(method = "hurtServer", at = @At("TAIL"))
    private void slimeform$retaliateWhenHurt(
            ServerLevel level, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return;
        }

        ServerPlayer player = (ServerPlayer) (Object) this;
        SlimeFormMod.recordCombat(player);
        LivingEntity attacker = source.getEntity() instanceof LivingEntity living
                ? living
                : null;
        if (attacker != null) {
            SlimeFormMod.commandNearbySlimesToAttack(player, attacker);
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void slimeform$splitOnDeath(DamageSource source, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (slimeform$deathHandled || !SlimeFormState.isActive(player)) {
            return;
        }

        LivingEntity originalKiller = source.getEntity() instanceof LivingEntity attacker
                ? attacker
                : null;
        if (originalKiller != null) {
            SlimeFormMod.commandNearbySlimesToAttack(player, originalKiller);
        }

        int currentSize = SlimeFormState.getSize(player);
        if (currentSize <= SlimeFormState.MIN_SIZE) {
            // Size 1 is the terminal split. Let vanilla handle the death and
            // reset the persistent form to the largest size on respawn.
            SlimeFormMod.LOGGER.info(
                    "[slimeform] {} died at size 1; allowing normal respawn and resetting to size {}",
                    player.getName().getString(), SlimeFormState.getMaxSize());
            return;
        }

        slimeform$deathHandled = true;
        GameType previousGameMode = player.gameMode() == GameType.SPECTATOR
                ? GameType.SURVIVAL
                : player.gameMode();

        int nextSize = Math.max(SlimeFormState.MIN_SIZE, currentSize - 1);
        int splitCount = currentSize == 2 ? 4 : 2;
        SlimeFormState.setSize(player, nextSize);

        List<Slime> splitSlimes = spawnSplitSlimes(player, nextSize, splitCount);
        if (!splitSlimes.isEmpty()) {
            SlimeFormMod.playSlimePlayerEffect(player, 28, 0.75F);
            SlimeFormMod.beginRecovery(player, splitSlimes, previousGameMode, originalKiller);
            player.setGameMode(GameType.SPECTATOR);
            player.setCamera(splitSlimes.get(0));
            player.connection.send(new ClientboundGameEventPacket(
                    ClientboundGameEventPacket.IMMEDIATE_RESPAWN, 1.0F));
            player.displayClientMessage(
                    Component.literal("You split into slimes. ").withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal("Reforming in 30 seconds...").withStyle(ChatFormatting.GRAY)),
                    true);
        }

        SlimeFormMod.LOGGER.info(
                "[slimeform] {} split from size {} into {} size {} slimes and entered spectator recovery",
                player.getName().getString(), currentSize, splitCount, nextSize);
    }

    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void slimeform$restoreState(ServerPlayer oldPlayer, boolean alive, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (SlimeFormState.isActive(oldPlayer)) {
            player.addTag(SlimeFormMod.SLIME_FORM_TAG);
            int restoredSize = SlimeFormState.getSize(oldPlayer) <= SlimeFormState.MIN_SIZE
                    ? SlimeFormState.getMaxSize()
                    : SlimeFormState.getSize(oldPlayer);
            SlimeFormState.setSize(player, restoredSize);
            SlimeFormState.applyHealth(player, true);
        } else {
            SlimeFormState.deactivate(player);
        }
    }

    @Unique
    private static List<Slime> spawnSplitSlimes(ServerPlayer player, int size, int count) {
        ServerLevel level = player.level();
        List<Slime> splitSlimes = new java.util.ArrayList<>();
        for (int index = 0; index < count; index++) {
            Slime slime = EntityType.SLIME.create(level, EntitySpawnReason.TRIGGERED);
            if (slime == null) {
                continue;
            }
            double angle = (Math.PI * 2.0D * index) / count;
            slime.setSize(size, true);
            slime.addTag(SlimeFormMod.recoveryLineageTag(player.getUUID()));
            slime.setPos(
                    player.getX() + Math.cos(angle) * 0.75D,
                    player.getY() + 0.1D,
                    player.getZ() + Math.sin(angle) * 0.75D);
            level.addFreshEntity(slime);
            SlimeFormMod.playSlimeFragmentSpawnEffects(slime);
            splitSlimes.add(slime);
        }
        return splitSlimes;
    }
}
