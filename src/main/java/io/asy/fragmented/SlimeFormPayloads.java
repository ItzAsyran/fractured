package io.asy.fragmented;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class SlimeFormPayloads {
    public static final CustomPacketPayload.Type<WakeDormantPayload> WAKE_DORMANT_TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                    SlimeFormMod.MOD_ID, "wake_dormant"));
    public static final StreamCodec<ByteBuf, WakeDormantPayload> WAKE_DORMANT_CODEC =
            StreamCodec.unit(new WakeDormantPayload());

    private SlimeFormPayloads() {
    }

    public record WakeDormantPayload() implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return WAKE_DORMANT_TYPE;
        }
    }
}
