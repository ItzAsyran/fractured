package io.asy.fragmented.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerGamePacketListenerImpl.class)
public interface SlimeServerConnectionAccessor {
    @Accessor("player")
    void slimeform$setPlayer(ServerPlayer player);

    @Accessor("player")
    ServerPlayer slimeform$getPlayer();
}
