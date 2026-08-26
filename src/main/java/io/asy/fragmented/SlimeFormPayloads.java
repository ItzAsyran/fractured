package io.asy.fragmented;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

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
    public static final CustomPacketPayload.Type<DormantDebugPayload> DORMANT_DEBUG_TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                    SlimeFormMod.MOD_ID, "dormant_debug"));
    public static final StreamCodec<ByteBuf, DormantDebugPayload> DORMANT_DEBUG_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, DormantDebugPayload::visible,
                    ByteBufCodecs.VAR_INT, DormantDebugPayload::remainingTicks,
                    DormantDebugPayload::new);
    public static final CustomPacketPayload.Type<MorphInputPayload> MORPH_INPUT_TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                    SlimeFormMod.MOD_ID, "slime_morph_input"));
    public static final StreamCodec<ByteBuf, MorphInputPayload> MORPH_INPUT_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, MorphInputPayload::crouch,
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
                    ByteBufCodecs.VAR_INT, MorphStatePayload::entityId,
                    MorphStatePayload::new);

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

    public record DormantDebugPayload(boolean visible, int remainingTicks) implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return DORMANT_DEBUG_TYPE;
        }
    }

    public record MorphInputPayload(boolean crouch, boolean jump, boolean forward, boolean back,
                                    boolean left, boolean right, float yaw, float pitch)
            implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return MORPH_INPUT_TYPE;
        }
    }

    public record MorphStatePayload(int phase, int remaining, int total, int entityId)
            implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return MORPH_STATE_TYPE;
        }
    }
}
