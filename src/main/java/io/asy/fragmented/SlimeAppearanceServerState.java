package io.asy.fragmented;

import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-session settings for each player's Slime Form appearance. */
public final class SlimeAppearanceServerState {
    private static final Settings DEFAULTS = new Settings(0, false, false);
    private static final Map<UUID, Settings> SETTINGS = new HashMap<>();

    private SlimeAppearanceServerState() {
    }

    public static void registerTrackingEvents() {
        EntityTrackingEvents.START_TRACKING.register(SlimeAppearanceServerState::sendToTracker);
        EntityTrackingEvents.STOP_TRACKING.register(SlimeAppearanceServerState::clearFromTracker);
    }

    public static void update(ServerPlayer player,
                              SlimeFormPayloads.PlayerAppearancePreferencePayload payload) {
        Settings settings = new Settings(
                clamp(payload.transparencyPercent()), payload.slimeTint(), payload.slimeShell());
        SETTINGS.put(player.getUUID(), settings);
        broadcast(((ServerLevel) player.level()).getServer(), statePayload(player, settings));
    }

    public static void sendSnapshot(ServerPlayer joiningPlayer) {
        MinecraftServer server = ((ServerLevel) joiningPlayer.level()).getServer();
        for (ServerPlayer trackedPlayer : server.getPlayerList().getPlayers()) {
            Settings settings = SETTINGS.get(trackedPlayer.getUUID());
            if (settings != null) {
                sendTo(joiningPlayer, statePayload(trackedPlayer, settings));
            }
        }
    }

    public static void remove(ServerPlayer player) {
        SETTINGS.remove(player.getUUID());
        SlimeFormPayloads.PlayerAppearanceStatePayload clear =
                new SlimeFormPayloads.PlayerAppearanceStatePayload(player.getId(), false, 0, false, false);
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        for (ServerPlayer viewer : PlayerLookup.all(server)) {
            if (viewer != player) {
                sendTo(viewer, clear);
            }
        }
    }

    private static void sendToTracker(Entity entity, ServerPlayer viewer) {
        if (!(entity instanceof ServerPlayer trackedPlayer)) {
            return;
        }
        Settings settings = SETTINGS.getOrDefault(trackedPlayer.getUUID(), DEFAULTS);
        sendTo(viewer, statePayload(trackedPlayer, settings));
    }

    private static void clearFromTracker(Entity entity, ServerPlayer viewer) {
        if (entity instanceof ServerPlayer trackedPlayer) {
            sendTo(viewer, new SlimeFormPayloads.PlayerAppearanceStatePayload(
                    trackedPlayer.getId(), false, 0, false, false));
        }
    }

    private static SlimeFormPayloads.PlayerAppearanceStatePayload statePayload(
            ServerPlayer player, Settings settings) {
        return new SlimeFormPayloads.PlayerAppearanceStatePayload(
                player.getId(), true, settings.transparencyPercent(), settings.slimeTint(), settings.slimeShell());
    }

    private static void broadcast(
            MinecraftServer server, SlimeFormPayloads.PlayerAppearanceStatePayload payload) {
        for (ServerPlayer viewer : PlayerLookup.all(server)) {
            sendTo(viewer, payload);
        }
    }

    private static void sendTo(
            ServerPlayer viewer, SlimeFormPayloads.PlayerAppearanceStatePayload payload) {
        if (ServerPlayNetworking.canSend(viewer, SlimeFormPayloads.PLAYER_APPEARANCE_STATE_TYPE)) {
            ServerPlayNetworking.send(viewer, payload);
        }
    }

    private static int clamp(int value) {
        return Math.max(SlimeFormConfig.MIN_PLAYER_TRANSPARENCY,
                Math.min(SlimeFormConfig.MAX_PLAYER_TRANSPARENCY, value));
    }

    private record Settings(int transparencyPercent, boolean slimeTint, boolean slimeShell) {
    }
}
