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
    public static final CustomPacketPayload.Type<FlowStateInputPayload> FLOW_STATE_INPUT_TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                    SlimeFormMod.MOD_ID, "flow_state_input"));
    public static final StreamCodec<ByteBuf, FlowStateInputPayload> FLOW_STATE_INPUT_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, FlowStateInputPayload::crouch,
                    ByteBufCodecs.BOOL, FlowStateInputPayload::jump,
                    ByteBufCodecs.BOOL, FlowStateInputPayload::forward,
                    ByteBufCodecs.BOOL, FlowStateInputPayload::back,
                    ByteBufCodecs.BOOL, FlowStateInputPayload::left,
                    ByteBufCodecs.BOOL, FlowStateInputPayload::right,
                    ByteBufCodecs.BOOL, FlowStateInputPayload::shift,
                    ByteBufCodecs.FLOAT, FlowStateInputPayload::yaw,
                    ByteBufCodecs.FLOAT, FlowStateInputPayload::pitch,
                    FlowStateInputPayload::new);
    public static final CustomPacketPayload.Type<FlowStateCameraPayload> FLOW_STATE_CAMERA_TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                    SlimeFormMod.MOD_ID, "flow_state_camera"));
    public static final StreamCodec<ByteBuf, FlowStateCameraPayload> FLOW_STATE_CAMERA_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, FlowStateCameraPayload::entityId,
                    ByteBufCodecs.BOOL, FlowStateCameraPayload::possessed,
                    FlowStateCameraPayload::new);

    private SlimeFormPayloads() {
    }

    public record WakeDormantPayload() implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return WAKE_DORMANT_TYPE;
        }
    }

    public record FlowStateInputPayload(
            boolean crouch,
            boolean jump,
            boolean forward,
            boolean back,
            boolean left,
            boolean right,
            boolean shift,
            float yaw,
            float pitch) implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return FLOW_STATE_INPUT_TYPE;
        }
    }

    public record FlowStateCameraPayload(int entityId, boolean possessed) implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return FLOW_STATE_CAMERA_TYPE;
        }
    }
}
