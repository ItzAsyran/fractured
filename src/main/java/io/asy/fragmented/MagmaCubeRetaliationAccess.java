package io.asy.fragmented;

import net.minecraft.world.entity.player.Player;

public interface MagmaCubeRetaliationAccess {
    void slimeform$setRetaliationTarget(Player player);

    boolean slimeform$isRetaliatingAgainst(Player player);
}
