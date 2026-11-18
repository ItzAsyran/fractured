package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeAbsorbRenderStateAccess;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Collections;
import java.util.List;

@Mixin(SlimeRenderState.class)
public abstract class SlimeAbsorbSlimeRenderStateMixin implements SlimeAbsorbRenderStateAccess {
    @Unique
    private boolean slimeform$holdingAbsorbed;
    @Unique
    private int slimeform$holderEntityId;
    @Unique
    private List<Integer> slimeform$trappedEntityIds = Collections.emptyList();

    @Override
    public boolean slimeform$isHoldingAbsorbed() {
        return slimeform$holdingAbsorbed;
    }

    @Override
    public void slimeform$setHoldingAbsorbed(boolean holding) {
        this.slimeform$holdingAbsorbed = holding;
    }

    @Override
    public int slimeform$getHolderEntityId() {
        return slimeform$holderEntityId;
    }

    @Override
    public void slimeform$setHolderEntityId(int id) {
        this.slimeform$holderEntityId = id;
    }

    @Override
    public List<Integer> slimeform$getTrappedEntityIds() {
        return slimeform$trappedEntityIds;
    }

    @Override
    public void slimeform$setTrappedEntityIds(List<Integer> ids) {
        this.slimeform$trappedEntityIds = ids != null ? ids : Collections.emptyList();
    }
}
