package io.asy.fragmented;

import java.util.List;

/**
 * Accessor for absorb rendering state on {@link net.minecraft.client.renderer.entity.state.SlimeRenderState}.
 */
public interface SlimeAbsorbRenderStateAccess {
    boolean slimeform$isHoldingAbsorbed();

    void slimeform$setHoldingAbsorbed(boolean holding);

    int slimeform$getHolderEntityId();

    void slimeform$setHolderEntityId(int id);

    List<Integer> slimeform$getTrappedEntityIds();

    void slimeform$setTrappedEntityIds(List<Integer> ids);
}
