package io.asy.fragmented;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

public interface SlimeSleepingStateAccess {
    boolean slimeform$shouldReplaceWithSlime();

    void slimeform$setReplaceWithSlime(boolean replace);
}
