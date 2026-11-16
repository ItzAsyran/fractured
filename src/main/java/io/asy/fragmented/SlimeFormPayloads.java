package io.asy.fragmented;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class SlimeFormPayloads {
    public static final CustomPacketPayload.Type<WakeDormantPayload> WAKE_DORMANT_TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                    SlimeFormMod.MOD_ID, "wake_dormant"));
    public static final StreamCodec<ByteBuf, WakeDormantPayload> WAKE_DORMANT_CODEC =
            StreamCodec.unit(new WakeDormantPayload());
    public static final CustomPacketPayload.Type<PhaseStatePayload> PHASE_STATE_TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                    SlimeFormMod.MOD_ID, "phase_state"));
    public static final StreamCodec<ByteBuf, PhaseStatePayload> PHASE_STATE_CODEC =
            ByteBufCodecs.BOOL.map(PhaseStatePayload::new, PhaseStatePayload::enabled);
    public static final CustomPacketPayload.Type<SlimeChunksStatePayload> SLIME_CHUNKS_STATE_TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                    SlimeFormMod.MOD_ID, "slime_chunks_state"));
    public static final StreamCodec<ByteBuf, AuraSource> AURA_SOURCE_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, AuraSource::chunkX,
                    ByteBufCodecs.VAR_INT, AuraSource::chunkZ,
                    AuraSource::new);
    public static final StreamCodec<ByteBuf, SlimeChunksStatePayload> SLIME_CHUNKS_STATE_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, SlimeChunksStatePayload::enabled,
                    ByteBufCodecs.collection(ArrayList::new, AURA_SOURCE_CODEC),
                    SlimeChunksStatePayload::sources,
                    SlimeChunksStatePayload::new);
    public static final CustomPacketPayload.Type<DormantDebugPayload> DORMANT_DEBUG_TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                    SlimeFormMod.MOD_ID, "dormant_debug"));
    public static final StreamCodec<ByteBuf, DormantDebugPayload> DORMANT_DEBUG_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, DormantDebugPayload::visible,
                    ByteBufCodecs.VAR_INT, DormantDebugPayload::remainingTicks,
                    DormantDebugPayload::new);
    public static final CustomPacketPayload.Type<MorphTogglePayload> MORPH_TOGGLE_TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                    SlimeFormMod.MOD_ID, "slime_morph_toggle"));
    public static final StreamCodec<ByteBuf, MorphTogglePayload> MORPH_TOGGLE_CODEC =
            StreamCodec.unit(new MorphTogglePayload());
    public static final CustomPacketPayload.Type<RecoveryCyclePayload> RECOVERY_CYCLE_TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                    SlimeFormMod.MOD_ID, "recovery_cycle"));
    public static final StreamCodec<ByteBuf, RecoveryCyclePayload> RECOVERY_CYCLE_CODEC =
            ByteBufCodecs.BOOL.map(RecoveryCyclePayload::new, RecoveryCyclePayload::next);
    public static final CustomPacketPayload.Type<MorphInputPayload> MORPH_INPUT_TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                    SlimeFormMod.MOD_ID, "slime_morph_input"));
    public static final StreamCodec<ByteBuf, MorphInputPayload> MORPH_INPUT_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, MorphInputPayload::jump,
                    ByteBufCodecs.BOOL, MorphInputPayload::forward,
                    ByteBufCodecs.BOOL, MorphInputPayload::back,
                    ByteBufCodecs.BOOL, MorphInputPayload::left,
                    ByteBufCodecs.BOOL, MorphInputPayload::right,
                    ByteBufCodecs.FLOAT, MorphInputPayload::yaw,
                    ByteBufCodecs.FLOAT, MorphInputPayload::pitch,
                    MorphInputPayload::new);
    public static final CustomPacketPayload.Type<MorphStatePayload> MORPH_STATE_TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                    SlimeFormMod.MOD_ID, "slime_morph_state"));
    public static final StreamCodec<ByteBuf, MorphStatePayload> MORPH_STATE_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, MorphStatePayload::phase,
                    ByteBufCodecs.VAR_INT, MorphStatePayload::remaining,
                    ByteBufCodecs.VAR_INT, MorphStatePayload::total,
                    ByteBufCodecs.VAR_INT, MorphStatePayload::size,
                    MorphStatePayload::new);
    public static final CustomPacketPayload.Type<PlayerAppearancePreferencePayload>
            PLAYER_APPEARANCE_PREFERENCE_TYPE = new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(SlimeFormMod.MOD_ID, "player_appearance_preference"));
    public static final StreamCodec<ByteBuf, PlayerAppearancePreferencePayload>
            PLAYER_APPEARANCE_PREFERENCE_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, PlayerAppearancePreferencePayload::transparencyPercent,
                    ByteBufCodecs.BOOL, PlayerAppearancePreferencePayload::slimeTint,
                    ByteBufCodecs.BOOL, PlayerAppearancePreferencePayload::slimeShell,
                    PlayerAppearancePreferencePayload::new);
    public static final CustomPacketPayload.Type<PlayerAppearanceStatePayload>
            PLAYER_APPEARANCE_STATE_TYPE = new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(SlimeFormMod.MOD_ID, "player_appearance_state"));
    public static final StreamCodec<ByteBuf, PlayerAppearanceStatePayload> PLAYER_APPEARANCE_STATE_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, PlayerAppearanceStatePayload::entityId,
                    ByteBufCodecs.BOOL, PlayerAppearanceStatePayload::present,
                    ByteBufCodecs.VAR_INT, PlayerAppearanceStatePayload::transparencyPercent,
                    ByteBufCodecs.BOOL, PlayerAppearanceStatePayload::slimeTint,
                    ByteBufCodecs.BOOL, PlayerAppearanceStatePayload::slimeShell,
                    PlayerAppearanceStatePayload::new);

    private SlimeFormPayloads() {
    }

    public record WakeDormantPayload() implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return WAKE_DORMANT_TYPE;
        }
    }

    public record PhaseStatePayload(boolean enabled) implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PHASE_STATE_TYPE;
        }
    }

    public record AuraSource(int chunkX, int chunkZ) {
    }

    public record SlimeChunksStatePayload(boolean enabled, List<AuraSource> sources)
            implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return SLIME_CHUNKS_STATE_TYPE;
        }
    }

    public record DormantDebugPayload(boolean visible, int remainingTicks) implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return DORMANT_DEBUG_TYPE;
        }
    }

    /** Sent while spectating a recovery: left click = next fragment, right click = previous. */
    public record RecoveryCyclePayload(boolean next) implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return RECOVERY_CYCLE_TYPE;
        }
    }

    public record MorphTogglePayload() implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return MORPH_TOGGLE_TYPE;
        }
    }

    public record MorphInputPayload(boolean jump, boolean forward, boolean back,
                                    boolean left, boolean right, float yaw, float pitch)
            implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return MORPH_INPUT_TYPE;
        }
    }

    public record MorphStatePayload(int phase, int remaining, int total, int size)
            implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return MORPH_STATE_TYPE;
        }
    }

    public record PlayerAppearancePreferencePayload(
            int transparencyPercent, boolean slimeTint, boolean slimeShell) implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PLAYER_APPEARANCE_PREFERENCE_TYPE;
        }
    }

    public record PlayerAppearanceStatePayload(
            int entityId, boolean present, int transparencyPercent, boolean slimeTint, boolean slimeShell)
            implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PLAYER_APPEARANCE_STATE_TYPE;
        }
    }
}
